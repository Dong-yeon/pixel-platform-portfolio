package com.pixelfactory.workorder.repository;

import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import java.util.List;

public interface WorkOrderRepositoryCustom {

    List<WorkOrder> search(WorkOrderStatus status, Long assignedUserId, String lotNo);
}
