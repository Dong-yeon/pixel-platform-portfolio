package com.pixelfactory.workorder.repository;

import com.pixelfactory.workorder.domain.WorkOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, WorkOrderRepositoryCustom {

    boolean existsByWorkOrderNo(String workOrderNo);

    Optional<WorkOrder> findByWorkOrderNo(String workOrderNo);
}
