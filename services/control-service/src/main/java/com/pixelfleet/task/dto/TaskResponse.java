package com.pixelfleet.task.dto;

import com.pixelfleet.task.domain.TaskPriority;
import com.pixelfleet.task.domain.TaskStatus;
import com.pixelfleet.task.domain.TransportTask;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String taskCode,
        String originNode,
        String destinationNode,
        TaskPriority priority,
        TaskStatus status,
        Long assignedRobotId,
        int retryCount,
        LocalDateTime assignedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String failureReason
) {

    public static TaskResponse from(TransportTask t) {
        return new TaskResponse(
                t.getId(),
                t.getTaskCode(),
                t.getOriginNode(),
                t.getDestinationNode(),
                t.getPriority(),
                t.getStatus(),
                t.getAssignedRobotId(),
                t.getRetryCount(),
                t.getAssignedAt(),
                t.getStartedAt(),
                t.getFinishedAt(),
                t.getFailureReason()
        );
    }
}
