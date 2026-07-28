package com.pixelfactory.workorder.domain;

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

@Getter
@Entity
@Table(name = "work_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String workOrderNo;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long processId;

    @Column(nullable = false)
    private Long equipmentId;

    @Column(nullable = false)
    private Long assignedUserId;

    @Column(nullable = false, length = 50)
    private String lotNo;

    @Column(nullable = false)
    private Integer plannedQty;

    @Column(nullable = false)
    private Integer producedQty;

    @Column(nullable = false)
    private Integer defectQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkOrderStatus status;

    @Column(nullable = false)
    private LocalDateTime plannedStartAt;

    @Column(nullable = false)
    private LocalDateTime plannedEndAt;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String holdReason;

    public WorkOrder(
            String workOrderNo,
            Long itemId,
            Long processId,
            Long equipmentId,
            Long assignedUserId,
            String lotNo,
            Integer plannedQty,
            LocalDateTime plannedStartAt,
            LocalDateTime plannedEndAt
    ) {
        this.workOrderNo = workOrderNo;
        this.itemId = itemId;
        this.processId = processId;
        this.equipmentId = equipmentId;
        this.assignedUserId = assignedUserId;
        this.lotNo = lotNo;
        this.plannedQty = plannedQty;
        this.producedQty = 0;
        this.defectQty = 0;
        this.status = WorkOrderStatus.ASSIGNED;
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;
    }

    public void start(LocalDateTime startedAt) {
        this.status = WorkOrderStatus.IN_PROGRESS;
        this.startedAt = startedAt;
        this.holdReason = null;
    }

    public void completeProduction(int producedQty, int defectQty) {
        this.status = WorkOrderStatus.INSPECTION_WAITING;
        this.producedQty = producedQty;
        this.defectQty = defectQty;
    }

    /**
     * 설비가 사이클 1회를 마쳤을 때 실적을 1 올린다(설비 텔레메트리 경로).
     * {@code producedQty}는 불량을 포함한 총 생산량이며, 양품은 produced - defect다.
     *
     * @return 반영했으면 true. 진행 중이 아니거나 계획 수량을 이미 채웠으면 false
     *         (계획 초과 생산은 세지 않는다. 마감은 작업자가 한다).
     */
    public boolean recordCycle(boolean defect) {
        if (status != WorkOrderStatus.IN_PROGRESS || producedQty >= plannedQty) {
            return false;
        }
        this.producedQty += 1;
        if (defect) {
            this.defectQty += 1;
        }
        return true;
    }

    public void hold(String holdReason) {
        this.status = WorkOrderStatus.ON_HOLD;
        this.holdReason = holdReason;
    }

    public void close(LocalDateTime completedAt) {
        this.status = WorkOrderStatus.COMPLETED;
        this.completedAt = completedAt;
    }
}
