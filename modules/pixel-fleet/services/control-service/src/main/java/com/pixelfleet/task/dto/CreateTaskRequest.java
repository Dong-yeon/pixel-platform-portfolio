package com.pixelfleet.task.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 호환 어댑터 입력 — 예전 "출발→도착" 작업 생성. 내부에서 2스텝 주문으로 변환된다.
 * P19-2에서 M4형 주문 API로 소비자가 옮겨가면 삭제.
 *
 * @param materialId 선택 — 상류가 실어 보내는 물리 단위 식별자(P23 D6, M4의 containerId에
 *                   대응). WMS라면 파렛트 코드가 온다. 생략해도 기존 호출부는 그대로 동작한다.
 */
public record CreateTaskRequest(
        @NotBlank String taskCode,
        @NotBlank String originNode,
        @NotBlank String destinationNode,
        @NotBlank String priority,
        String materialId
) {

    /** LOW/NORMAL/HIGH/URGENT → M4식 정수(클수록 높음). */
    public int priorityValue() {
        return switch (priority) {
            case "LOW" -> 0;
            case "NORMAL" -> 1;
            case "HIGH" -> 2;
            case "URGENT" -> 3;
            default -> 1;
        };
    }
}
