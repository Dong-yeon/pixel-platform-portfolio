package com.pixelfactory.layout.domain;

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
 * 평면도 상의 지점 — 충전 도크, 자재 창고, 하역 지점, 출하장.
 *
 * <p>AMR의 목적지가 되는 자리들이다. <b>평면도는 factory가 소유하고</b> fleet은 이 좌표를
 * REST로 받아 캐시한다 — 좌표를 세 모듈에 하드코딩하던 것을 끊기 위한 것이다.
 *
 * <p>{@code pos_x}/{@code pos_y} 컬럼명을 명시하는 이유는 {@code Equipment} 주석 참고
 * (Hibernate 기본 네이밍이 {@code posX} → {@code posx}로 만든다).
 */
@Getter
@Entity
@Table(name = "layout_nodes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutNode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String nodeCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LayoutNodeType nodeType;

    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    /**
     * 소속 건물·층.
     *
     * <p>예전에는 좌표만으로 소속을 알았다(건물은 사각형, 노드는 점). 층이 생기면서 소속이
     * <b>기능</b>이 됐다 — 배차는 같은 층 로봇에게만 가야 하고, 위층 노드는 아래층과 좌표가
     * 겹치므로 좌표로는 구분할 수 없다.
     */
    @Column(nullable = false, length = 30)
    private String buildingCode;

    @Column(nullable = false)
    private Short floorNo;

    public LayoutNode(String nodeCode, String name, LayoutNodeType nodeType, double posX, double posY,
                      String buildingCode, short floorNo) {
        this.nodeCode = nodeCode;
        this.name = name;
        this.nodeType = nodeType;
        this.posX = posX;
        this.posY = posY;
        this.buildingCode = buildingCode;
        this.floorNo = floorNo;
    }
}
