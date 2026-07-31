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

/** 입고 지시 — 창고로 자재가 들어온다. */
@Getter
@Entity
@Table(name = "inbound_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNo;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long locationId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    private LocalDateTime completedAt;

    public InboundOrder(String orderNo, Long itemId, Long locationId, Integer quantity) {
        this.orderNo = orderNo;
        this.itemId = itemId;
        this.locationId = locationId;
        this.quantity = quantity;
        this.status = OrderStatus.CREATED;
    }

    public void complete(LocalDateTime completedAt) {
        this.status = OrderStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
