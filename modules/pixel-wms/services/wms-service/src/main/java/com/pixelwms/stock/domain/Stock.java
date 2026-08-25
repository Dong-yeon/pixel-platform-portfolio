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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파렛트 × 품목 재고 (P23).
 *
 * <p>파렛트당 재고 행 하나뿐이다({@code uq_stock_pallet}) — 로케이션은 더 이상 여기서
 * 직접 갖지 않는다, {@link Pallet#getLocationId()}가 유일한 출처다(중복 제거, 설계 근거:
 * docs/p23-pallet-unit-design.md D2). {@code inboundDt}가 FIFO 정렬 축이다(D3) — 같은
 * 품목의 여러 파렛트 중 이 값이 빠른 것부터 출고 대상으로 고른다.
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
    private Long palletId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Integer quantity;

    /** FIFO 축 겸 LOT 번호. 지금은 파렛트 코드를 그대로 재사용한다(1파렛트=1LOT). */
    @Column(nullable = false, length = 50)
    private String lotNo;

    @Column(nullable = false)
    private LocalDateTime inboundDt;

    public Stock(Long palletId, Long itemId, Integer quantity, String lotNo, LocalDateTime inboundDt) {
        this.palletId = palletId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.lotNo = lotNo;
        this.inboundDt = inboundDt;
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
