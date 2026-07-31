package com.pixelqms.inspection.repository;

import com.pixelqms.inspection.domain.Inspection;
import com.pixelqms.inspection.domain.InspectionResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    boolean existsByInspectionNo(String inspectionNo);

    List<Inspection> findByResultOrderByIdDesc(InspectionResult result);

    List<Inspection> findByOrderByIdDesc();
}
