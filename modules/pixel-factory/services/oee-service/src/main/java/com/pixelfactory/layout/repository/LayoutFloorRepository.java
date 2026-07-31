package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutFloor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutFloorRepository extends JpaRepository<LayoutFloor, Long> {

    List<LayoutFloor> findAllByOrderByBuildingIdAscFloorNoAsc();
}
