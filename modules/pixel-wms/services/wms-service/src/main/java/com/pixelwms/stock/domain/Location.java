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

    public Location(String locationCode, String name, String nodeCode) {
        this.locationCode = locationCode;
        this.name = name;
        this.nodeCode = nodeCode;
    }
}
