package com.pixelwms.stock.repository;

import com.pixelwms.stock.domain.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByLocationCode(String locationCode);

    List<Location> findAllByOrderByLocationCodeAsc();
}
