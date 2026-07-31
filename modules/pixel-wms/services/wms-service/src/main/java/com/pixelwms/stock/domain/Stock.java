package com.pixelwms.stock.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
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
 * 로케이션 × 품목 재고.
 *
 * <p>수량은 {@code stock_movements} 이력의 접힌 결과다 — 이 값만 고쳐 쓰면 "왜 줄었는지"에
 * 답할 수 없으므로, 변경은 항상 이동 이력과 함께 기록한다(StockService).
 */
@Getter
@Entity
@Table(name = "stocks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long locationId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Integer quantity;

    public Stock(Long locationId, Long itemId, Integer quantity) {
        this.locationId = locationId;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public void add(int amount) {
        this.quantity += amount;
    }

    /** 재고보다 많이 빼려 하면 거절한다(DB의 non-negative 제약보다 먼저 뜻이 통하는 메시지로). */
    public void subtract(int amount) {
        if (amount > this.quantity) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "재고가 부족합니다. 현재 " + this.quantity + ", 요청 " + amount);
        }
        this.quantity -= amount;
    }
}
