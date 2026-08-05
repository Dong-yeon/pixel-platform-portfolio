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
 * 두 {@link LayoutNode} 사이의 연결 (P20).
 *
 * <p>factory는 <b>정적 토폴로지</b>만 갖는다 — 이 노드와 저 노드가 물리적으로 이어져 있는가,
 * 기본 통행 비용은 얼마인가. "지금 이 엣지가 막혀 있는가"(동적 장애물) 같은 그때그때 다른
 * 사실은 여기 두지 않는다 — 그건 fleet의 라이브 상태(Redis)다. 로봇 위치가 {@code RobotLiveState}
 * (fleet, Redis)와 로봇 소속 층({@code Robot.floorNo}, factory 마스터)으로 나뉘는 것과 같은
 * 원리다. 설계 근거: {@code docs/p20-layout-routing-design.md} D2·D4.
 *
 * <p>{@code from_node}/{@code to_node}는 {@link LayoutNode#getNodeCode()}를 참조하는 FK다 —
 * 존재하지 않는 노드를 가리키는 엣지가 조용히 생기는 걸 DB가 막아준다.
 */
@Getter
@Entity
@Table(name = "layout_edges")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutEdge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_node", nullable = false, length = 30)
    private String fromNode;

    @Column(name = "to_node", nullable = false, length = 30)
    private String toNode;

    @Column(name = "base_cost", nullable = false)
    private Double baseCost;

    @Column(nullable = false)
    private Boolean bidirectional;

    public LayoutEdge(String fromNode, String toNode, double baseCost, boolean bidirectional) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.baseCost = baseCost;
        this.bidirectional = bidirectional;
    }
}
