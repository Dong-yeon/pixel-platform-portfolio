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
import com.pixelfleet.task.repository.TransportTaskRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final TransportTaskRepository taskRepository;
    private final RobotService robotService;
    private final FleetEventService fleetEventService;
    private final RobotCommandPublisher robotCommandPublisher;
    private final AssignmentPolicy assignmentPolicy;
    private final LaneGraph laneGraph;
    private final TrafficController trafficController;

    public TaskService(
            TransportTaskRepository taskRepository,
            RobotService robotService,
            FleetEventService fleetEventService,
            RobotCommandPublisher robotCommandPublisher,
            AssignmentPolicy assignmentPolicy,
            LaneGraph laneGraph,
            TrafficController trafficController
    ) {
        this.taskRepository = taskRepository;
        this.robotService = robotService;
        this.fleetEventService = fleetEventService;
        this.robotCommandPublisher = robotCommandPublisher;
        this.assignmentPolicy = assignmentPolicy;
        this.laneGraph = laneGraph;
        this.trafficController = trafficController;
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

        // 경로는 서버가 정한다: 현재 위치 → 픽업 → 하역, 두 구간을 이어 붙인다.
        double[] here = {robot.posX(), robot.posY()};
        LaneGraph.RoutePlan toPickup = laneGraph.planByNode(here, next.getOriginNode());
        double[] pickup = toPickup.waypoints().get(toPickup.waypoints().size() - 1);
        LaneGraph.RoutePlan toDrop = laneGraph.planByNode(pickup, next.getDestinationNode());

        List<String> segments = new ArrayList<>(toPickup.segments());
        toDrop.segments().stream().filter(seg -> !segments.contains(seg)).forEach(segments::add);

        // 지나갈 구간을 전부 확보하지 못하면 배차하지 않는다 — 다른 로봇과 겹치기 때문.
        // 작업은 PENDING으로 남아 다음 배차 패스에서 다시 시도한다.
        if (!trafficController.tryReserve(robot.id(), segments)) {
            log.info("Traffic: robot {} cannot take {} — segments busy {}",
                    robot.robotCode(), next.getTaskCode(), laneGraph.describe(segments));
            return null;
        }

        List<double[]> waypoints = new ArrayList<>(toPickup.waypoints());
        waypoints.addAll(toDrop.waypoints());

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
        // 주행이 끝났으니 쥐고 있던 레인 구간을 모두 놓는다.
        trafficController.release(task.getAssignedRobotId());
        fleetEventService.record(
                FleetEventType.TASK_COMPLETED,
                SourceType.ROBOT, task.getAssignedRobotId(),
                TargetType.TASK, task.getId(),
                task.getId(), EventSeverity.INFO,
                "Task " + taskCode + " completed", null);
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
        }
    }

    private TransportTask requireByCode(String taskCode) {
        return taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 작업입니다. code=" + taskCode));
    }
}
