package com.pixelwms.order.repository;

import com.pixelwms.order.domain.InboundOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    boolean existsByOrderNo(String orderNo);

    List<InboundOrder> findByOrderByIdDesc();
}
