package com.pixelqms.ncr.repository;

import com.pixelqms.ncr.domain.Nonconformance;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonconformanceRepository extends JpaRepository<Nonconformance, Long> {

    boolean existsByNcrNo(String ncrNo);

    List<Nonconformance> findByOrderByIdDesc();
}
