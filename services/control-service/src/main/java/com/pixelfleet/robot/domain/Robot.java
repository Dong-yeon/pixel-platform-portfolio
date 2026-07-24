package com.pixelfleet.robot.domain;

import com.pixelfleet.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Master + live-state record for a single AMR. Position/battery/status are the last
 * values reported over MQTT; the authoritative history lives in fleet_events.
 */
@Getter
@Entity
@Table(name = "robots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Robot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String robotCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RobotStatus status;

    @Column(nullable = false)
    private int batteryPercent;

    @Column(nullable = false)
    private double posX;

    @Column(nullable = false)
    private double posY;

    private LocalDateTime lastHeartbeatAt;

    public Robot(String robotCode, String name) {
        this.robotCode = robotCode;
        this.name = name;
        this.status = RobotStatus.OFFLINE;
        this.batteryPercent = 100;
        this.posX = 0.0;
        this.posY = 0.0;
    }

    public void changeStatus(RobotStatus status) {
        this.status = status;
        this.lastHeartbeatAt = LocalDateTime.now();
    }

    public void updatePosition(double posX, double posY) {
        this.posX = posX;
        this.posY = posY;
        this.lastHeartbeatAt = LocalDateTime.now();
    }

    public void updateBattery(int batteryPercent) {
        this.batteryPercent = batteryPercent;
        this.lastHeartbeatAt = LocalDateTime.now();
    }

    public boolean isAvailable() {
        return status == RobotStatus.IDLE;
    }
}
