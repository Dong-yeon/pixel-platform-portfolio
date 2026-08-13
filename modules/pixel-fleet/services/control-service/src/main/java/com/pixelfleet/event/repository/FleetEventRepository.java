package com.pixelfleet.event.repository;

import com.pixelfleet.event.domain.FleetEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FleetEventRepository extends JpaRepository<FleetEvent, Long> {

    List<FleetEvent> findTop100ByOrderByIdDesc();

    List<FleetEvent> findByTaskIdOrderByIdDesc(Long taskId);

    /**
     * 보존 정책 — 벌크 DELETE. factory와 같은 이유로 파생 쿼리 대신 직접 SQL DELETE를 쓴다
     * (자세한 근거는 {@code FactoryEventRepository.deleteByCreatedAtBefore} Javadoc).
     */
    @Modifying
    @Query("delete from FleetEvent e where e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(LocalDateTime cutoff);
}
