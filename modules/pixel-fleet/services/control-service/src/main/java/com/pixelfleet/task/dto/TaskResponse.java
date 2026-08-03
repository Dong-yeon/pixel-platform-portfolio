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
        /** 이 작업이 벌어지는 층 — 같은 층 로봇에게만 배차된다. */
        short floorNo,
        /** 채워져 있으면 이 구간이 끝난 뒤 물건이 엘리베이터를 타고 여기로 간다. */
        String handoffDestination,
        /** 채워져 있으면 이 구간은 엘리베이터에서 물건을 이어받은 뒷 구간이다. */
        String handoffOf,
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
                t.getFloorNo(),
                t.getHandoffDestination(),
                t.getHandoffOf(),
                t.getAssignedAt(),
                t.getStartedAt(),
                t.getFinishedAt(),
                t.getFailureReason()
        );
    }
}
