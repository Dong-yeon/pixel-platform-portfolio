package com.pixelfactory.oee.domain;

import java.time.Duration;

/**
 * OEE 계산 결과. 비율(0.0~)과 함께 <b>계산 근거가 된 원값</b>을 같이 실어 보낸다 —
 * 숫자만 보고는 왜 그렇게 나왔는지 알 수 없기 때문이다.
 *
 * @param performanceAnomaly P가 1.0을 넘었다는 신호. <b>값을 자르지 않고 플래그로 알린다.</b>
 *                           표준CT가 실제보다 크게 잡혀 있다는 뜻이라 숨기면 원인이 묻힌다.
 */
public record OeeResult(
        double availability,
        double performance,
        double quality,
        double oee,
        boolean performanceAnomaly,
        Duration plannedTime,
        Duration operatingTime,
        long producedQty,
        long defectQty
) {

    /** 계획가동시간이 0이면 평가 대상이 아니다(시프트 밖 구간 등) — 0으로 채운 결과. */
    public static OeeResult notApplicable(Duration plannedTime, Duration operatingTime) {
        return new OeeResult(0, 0, 0, 0, false, plannedTime, operatingTime, 0, 0);
    }
}
