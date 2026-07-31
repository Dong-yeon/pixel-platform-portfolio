package com.pixelqms.factory;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "factory")
public class FactoryClientProperties {

    /** factory 서비스 베이스 URL(기본 http://localhost:9001). */
    private String baseUrl = "http://localhost:9001";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
