package com.pixelfactory.quality;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 품질 신호 설정.
 *
 * <p>{@code defectThreshold}: 한 작업지시의 누적 불량이 이 수를 넘으면 검사를 요청한다
 * (한 번만). factory는 <b>누가 검사하는지 모른다</b> — 토픽에 신호를 던질 뿐이다.
 */
@ConfigurationProperties(prefix = "quality")
public class QualityProperties {

    /** 검사 요청 임계 불량 수. 기본 3. */
    private int defectThreshold = 3;

    public int getDefectThreshold() {
        return defectThreshold;
    }

    public void setDefectThreshold(int defectThreshold) {
        this.defectThreshold = defectThreshold;
    }
}
