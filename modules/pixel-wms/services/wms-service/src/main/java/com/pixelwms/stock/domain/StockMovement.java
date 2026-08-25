package com.pixelwms.stock.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재고 이동 이력(이벤트 소싱). 재고 수량 변화의 단일 근거다. */
@Getter
@Entity
@Table(name = "stock_movements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long locationId;

    /**
     * 이 이동이 어느 파렛트의 것인지(P23). null이면 P23 이전에 남은 이력이거나 파렛트
     * 개념이 없던 시절의 데이터다 — 강제로 채우지 않는다.
     */
    @Column
    private Long palletId;

    /** 증가는 양수, 차감은 음수. */
    @Column(nullable = false)
    private Integer quantityDelta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType movementType;

    /** 근거 전표 번호(입고/출고 지시 번호 등). */
    @Column(length = 50)
    private String referenceNo;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public StockMovement(Long itemId, Long locationId, Long palletId, Integer quantityDelta,
                         MovementType movementType, String referenceNo, LocalDateTime occurredAt) {
        this.itemId = itemId;
        this.locationId = locationId;
        this.palletId = palletId;
        this.quantityDelta = quantityDelta;
        this.movementType = movementType;
        this.referenceNo = referenceNo;
        this.occurredAt = occurredAt;
    }
}
