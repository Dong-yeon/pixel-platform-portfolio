package com.pixelqms.inspection.repository;

import com.pixelqms.inspection.domain.DefectType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectTypeRepository extends JpaRepository<DefectType, Long> {

    Optional<DefectType> findByDefectCode(String defectCode);

    List<DefectType> findAllByOrderByDefectCodeAsc();
}
