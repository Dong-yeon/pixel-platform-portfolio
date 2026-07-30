package com.pixelfactory.oee.domain;

import java.time.Duration;

/**
 * OEE 계산의 입력. 구간·이벤트 집계가 끝난 <b>숫자만</b> 담는다.
 *
 * <p>계산기를 DB에서 떼어내기 위한 경계다 — 덕분에 손계산 케이스를 그대로 단위 테스트할 수 있다.
 *
 * @param plannedTime      계획가동시간 (A의 분모). 시프트 ∩ 조회구간 − 휴식 − PLANNED_STOP
 * @param operatingTime    실가동시간 (A의 분자, P의 분모). RUNNING 구간의 합
 * @param idealCycleTimeMs 표준 사이클타임. 지금은 설비 고정값이지만 원래 품번 단위다(D6)
 * @param producedQty      총생산수 (양품 + 불량)
 * @param defectQty        불량수
 */
public record OeeInput(
        Duration plannedTime,
        Duration operatingTime,
        long idealCycleTimeMs,
        long producedQty,
        long defectQty
) {

    public OeeInput {
        if (plannedTime.isNegative() || operatingTime.isNegative()) {
            throw new IllegalArgumentException("시간은 음수가 될 수 없다");
        }
        if (producedQty < 0 || defectQty < 0) {
            throw new IllegalArgumentException("생산수·불량수는 음수가 될 수 없다");
        }
        if (defectQty > producedQty) {
            throw new IllegalArgumentException("불량수가 총생산수를 넘을 수 없다: " + defectQty + " > " + producedQty);
        }
    }

    public long goodQty() {
        return producedQty - defectQty;
    }
}
