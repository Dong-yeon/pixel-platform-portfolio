package com.pixelwms.stock.domain;

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
 * 창고 로케이션.
 *
 * <p>{@code nodeCode}는 factory 평면도의 노드 코드(WAREHOUSE, SHIPPING …)와 맞춘다.
 * AMR 운송의 출발/도착지가 되므로 어긋나면 로봇이 엉뚱한 좌표로 간다 — fleet은 모르는
 * 노드 코드도 거부하지 않고 해시 좌표로 "resolve"해 버리기 때문에 조용히 틀어진다.
 */
@Getter
@Entity
@Table(name = "locations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String locationCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String nodeCode;

    /**
     * 파렛트 슬롯 용량 (P23 D7). factory {@code layout_racks.capacityQty}(EA, "만재 수량")와는
     * 다른 축이다 — 몇 개가 아니라 몇 장(파렛트)까지 앉는가. null이면 상한을 두지 않는다
     * (레거시·통과 지점 로케이션).
     */
    @Column
    private Integer maxPallet;

    /**
     * 안전재고 수량 (P26 D1) — 이 로케이션의 이 로케이션 재고가 이 값 아래로 떨어지면
     * 자동 보충 대상이다. {@code max_pallet}과 같은 자리(WMS 자체 값, null이면 모니터링
     * 안 함) — 품목 총량이 아니라 <b>로케이션 단위</b>다(로봇이 실제로 옮길 수 있는 것만
     * 다룬다, 설계 근거: docs/p26-safety-stock-replenishment-design.md 0절).
     */
    @Column
    private Integer safetyStockQty;

    public Location(String locationCode, String name, String nodeCode) {
        this.locationCode = locationCode;
        this.name = name;
        this.nodeCode = nodeCode;
    }
}
