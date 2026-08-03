package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutElevator;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutElevatorRepository extends JpaRepository<LayoutElevator, Long> {

    List<LayoutElevator> findAllByOrderByElevatorCodeAsc();
}
