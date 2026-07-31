package com.pixelwms.fleet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * fleet 모듈 호출 설정.
 *
 * <p>WMS는 fleet의 <b>REST 계약</b>만 알고 코드·DB는 모른다(컴포저블 원칙). 반대로 fleet은
 * WMS를 전혀 모른다 — 완료 통지는 fleet이 발행하는 MQTT 이벤트를 WMS가 구독해 받는다.
 */
@ConfigurationProperties(prefix = "fleet")
public class FleetClientProperties {

    /** fleet 서비스 베이스 URL(기본 http://localhost:9002). */
    private String baseUrl = "http://localhost:9002";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
