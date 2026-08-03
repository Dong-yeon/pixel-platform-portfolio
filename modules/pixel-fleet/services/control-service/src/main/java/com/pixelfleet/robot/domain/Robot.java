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

    /**
     * 이 로봇이 일하는 층. <b>로봇은 층을 오가지 못한다</b> — 창고동 엘리베이터는 화물용이라
     * 물건만 태운다. 그래서 층은 라이브 상태가 아니라 마스터(변하지 않는 배치)다.
     */
    @Column(nullable = false)
    private short floorNo;

    public Robot(String robotCode, String name, short floorNo) {
        this.robotCode = robotCode;
        this.name = name;
        this.floorNo = floorNo;
    }
}
