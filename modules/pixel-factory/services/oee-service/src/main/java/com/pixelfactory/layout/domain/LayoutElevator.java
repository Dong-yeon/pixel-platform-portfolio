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
 * 화물 엘리베이터.
 *
 * <p><b>물건만 오르내린다.</b> AMR은 자기 층에 머물며 승강장({@code ELEVATOR} 노드)에서 싣고
 * 내린다 — 그래서 층마다 로봇이 따로 있고, 로봇은 층을 넘지 않는다.
 *
 * <p>샤프트는 수직으로 관통하므로 층이 달라도 <b>같은 자리</b>에 그린다.
 */
@Getter
@Entity
@Table(name = "layout_elevators")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutElevator extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String elevatorCode;

    @Column(nullable = false, length = 30)
    private String buildingCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    /** 닿는 층(쉼표 구분, 예 "1,2,3"). */
    @Column(nullable = false, length = 30)
    private String servesFloors;
}
