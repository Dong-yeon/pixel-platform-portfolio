package com.pixelfactory.workorder.repository;

import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, WorkOrderRepositoryCustom {

    boolean existsByWorkOrderNo(String workOrderNo);

    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);

    /** 해당 설비에서 지금 돌고 있는 작업지시. 설비 사이클 실적을 붙일 대상을 찾는 데 쓴다. */
    Optional<WorkOrder> findFirstByEquipmentIdAndStatusOrderByIdAsc(Long equipmentId, WorkOrderStatus status);
}
