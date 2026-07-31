package com.pixelwms.item.repository;

import com.pixelwms.item.domain.ItemStandardCycleTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemStandardCycleTimeRepository extends JpaRepository<ItemStandardCycleTime, Long> {

    List<ItemStandardCycleTime> findByItemId(Long itemId);

    Optional<ItemStandardCycleTime> findByItemIdAndProcessCode(Long itemId, String processCode);
}
