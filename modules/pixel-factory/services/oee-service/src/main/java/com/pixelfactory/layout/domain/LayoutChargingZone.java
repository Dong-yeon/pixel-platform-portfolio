package com.pixelfactory.layout.domain;

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
 * 충전존 — 충전 베이(DOCK 노드)들을 감싸는 구역.
 *
 * <p>도크가 렉 사이에 흩어져 있을 때는 "충전하러 가는 곳"이 화면에서 읽히지 않았고,
 * 로봇이 주차하면서 렉과 겹쳤다(실측). 구역을 비워 두고 베이를 그 안에 나란히 둔다.
 */
@Getter
@Entity
@Table(name = "layout_charging_zones")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutChargingZone extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String zoneCode;

    @Column(nullable = false, length = 30)
    private String buildingCode;

    @Column(nullable = false)
    private Short floorNo;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    @Column(nullable = false)
    private Double width;

    @Column(nullable = false)
    private Double height;
}
