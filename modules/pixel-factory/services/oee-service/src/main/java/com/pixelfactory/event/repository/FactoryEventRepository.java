package com.pixelfactory.event.repository;

import com.pixelfactory.event.domain.FactoryEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryEventRepository extends JpaRepository<FactoryEvent, Long> {

    List<FactoryEvent> findByOrderByCreatedAtDesc(Pageable pageable);

    List<FactoryEvent> findByWorkOrderIdOrderByCreatedAtDesc(Long workOrderId);
}
