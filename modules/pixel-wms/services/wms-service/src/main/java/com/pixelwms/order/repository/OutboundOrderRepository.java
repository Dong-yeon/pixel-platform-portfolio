package com.pixelwms.order.repository;

import com.pixelwms.order.domain.OutboundOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {

    boolean existsByOrderNo(String orderNo);

    /** 운송 완료 통지를 받으면 이 코드로 지시를 되찾아 재고를 차감한다. */
    Optional<OutboundOrder> findByTaskCode(String taskCode);

    List<OutboundOrder> findByOrderByIdDesc();
}
