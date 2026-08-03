package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutChargingZone;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutChargingZoneRepository extends JpaRepository<LayoutChargingZone, Long> {

    List<LayoutChargingZone> findAllByOrderByZoneCodeAsc();
}
