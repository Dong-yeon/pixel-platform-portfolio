package com.pixelfleet.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * M4형 주문 생성 입력(P24) — 스텝 배열을 직접 받는다. {@code TaskController}(호환
 * 어댑터, 2필드 출발→도착)와 달리 여러 스텝을 명시할 수 있지만, 지금 유일한 실사용
 * 호출부(WMS)는 여전히 스텝 2개(pickup/dropoff)만 보낸다 — 스텝 배열을 그대로 받아 두는
 * 것은 향후(add-steps 등) 확장 여지를 남겨 두기 위함이다.
 *
 * @param externalId 상류 전표 번호(M4의 {@code ref_uuid}에 대응). 완료/실패 통지의 열쇠.
 * @param priority   0(LOW)~3(URGENT), 클수록 높다. null이면 1(NORMAL).
 * @param stepFixed  봉인 여부. null이면 true(봉인) — 스텝 추가를 기다리는 미봉인 주문은
 *                   지금 실사용 호출부가 없다.
 * @param materialId 물리 단위 식별자(P23 D6, 예: WMS 파렛트 코드). 선택.
 */
public record CreateOrderRequest(
        String externalId,
        @NotEmpty List<@Valid StepRequest> steps,
        Integer priority,
        Boolean stepFixed,
        String materialId
) {

    public record StepRequest(@NotBlank String location, boolean forLoad, boolean forUnload) {
    }
}
