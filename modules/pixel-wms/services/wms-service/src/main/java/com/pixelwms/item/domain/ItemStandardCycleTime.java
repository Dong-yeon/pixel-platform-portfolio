package com.pixelwms.item.domain;

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
 * 품번 × 공정 표준CT (D6).
 *
 * <p>표준CT는 설비가 아니라 <b>무엇을 어느 공정으로 만드는가</b>의 속성이다. factory가
 * 설비 고정값으로 OEE의 P를 계산하면 같은 설비에서 다른 품번을 돌릴 때 지표가 틀어진다.
 */
@Getter
@Entity
@Table(name = "item_standard_cycle_times")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemStandardCycleTime extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 30)
    private String processCode;

    @Column(nullable = false)
    private Integer standardCycleTimeMs;

    public ItemStandardCycleTime(Long itemId, String processCode, Integer standardCycleTimeMs) {
        this.itemId = itemId;
        this.processCode = processCode;
        this.standardCycleTimeMs = standardCycleTimeMs;
    }
}
