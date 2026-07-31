package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutBuilding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutBuildingRepository extends JpaRepository<LayoutBuilding, Long> {

    List<LayoutBuilding> findAllByOrderByDisplayOrderAsc();
}
