package com.pixelfactory.master.repository;

import com.pixelfactory.master.domain.Part;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartRepository extends JpaRepository<Part, Long> {

    Optional<Part> findByPartCode(String partCode);

    List<Part> findAllByOrderByPartCodeAsc();

    /** 차종별 품번. 공용 부품(modelId=null)은 여기 안 잡힌다. */
    List<Part> findByModelIdOrderByPartCodeAsc(Long modelId);
}
