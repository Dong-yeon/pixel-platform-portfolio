package com.pixelfleet.robot.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Master record for an AMR: stable identity only (id/code/name). Live state
 * (status/position/battery) is held in Redis — see {@link RobotLiveState} — and the
 * authoritative history is the fleet_events log.
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

    public Robot(String robotCode, String name) {
        this.robotCode = robotCode;
        this.name = name;
    }
}
