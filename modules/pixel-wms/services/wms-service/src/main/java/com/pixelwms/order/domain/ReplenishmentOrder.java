package com.pixelwms.order.domain;

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

/**
 * 안전재고 미달 보충 — 파렛트를 로케이션 간 옮기는 <b>내부 이동</b> 지시 (P26).
 *
 * <p>{@link OutboundOrder}와 구조는 비슷하지만 완료 의미가 다르다 — 출고는 파렛트가
 * WMS 밖으로 나가며 은퇴하지만(P23 D5), 보충은 파렛트가 그대로 살아서 다른 로케이션에
 * 다시 나타난다. 그래서 별도 엔티티다(설계 근거: docs/p26-safety-stock-replenishment-design.md
 * D2 — {@link InboundOrder}/{@link OutboundOrder}가 이미 구조가 겹쳐도 의미가 다르면
 * 따로 두는 전례를 그대로 따른다).
 *
 * <p>시스템이 스스로 판단해서 만드는 지시라({@code ReplenishmentService}) 외부 호출자가
 * 없다 — {@code orderNo}도 시스템이 채번한다.
 */
@Getter
@Entity
@Table(name = "replenishment_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplenishmentOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNo;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long fromLocationId;

    @Column(nullable = false)
    private Long toLocationId;

    @Column(nullable = false)
    private Long palletId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** fleet 운송 작업 코드. 작업 생성에 성공해야 채워진다. */
    @Column(unique = true, length = 50)
    private String taskCode;

    private LocalDateTime completedAt;

    public ReplenishmentOrder(String orderNo, Long itemId, Long fromLocationId, Long toLocationId,
                              Long palletId, Integer quantity) {
        this.orderNo = orderNo;
        this.itemId = itemId;
        this.fromLocationId = fromLocationId;
        this.toLocationId = toLocationId;
        this.palletId = palletId;
        this.quantity = quantity;
        this.status = OrderStatus.CREATED;
    }

    /** fleet에 운송 작업이 만들어졌다 — 아직 파렛트는 원래 자리에 있다. */
    public void markInTransit(String taskCode) {
        this.taskCode = taskCode;
        this.status = OrderStatus.IN_TRANSIT;
    }

    public void complete(LocalDateTime completedAt) {
        this.status = OrderStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public boolean isCompleted() {
        return this.status == OrderStatus.COMPLETED;
    }
}
