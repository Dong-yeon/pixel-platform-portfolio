package com.pixelfleet.event.repository;

import com.pixelfleet.event.domain.FleetEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FleetEventRepository extends JpaRepository<FleetEvent, Long> {

    List<FleetEvent> findTop100ByOrderByIdDesc();

    List<FleetEvent> findByTaskIdOrderByIdDesc(Long taskId);
}
