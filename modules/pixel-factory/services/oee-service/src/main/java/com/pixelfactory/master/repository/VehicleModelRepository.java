package com.pixelfactory.master.repository;

import com.pixelfactory.master.domain.VehicleModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleModelRepository extends JpaRepository<VehicleModel, Long> {

    Optional<VehicleModel> findByModelCode(String modelCode);

    List<VehicleModel> findAllByOrderByModelCodeAsc();
}
