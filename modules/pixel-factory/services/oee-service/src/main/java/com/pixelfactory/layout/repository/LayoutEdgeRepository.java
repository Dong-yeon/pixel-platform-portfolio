package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutEdge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutEdgeRepository extends JpaRepository<LayoutEdge, Long> {

    List<LayoutEdge> findAllByOrderByIdAsc();
}
