package com.pixelfleet.task.service;

import com.pixelfleet.command.RobotCommandPublisher;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.location.LocationRegistry;
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
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final LocationRegistry locations;
    private final ApplicationEventPublisher eventPublisher;

    /** 화물 엘리베이터가 한 층에서 다른 층으로 물건을 옮기는 데 걸리는 시간. */
    private final int elevatorTravelSeconds;

    public TaskService(
            TransportTaskRepository taskRepository,
            RobotService robotService,
            FleetEventService fleetEventService,
            RobotCommandPublisher robotCommandPublisher,
            AssignmentPolicy assignmentPolicy,
            LaneGraph laneGraph,
            TrafficController trafficController,
            LocationRegistry locations,
            ApplicationEventPublisher eventPublisher,
            @Value("${fleet.elevator.travel-seconds:12}") int elevatorTravelSeconds
    ) {
        this.locations = locations;
        this.elevatorTravelSeconds = elevatorTravelSeconds;
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

        short originFloor = locations.floorOf(originNode);
        short destinationFloor = locations.floorOf(destinationNode);

        // 층이 다르면 **로봇이 따라갈 수 없다** — 엘리베이터는 화물용이라 물건만 태운다.
        // 그래서 출발 층 안에서 승강장까지만 나르는 작업으로 끊고, 최종 목적지는 인수인계로 달아 둔다.
        // 나머지 절반(승강장 → 목적지)은 물건이 그 층에 도착한 뒤 만들어진다.
        boolean crossFloor = originFloor != destinationFloor;
        String legDestination = crossFloor ? elevatorNode(originFloor) : destinationNode;

        TransportTask task = new TransportTask(taskCode, originNode, legDestination, priority, originFloor);
        if (crossFloor) {
            task.handOffTo(destinationNode);
        }
        taskRepository.save(task);

        fleetEventService.record(
                FleetEventType.TASK_CREATED,
                SourceType.OPERATOR, null,
                TargetType.TASK, task.getId(),
                task.getId(), EventSeverity.INFO,
                "Task " + taskCode + " created (" + originNode + " -> " + legDestination + ")"
                        + (crossFloor ? " [엘리베이터로 " + destinationFloor + "층 " + destinationNode + "까지]" : ""),
                null);
        return task;
    }

    /** 층이 다른 이송이 끝난 뒤, 물건이 도착한 층에서 이어받을 작업을 만든다. */
    private void createHandoffLeg(TransportTask finished) {
        String finalDestination = finished.getHandoffDestination();
        short arrivalFloor = locations.floorOf(finalDestination);
        String pickupNode = elevatorNode(arrivalFloor);

        // 코드가 겹치지 않게 앞 구간 코드에 층을 덧붙인다(코드에 unique 제약이 있다).
        String code = finished.getTaskCode() + "-F" + arrivalFloor;
        if (taskRepository.findByTaskCode(code).isPresent()) {
            return; // 완료 보고가 두 번 오더라도 뒷 구간은 하나만 만든다.
        }

        TransportTask leg = new TransportTask(
                code, pickupNode, finalDestination, finished.getPriority(), arrivalFloor);
        leg.continues(finished.getTaskCode(), LocalDateTime.now().plusSeconds(elevatorTravelSeconds));
        taskRepository.save(leg);

        fleetEventService.record(
                FleetEventType.TASK_CREATED,
                SourceType.SYSTEM, null,
                TargetType.TASK, leg.getId(),
                leg.getId(), EventSeverity.INFO,
                "엘리베이터: " + finished.getTaskCode() + " 화물이 " + arrivalFloor + "층으로 이동 중 "
                        + "(" + elevatorTravelSeconds + "초 후 " + pickupNode + "에서 인수)", null);
    }

    /** 승강장 노드 코드 — 평면도 시드 규칙(WH-ELEV-1F/2F/3F)을 따른다. */
    private String elevatorNode(short floor) {
        return "WH-ELEV-" + floor + "F";
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
        LocalDateTime now = LocalDateTime.now();
        // 엘리베이터를 기다리는 뒷 구간은 아직 배차하지 않는다 — 물건이 그 층에 도착하기 전에
        // 로봇을 승강장으로 보내면 빈손으로 서서 자리만 차지한다.
        List<TransportTask> pending = taskRepository.findByStatusOrderByIdAsc(TaskStatus.PENDING).stream()
                .filter(t -> t.isDispatchable(now))
                .toList();
        if (pending.isEmpty()) {
            return null;
        }

        // 로봇 상태(텔레메트리)만 믿지 않는다. 배차 직후 로봇이 아직 MOVING을 보고하기 전이면
        // 상태로는 IDLE로 보여 같은 로봇에 두 번 배차될 수 있다. 진행 중 작업 유무(DB)가
        // 더 확실한 근거이므로 함께 거른다.
        List<RobotResponse> available = robotService.findAvailable().stream()
                .filter(r -> !taskRepository.existsByAssignedRobotIdAndStatusIn(
                        r.id(), List.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS)))
                .toList();
        if (available.isEmpty()) {
            return null;
        }

        // pending is already id-ascending (FIFO); a stable ordering by descending priority
        // keeps FIFO within each priority band.
        //
        // **맨 앞 하나만 시도하고 끝내지 않는다.** 예전엔 최우선 작업 하나를 골라 로봇을 못 찾으면
        // 그대로 돌아갔다. 로봇 풀이 하나였을 땐 "아무도 못 하니 아무도 못 한다"가 맞았지만,
        // 배차가 층별로 갈리면서 틀린 말이 됐다 — 1층 작업이 큐 앞을 채우면 2·3층 로봇은
        // 자기 층 작업이 밀려 있어도 영원히 놀았다(실측: 2층 대기 2건, 완료 0건, 로봇은 IDLE).
        // 앞에서부터 훑어 **배차 가능한 첫 작업**을 내보낸다.
        List<TransportTask> ordered = pending.stream()
                .sorted(Comparator.comparingInt((TransportTask t) -> -t.getPriority().getWeight())
                        .thenComparingLong(TransportTask::getId))
                .toList();

        for (TransportTask candidate : ordered) {
            TransportTask dispatched = tryDispatch(candidate, available);
            if (dispatched != null) {
                return dispatched;
            }
        }
        return null;
    }

    /**
     * 작업 하나를 배차해 본다. 맞는 로봇이 없거나 레인을 못 잡으면 {@code null} —
     * 부작용 없이 돌아가므로 호출한 쪽이 다음 작업으로 넘어가도 된다.
     */
    private TransportTask tryDispatch(TransportTask next, List<RobotResponse> available) {
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

        // 승강장까지 나른 물건은 여기서 로봇을 떠난다 — 엘리베이터가 받아 다른 층으로 올린다.
        // 뒷 구간은 그 층 로봇이 이어받는다(로봇은 층을 오가지 못한다).
        //
        // **이 구간이 끝난 것을 밖에 알리지 않는다.** 듣는 쪽(WMS)에게 완료는 "물건이 목적지에
        // 도착했다"는 뜻이고, 그걸 근거로 출고지시를 정리하며 재고를 뺀다. 승강장은 목적지가
        // 아니다 — 여기서 알리면 아직 옮기는 중인 물건을 도착한 것으로 처리한다.
        if (task.getHandoffDestination() != null) {
            createHandoffLeg(task);
            return;
        }

        // 모듈 밖에는 **처음 만들어진 작업코드로** 알린다. 층을 넘느라 구간이 쪼개진 건
        // fleet 안의 사정이고, 밖에서는 자기가 낸 코드 하나로 결과를 기다리고 있다.
        eventPublisher.publishEvent(TaskLifecycleChanged.completed(
                task.getHandoffOf() != null ? task.getHandoffOf() : taskCode));
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
        // 완료와 마찬가지로 밖에는 처음 만들어진 코드로 알린다.
        eventPublisher.publishEvent(TaskLifecycleChanged.failed(
                task.getHandoffOf() != null ? task.getHandoffOf() : taskCode, reason));
    }

    private TransportTask requireByCode(String taskCode) {
        return taskRepository.findByTaskCode(taskCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 작업입니다. code=" + taskCode));
    }
}
