package com.pixelwms.item.service;

import com.pixelwms.item.domain.Item;
import com.pixelwms.item.domain.ItemStandardCycleTime;
import com.pixelwms.item.dto.ItemResponse;
import com.pixelwms.item.repository.ItemRepository;
import com.pixelwms.item.repository.ItemStandardCycleTimeRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemStandardCycleTimeRepository cycleTimeRepository;

    public ItemService(ItemRepository itemRepository, ItemStandardCycleTimeRepository cycleTimeRepository) {
        this.itemRepository = itemRepository;
        this.cycleTimeRepository = cycleTimeRepository;
    }

    public List<ItemResponse> getItems() {
        Map<Long, List<ItemStandardCycleTime>> cycleTimesByItem = cycleTimeRepository.findAll().stream()
                .collect(Collectors.groupingBy(ItemStandardCycleTime::getItemId));

        return itemRepository.findAllByOrderByItemCodeAsc().stream()
                .map(item -> toResponse(item, cycleTimesByItem.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    public ItemResponse getItem(String itemCode) {
        Item item = requireItem(itemCode);
        return toResponse(item, cycleTimeRepository.findByItemId(item.getId()));
    }

    public Item requireItem(String itemCode) {
        return itemRepository.findByItemCode(itemCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "품목을 찾을 수 없습니다: " + itemCode));
    }

    private ItemResponse toResponse(Item item, List<ItemStandardCycleTime> cycleTimes) {
        return new ItemResponse(
                item.getId(),
                item.getItemCode(),
                item.getName(),
                item.getUnit(),
                cycleTimes.stream()
                        .map(c -> new ItemResponse.StandardCycleTime(c.getProcessCode(), c.getStandardCycleTimeMs()))
                        .toList()
        );
    }
}
