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
 * 출고 지시 — 창고에서 목적지로 물건이 나간다. <b>이것이 로봇을 움직이는 이유다.</b>
 *
 * <p>{@code taskCode}로 fleet 운송 작업과 느슨히 연결한다(FK 아님, 다른 모듈 DB).
 * 재고 차감은 지시 생성이 아니라 <b>운송 완료 통지</b>를 받은 뒤에 한다.
 */
@Getter
@Entity
@Table(name = "outbound_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboundOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNo;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long fromLocationId;

    /** 도착지 — factory 평면도 노드 코드(예: SHIPPING). */
    @Column(nullable = false, length = 30)
    private String toNodeCode;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    /** fleet 운송 작업 코드. 작업 생성에 성공해야 채워진다. */
    @Column(unique = true, length = 50)
    private String taskCode;

    private LocalDateTime completedAt;

    public OutboundOrder(String orderNo, Long itemId, Long fromLocationId, String toNodeCode, Integer quantity) {
        this.orderNo = orderNo;
        this.itemId = itemId;
        this.fromLocationId = fromLocationId;
        this.toNodeCode = toNodeCode;
        this.quantity = quantity;
        this.status = OrderStatus.CREATED;
    }

    /** fleet에 운송 작업이 만들어졌다 — 아직 물건은 그대로다. */
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
