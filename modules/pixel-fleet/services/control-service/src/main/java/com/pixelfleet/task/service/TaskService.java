package com.pixelfleet.task.service;

import com.pixelfleet.command.RobotCommandPublisher;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.robot.service.RobotService;
import com.pixelfleet.task.dispatch.AssignmentPolicy;
import com.pixelfleet.traffic.LaneGraph;
import com.pixelfleet.traffic.TrafficController;
import com.pixelfleet.task.domain.TaskPriority;
import com.pixelfleet.task.domain.TaskStatus;
import com.pixelfleet.task.domain.TransportTask;
import com.pixelfleet.task.event.TaskLifecycleChanged;
import com.pixelfleet.task.repository.TransportTaskRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the transport-task lifecycle: creation, dispatch (matching a pending
 * task to an available robot), and the completion/failure/retry paths. This is the
 * heart of the control server — the assignment policy in {@link #dispatchOnce} is
 * where fleet efficiency is won or lost.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int MAX_RETRIES = 3;

    /**
     * 픽업 도착 후 내줄 두 번째 구간(픽업→하역) 계획. 작업코드 기준.
     *
     * <p>배차 때 leg1만 예약하므로 leg2는 여기 담아 뒀다가, 로봇이 픽업 도착을 알리면
     * 그때 예약을 시도한다. 예약에 실패하면 그대로 남겨 두고 다음 패스에서 다시 시도한다
     * (로봇은 픽업 지점에서 기다린다).
     */
    private final java.util.Map<String, LaneGraph.RoutePlan> pendingSecondLeg = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 픽업 도착을 <b>실제로 보고한</b> 작업들. leg2는 이 작업들에만 내준다.
     *
     * <p>작업은 배차 직후(로봇이 출발하자마자) IN_PROGRESS가 되므로, 상태만 보고 leg2를 보내면
     * 아직 leg1을 달리는 로봇에게 도착한다. 로봇은 그걸 "이미 작업 중"으로 거부하고,
     * 서버는 leg2를 보냈다고 여겨 구간만 잡힌 채 아무도 움직이지 않는다(실제로 그렇게 멈췄다).
     */
    private final java.util.Set<String> arrivedAtPickup = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final TransportTaskRepository taskRepository;
    private final RobotService robotService;
    private final FleetEventService fleetEventService;
    private final RobotCommandPublisher robotCommandPublisher;
    private final AssignmentPolicy assignmentPolicy;
    private final LaneGraph laneGraph;
    private final TrafficController trafficController;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(
            TransportTaskRepository taskRepository,
            RobotService robotService,
            FleetEventService fleetEventService,
            RobotCommandPublisher robotCommandPublisher,
            AssignmentPolicy assignmentPolicy,
            LaneGraph laneGraph,
            TrafficController trafficController,
            ApplicationEventPublisher eventPublisher
    ) {
        this.taskRepository = taskRepository;
        this.robotService = robotService;
        this.fleetEventService = fleetEventService;
        this.robotCommandPublisher = robotCommandPublisher;
        this.assignmentPolicy = assignmentPolicy;
        this.laneGraph = laneGraph;
        this.trafficController = trafficController;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<TransportTask> findAll() {
        return taskRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public TransportTask getById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 작업입니다. id=" + id));
    }

    @Transactional
    public TransportTask create(String taskCode, String originNode, String destinationNode, TaskPriority priority) {
        taskRepository.findByTaskCode(taskCode).ifPresent(t -> {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 작업 코드입니다: " + taskCode);
        });

        TransportTask task = taskRepository.save(
                new TransportTask(taskCode, originNode, destinationNode, priority));
        fleetEventService.record(
                FleetEventType.TASK_CREATED,
                SourceType.OPERATOR, null,
                TargetType.TASK, task.getId(),
                task.getId(), EventSeverity.INFO,
                "Task " + taskCode + " created (" + originNode + " -> " + destinationNode + ")", null);
        return task;
    }

    /**
     * Assign one pending task to the best-matched available robot, if any.
     *
     * <p>Task order: highest priority first, FIFO within the same priority. Robot choice is
     * delegated to {@link AssignmentPolicy} (nearest to origin, battery-aware). Returns the
     * assigned task, or {@code null} when there is nothing to dispatch or no suitable robot
     * (e.g. every free robot is too low on battery — the task then waits).
     *
     * TODO: traffic/deadlock avoidance once more than a couple of robots share the floor.
     */
    @Transactional
    public TransportTask dispatchOnce() {
        List<TransportTask> pending = taskRepository.findByStatusOrderByIdAsc(TaskStatus.PENDING);
        if (pending.isEmpty()) {
            return null;
        }
        // pending is already id-ascending (FIFO); a stable ordering by descending priority
        // keeps FIFO within each priority band.
        TransportTask next = pending.stream()
                .min(Comparator.comparingInt((TransportTask t) -> -t.getPriority().getWeight())
                        .thenComparingLong(TransportTask::getId))
                .orElseThrow();

        // 로봇 상태(텔레메트리)만 믿지 않는다. 배차 직후 로봇이 아직 MOVING을 보고하기 전이면
        // 상태로는 IDLE로 보여 같은 로봇에 두 번 배차될 수 있다. 진행 중 작업 유무(DB)가
        // 더 확실한 근거이므로 함께 거른다.
        List<RobotResponse> available = robotService.findAvailable().stream()
                .filter(r -> !taskRepository.existsByAssignedRobotIdAndStatusIn(
                        r.id(), List.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS)))
                .toList();
        RobotResponse robot = assignmentPolicy.selectRobot(next, available).orElse(null);
        if (robot == null) {
            return null;
        }

        // 경로는 서버가 정한다. **픽업까지(leg1)만 예약하고 보낸다** — 하역까지 통째로 잡으면
        // 픽업이 먼 로봇이 공장을 가로지르는 구간 전체를 요구해 다른 로봇이 거의 못 움직인다.
        double[] here = {robot.posX(), robot.posY()};
        LaneGraph.RoutePlan toPickup = laneGraph.planByNode(here, next.getOriginNode());
        double[] pickup = toPickup.waypoints().get(toPickup.waypoints().size() - 1);
        LaneGraph.RoutePlan toDrop = laneGraph.planByNode(pickup, next.getDestinationNode());

        if (!trafficController.tryReserve(robot.id(), toPickup.segments())) {
            log.info("Traffic: robot {} cannot take {} — leg1 segments busy {}",
                    robot.robotCode(), next.getTaskCode(), laneGraph.describe(toPickup.segments()));
            return null;
        }
        pendingSecondLeg.put(next.getTaskCode(), toDrop);

        List<double[]> waypoints = toPickup.waypoints();

        next.assignTo(robot.id());
        // Mark the robot busy immediately so the next dispatch pass can't double-assign it.
        // The robot's real MOVING/IDLE lifecycle is then driven by its own telemetry.
        robotService.changeStatus(robot.robotCode(), RobotStatus.MOVING, null);
        fleetEventService.record(
                FleetEventType.TASK_ASSIGNED,
                SourceType.SYSTEM, null,
                TargetType.TASK, next.getId(),
                next.getId(), EventSeverity.INFO,
                "Task " + next.getTaskCode() + " assigned to robot " + robot.robotCode(), null);

        // Downlink: tell the robot to execute the task (no-op if the publisher is disabled/offline).
        robotCommandPublisher.sendGoto(
                robot.robotCode(), next.getTaskCode(),
                next.getOriginNode(), next.getDestinationNode(), waypoints);
        return next;
    }

    /**
     * 로봇이 픽업 지점에 도착했다는 보고. 이제서야 두 번째 구간(픽업→하역)을 예약하고 내준다.
     *
     * <p>확보하지 못하면 아무것도 하지 않는다 — 로봇은 픽업에서 기다리고,
     * {@link #grantPendingSecondLegs()}가 다음 패스에서 다시 시도한다.
     */
    @Transactional
    public void markPickedUp(String taskCode) {
        TransportTask task = requireByCode(taskCode);
        // 픽업에 도착했으니 leg1은 다 지났다. **leg2를 요청하기 전에 먼저 전부 놓는다.**
        //
        // 쥔 채로 요청하면(hold-and-wait) 여러 로봇이 서로가 쥔 구간을 기다려 교착에 빠진다.
        // 실제로 그렇게 만들었다가 로봇 4대가 모두 픽업에서 멈춰 아무것도 완료되지 않았다.
        // 픽업 자리는 레인 밖 정차 지점이므로 놓아도 통행을 막지 않는다.
        trafficController.release(task.getAssignedRobotId());
        arrivedAtPickup.add(taskCode);
        grantSecondLeg(task);
    }

    /** 픽업에서 대기 중인 로봇들에게 leg2를 내주려 재시도한다(배차 스케줄러가 호출). */
    @Transactional
    public void grantPendingSecondLegs() {
        if (arrivedAtPickup.isEmpty()) {
            return;
        }
        // 픽업에 도착해 실제로 기다리는 작업만 대상으로 한다.
        for (String taskCode : List.copyOf(arrivedAtPickup)) {
            taskRepository.findByTaskCode(taskCode)
                    .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                    .ifPresent(this::grantSecondLeg);
        }
    }

    private void grantSecondLeg(TransportTask task) {
        LaneGraph.RoutePlan leg2 = pendingSecondLeg.get(task.getTaskCode());
        if (leg2 == null || task.getAssignedRobotId() == null) {
            return;
        }
        // 이 시점에 로봇은 아무 구간도 쥐고 있지 않다(markPickedUp에서 놓았다).
        // 확보 실패해도 남의 길을 막지 않으므로 그냥 기다렸다 다음 패스에서 재시도한다.
        if (!trafficController.tryReserve(task.getAssignedRobotId(), leg2.segments())) {
            log.debug("Traffic: {} waiting at pickup — leg2 segments busy {}",
                    task.getTaskCode(), laneGraph.describe(leg2.segments()));
            return;
        }
        pendingSecondLeg.remove(task.getTaskCode());
        arrivedAtPickup.remove(task.getTaskCode());

        String robotCode = robotService.findAll().stream()
                .filter(r -> r.id().equals(task.getAssignedRobotId()))
                .map(RobotResponse::robotCode)
                .findFirst()
                .orElse(null);
        if (robotCode == null) {
            return;
        }
        robotCommandPublisher.sendGoto(
                robotCode, task.getTaskCode(),
                task.getOriginNode(), task.getDestinationNode(), leg2.waypoints());
        log.debug("Granted second leg for {} to robot {}", task.getTaskCode(), robotCode);
    }

    @Transactional
    public void markStarted(String taskCode) {
        TransportTask task = requireByCode(taskCode);
        task.start();
        fleetEventService.record(
                FleetEventType.TASK_STARTED,
                SourceType.ROBOT, task.getAssignedRobotId(),
                TargetType.TASK, task.getId(),
                task.getId(), EventSeverity.INFO,
                "Task " + taskCode + " started", null);
    }

    @Transactional
    public void markCompleted(String taskCode) {
        TransportTask task = requireByCode(taskCode);
        task.complete();
        pendingSecondLeg.remove(taskCode);
        arrivedAtPickup.remove(taskCode);
        // 주행이 끝났으니 쥐고 있던 레인 구간을 모두 놓는다.
        trafficController.release(task.getAssignedRobotId());
        fleetEventService.record(
                FleetEventType.TASK_COMPLETED,
                SourceType.ROBOT, task.getAssignedRobotId(),
                TargetType.TASK, task.getId(),
                task.getId(), EventSeverity.INFO,
                "Task " + taskCode + " completed", null);
        // 모듈 밖에도 알린다(커밋 후 MQTT 발행). fleet은 누가 듣는지 모른다.
        eventPublisher.publishEvent(TaskLifecycleChanged.completed(taskCode));
    }

    /**
     * Record a task failure. If the retry budget allows, re-open the task to PENDING so
     * the next dispatch picks it up again; otherwise leave it FAILED for operator review.
     */
    @Transactional
    public void markFailed(String taskCode, String reason) {
        TransportTask task = requireByCode(taskCode);
        Long robotId = task.getAssignedRobotId();
        task.fail(reason);
        pendingSecondLeg.remove(taskCode);
        arrivedAtPickup.remove(taskCode);
        // 실패해도 구간은 놓아야 한다 — 안 그러면 그 경로가 영원히 막힌다.
        trafficController.release(robotId);
        fleetEventService.record(
                FleetEventType.TASK_FAILED,
                SourceType.ROBOT, task.getAssignedRobotId(),
                TargetType.TASK, task.getId(),
                task.getId(), EventSeverity.ERROR,
                "Task " + taskCode + " failed: " + reason, null);

        if (task.getRetryCount() < MAX_RETRIES) {
            task.retry();
            fleetEventService.record(
                    FleetEventType.TASK_RETRIED,
                    SourceType.SYSTEM, null,
                    TargetType.TASK, task.getId(),
                    task.getId(), EventSeverity.WARNING,
                    "Task " + taskCode + " re-queued (attempt " + (task.getRetryCount() + 1) + ")", null);
            return;
        }

        // 재시도가 남아 있으면 아직 진행 중인 작업이다 — 최종 실패일 때만 밖에 알린다.
        // (재시도할 작업을 실패로 알리면 받은 쪽이 전표를 성급히 정리한다.)
        eventPublisher.publishEvent(TaskLifecycleChanged.failed(taskCode, reason));
    }

    private TransportTask requireByCode(String taskCode) {
        return taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 작업입니다. code=" + taskCode));
    }
}
