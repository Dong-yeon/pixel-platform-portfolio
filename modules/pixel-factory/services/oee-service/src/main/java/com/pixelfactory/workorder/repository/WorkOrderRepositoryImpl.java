package com.pixelfactory.workorder.repository;

import static com.pixelfactory.workorder.domain.QWorkOrder.workOrder;

import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class WorkOrderRepositoryImpl implements WorkOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public WorkOrderRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<WorkOrder> search(WorkOrderStatus status, Long assignedUserId, String lotNo) {
        return queryFactory
                .selectFrom(workOrder)
                .where(
                        statusEq(status),
                        assignedUserIdEq(assignedUserId),
                        lotNoContains(lotNo)
                )
                .orderBy(workOrder.createdAt.desc())
                .fetch();
    }

    private BooleanExpression statusEq(WorkOrderStatus status) {
        return status == null ? null : workOrder.status.eq(status);
    }

    private BooleanExpression assignedUserIdEq(Long assignedUserId) {
        return assignedUserId == null ? null : workOrder.assignedUserId.eq(assignedUserId);
    }

    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? workOrder.lotNo.containsIgnoreCase(lotNo) : null;
    }
}
