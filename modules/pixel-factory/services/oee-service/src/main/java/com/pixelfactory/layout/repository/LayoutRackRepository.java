package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutRack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutRackRepository extends JpaRepository<LayoutRack, Long> {

    List<LayoutRack> findAllByOrderByRackCodeAsc();
}
