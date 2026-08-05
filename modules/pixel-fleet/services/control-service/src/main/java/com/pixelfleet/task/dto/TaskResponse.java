package com.pixelfleet.task.dto;

import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.domain.OrderStep;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <b>호환 어댑터 뷰</b> — 주문(다단 스텝)을 예전 "작업(출발→도착)" 모양으로 눕혀서 보여준다.
 *
 * <p>P19-1은 엔진만 바꾸고 겉(REST 계약)은 그대로 두는 단계라, 대시보드·WMS가 아는
 * 필드명(taskCode/originNode/destinationNode/상태 문자열)을 유지한다. P19-2에서 M4형
 * 주문 API로 소비자를 옮기면 이 파일은 컨트롤러와 함께 삭제된다.
 */
public record TaskResponse(
        Long id,
        String taskCode,
        String originNode,
        String destinationNode,
        String priority,
        String status,
        Long assignedRobotId,
        int retryCount,
        short floorNo,
        String handoffDestination,
        String handoffOf,
        LocalDateTime assignedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String failureReason
) {

    public static TaskResponse from(FleetOrder order) {
        List<OrderStep> steps = order.getSteps();
        String origin = steps.isEmpty() ? "?" : steps.get(0).getLocationNode();
        String destination = steps.isEmpty() ? "?" : steps.get(steps.size() - 1).getLocationNode();
        return new TaskResponse(
                order.getId(),
                order.getOrderCode(),
                origin,
                destination,
                priorityName(order.getPriority()),
                legacyStatus(order),
                order.getAssignedRobotId(),
                order.getFailureNum(),
                order.getFloorNo(),
                order.getHandoffDestination(),
                order.getHandoffOf(),
                order.getAssignedAt(),
                order.getStartedAt(),
                order.getFinishedAt(),
                order.getFailureReason()
        );
    }

    private static String priorityName(int priority) {
        return switch (priority) {
            case 0 -> "LOW";
            case 1 -> "NORMAL";
            case 2 -> "HIGH";
            default -> "URGENT";
        };
    }

    /** fault(동결)는 예전 어휘로 FAILED다 — 대시보드 배지 색이 그 이름을 안다. */
    private static String legacyStatus(FleetOrder order) {
        if (order.isFault()) {
            return "FAILED";
        }
        return switch (order.getStatus()) {
            case TO_BE_ALLOCATED -> "PENDING";
            case ALLOCATED -> "ASSIGNED";
            case EXECUTING, PENDING -> "IN_PROGRESS";
            case DONE -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
        };
    }
}
