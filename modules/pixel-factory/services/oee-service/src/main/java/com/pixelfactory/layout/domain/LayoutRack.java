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
 * 렉(선반).
 *
 * <p><b>용량은 여기, 수량은 WMS에.</b> 몇 개가 들어가는지는 선반의 물리 속성이라 평면도가 갖고,
 * 지금 몇 개가 있는지는 재고라 WMS가 갖는다. 대시보드가 {@code rackCode}와 WMS의
 * {@code locationCode}를 맞춰 적재율을 그린다 — 모듈 간 FK 없이 코드로만 잇는다.
 */
@Getter
@Entity
@Table(name = "layout_racks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutRack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String rackCode;

    @Column(nullable = false, length = 30)
    private String buildingCode;

    @Column(nullable = false)
    private Short floorNo;

    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    /** V(세로) / H(가로) — 지도에서 선반이 놓인 방향. */
    @Column(nullable = false, length = 10)
    private String orientation;

    /** 열 */
    @Column(nullable = false)
    private Short columnsCount;

    /** 단 */
    @Column(nullable = false)
    private Short levelsCount;

    /** 만재 수량(EA). 적재율 = WMS 재고 수량 / 이 값. */
    @Column(nullable = false)
    private Integer capacityQty;
}
