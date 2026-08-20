package com.pixelfleet.robot.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * 조작자가 이 로봇을 배차 대상에서 뺐다(휴무). {@link RobotStatus}와 달리 텔레메트리로
     * 바뀌지 않는다 — 다음 하트비트가 이 결정을 덮어쓰면 안 되기 때문에 마스터(Postgres)에 둔다.
     */
    @Column(nullable = false)
    private boolean offDuty;

    /** 조작자가 이 로봇을 고장/점검 등의 이유로 완전히 잠갔다. off-duty보다 강한 배제. */
    @Column(nullable = false)
    private boolean disabled;

    /**
     * 로봇 종류(P21) — {@code AMR}은 공장 레인망을 타고, {@code AGV}(옛 이름: 랙 피더)는 자기 존의
     * 렉만 오간다. 기본값 AMR(기존 로봇 전부 이 값).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RobotType robotType = RobotType.AMR;

    /**
     * AGV 전용 — 이 로봇이 담당하는 피킹 클러스터(피킹존 노드 코드 그대로 재사용,
     * 예: {@code WH-2F-P1}). AMR은 항상 {@code null}이다.
     */
    @Column(length = 30)
    private String zoneCode;

    public Robot(String robotCode, String name, short floorNo) {
        this.robotCode = robotCode;
        this.name = name;
        this.floorNo = floorNo;
        this.robotType = RobotType.AMR;
    }

    public Robot(String robotCode, String name, short floorNo, RobotType robotType, String zoneCode) {
        this.robotCode = robotCode;
        this.name = name;
        this.floorNo = floorNo;
        this.robotType = robotType;
        this.zoneCode = zoneCode;
    }

    public void markOffDuty() {
        this.offDuty = true;
    }

    public void markOnDuty() {
        this.offDuty = false;
    }

    public void disable() {
        this.disabled = true;
    }

    public void enable() {
        this.disabled = false;
    }
}
