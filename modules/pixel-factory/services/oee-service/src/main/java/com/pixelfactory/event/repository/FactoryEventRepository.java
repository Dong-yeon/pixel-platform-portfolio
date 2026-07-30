package com.pixelfactory.event.repository;

import com.pixelfactory.event.domain.FactoryEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryEventRepository extends JpaRepository<FactoryEvent, Long> {

    // 정렬 기준은 적재 시각(createdAt)이 아니라 발생 시각(occurredAt)이다.
    // 밀렸다 한꺼번에 들어온 메시지가 처리 순서대로 붙으면 실제 일어난 순서와 뒤바뀐다.
    List<FactoryEvent> findByOrderByOccurredAtDesc(Pageable pageable);

    List<FactoryEvent> findByWorkOrderIdOrderByOccurredAtDesc(Long workOrderId);
}
