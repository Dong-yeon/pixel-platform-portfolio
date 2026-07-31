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
 * 건물 — 생산동 / 창고동 / 품질동.
 *
 * <p>좌상단 좌표와 크기를 갖는 사각형이다. <b>설비·노드·단말이 어느 건물에 속하는지는
 * 별도 컬럼으로 두지 않는다</b> — 좌표가 이 사각형 안에 있으면 그 건물이다. 컬럼을 더하면
 * 좌표와 소속이 어긋날 수 있는 두 번째 진실이 생긴다.
 */
@Getter
@Entity
@Table(name = "layout_buildings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutBuilding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String buildingCode;

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

    /** 층 수. 창고동만 3이고 나머지는 1이다. */
    @Column(nullable = false)
    private Short floorCount;

    @Column(nullable = false)
    private Short displayOrder;
}
