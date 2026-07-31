package com.pixelqms.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qms.notification")
public class NotificationProperties {

    /** outbox(기본) | smtp — 어느 {@code NotificationSender}를 쓸지. */
    private String sender = "outbox";

    /** MRB 심의 요청을 받는 주소. */
    private String qualityTeamAddress = "quality@pixelfactory.local";

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getQualityTeamAddress() {
        return qualityTeamAddress;
    }

    public void setQualityTeamAddress(String qualityTeamAddress) {
        this.qualityTeamAddress = qualityTeamAddress;
    }
}
