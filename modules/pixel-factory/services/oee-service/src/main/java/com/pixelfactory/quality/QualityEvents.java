package com.pixelfactory.quality;

/**
 * 품질 신호 — 모듈 밖으로 나가는 통지의 씨앗.
 *
 * <p>factory는 <b>수신자를 지목하지 않는다</b>. 앱 이벤트로 던지면 MQTT 어댑터가 커밋 후
 * 토픽에 발행하고, 관심 있는 모듈(QMS)이 구독한다.
 */
public final class QualityEvents {

    private QualityEvents() {
    }

    /** 불량이 임계를 넘어 검사가 필요하다. */
    public record InspectionRequested(
            String equipmentCode,
            String workOrderNo,
            String lotNo,
            int defectQty
    ) {
    }
}
