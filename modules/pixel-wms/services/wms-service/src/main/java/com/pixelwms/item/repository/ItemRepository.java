package com.pixelwms.item.repository;

import com.pixelwms.item.domain.Item;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByItemCode(String itemCode);

    List<Item> findAllByOrderByItemCodeAsc();
}
