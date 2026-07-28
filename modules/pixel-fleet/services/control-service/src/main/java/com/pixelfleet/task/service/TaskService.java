package com.pixelfleet.task.service;

import com.pixelfleet.command.RobotCommandPublisher;
import com.pixelfleet.common.exception.BusinessException;
import com.pixelfleet.common.exception.ErrorCode;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.robot.service.RobotService;
import com.pixelfleet.task.dispatch.AssignmentPolicy;
import com.pixelfleet.task.domain.TaskPriority;
import com.pixelfleet.task.domain.TaskStatus;
import com.pixelfleet.task.domain.TransportTask;
import com.pixelfleet.task.repository.TransportTaskRepository;
import java.util.Comparator;
import java.util.List;
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

    private static final int MAX_RETRIES = 3;

    private final TransportTaskRepository taskRepository;
    private final RobotService robotService;
    private final FleetEventService fleetEventService;
    private final RobotCommandPublisher robotCommandPublisher;
    private final AssignmentPolicy assignmentPolicy;

    public TaskService(
            TransportTaskRepository taskRepository,
            RobotService robotService,
            FleetEventService fleetEventService,
            RobotCommandPublisher robotCommandPublisher,
            AssignmentPolicy assignmentPolicy
    ) {
        this.taskRepository = taskRepository;
        this.robotService = robotService;
        this.fleetEventService = fleetEventService;
        this.robotCommandPublisher = robotCommandPublisher;
        this.assignmentPolicy = assignmentPolicy;
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

        List<RobotResponse> available = robotService.findAvailable();
        RobotResponse robot = assignmentPolicy.selectRobot(next, available).orElse(null);
        if (robot == null) {
            return null;
        }

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
                robot.robotCode(), next.getTaskCode(), next.getOriginNode(), next.getDestinationNode());
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
        task.fail(reason);
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
