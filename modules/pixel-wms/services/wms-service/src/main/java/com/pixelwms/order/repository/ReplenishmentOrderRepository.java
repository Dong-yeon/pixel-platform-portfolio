package com.pixelwms.order.repository;

import com.pixelwms.order.domain.OrderStatus;
import com.pixelwms.order.domain.ReplenishmentOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReplenishmentOrderRepository extends JpaRepository<ReplenishmentOrder, Long> {

    /** 완료 통지를 받으면 이 코드로 지시를 되찾는다. */
    Optional<ReplenishmentOrder> findByTaskCode(String taskCode);

    List<ReplenishmentOrder> findByOrderByIdDesc();

    /** 중복 생성 방지(D7) — 같은 (품목, 도착 로케이션)에 이미 진행 중인 보충이 있는가. */
    boolean existsByItemIdAndToLocationIdAndStatusIn(Long itemId, Long toLocationId, List<OrderStatus> statuses);

    /** 채번용 시퀀스(V8). {@code ReplenishmentCodeGenerator}가 포맷을 입힌다. */
    @Query(value = "select nextval('replenishment_order_seq')", nativeQuery = true)
    long nextOrderSeq();
}
