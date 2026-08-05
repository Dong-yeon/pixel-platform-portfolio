package com.pixelfleet.order.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문의 한 스텝 — "이 지점으로 가서, 싣거나(forLoad) 내리거나(forUnload) 그냥 들른다".
 *
 * <p>forLoad/forUnload는 상호배타다(M4 규격). 로봇의 적재 상태(laden)는 이 플래그의
 * 누적 결과이지 leg 순서에서 추론하지 않는다 — 추론은 스텝이 2개일 때만 맞는 우연이었다.
 */
@Getter
@Entity
@Table(name = "fleet_order_steps")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private FleetOrder order;

    @Column(nullable = false)
    private int stepIndex;

    @Column(nullable = false, length = 30)
    private String locationNode;

    @Column(nullable = false)
    private boolean forLoad;

    @Column(nullable = false)
    private boolean forUnload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    OrderStep(FleetOrder order, int stepIndex, String locationNode, boolean forLoad, boolean forUnload) {
        this.order = order;
        this.stepIndex = stepIndex;
        this.locationNode = locationNode;
        this.forLoad = forLoad;
        this.forUnload = forUnload;
        this.status = StepStatus.EXECUTABLE;
    }

    void markExecuting() {
        this.status = StepStatus.EXECUTING;
        this.startedAt = LocalDateTime.now();
    }

    void markDone() {
        this.status = StepStatus.DONE;
        this.finishedAt = LocalDateTime.now();
    }

    void markCancelled() {
        this.status = StepStatus.CANCELLED;
    }

    /** 재시도용 — 처음부터 다시 달릴 수 있게 되돌린다. */
    void resetToExecutable() {
        this.status = StepStatus.EXECUTABLE;
        this.startedAt = null;
        this.finishedAt = null;
    }
}
