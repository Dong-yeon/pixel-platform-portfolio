package com.pixelfactory.layout.repository;

import com.pixelfactory.layout.domain.LayoutNode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LayoutNodeRepository extends JpaRepository<LayoutNode, Long> {

    List<LayoutNode> findAllByOrderByNodeCodeAsc();
}
