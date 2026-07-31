package com.pixelwms.item.controller;

import com.pixelwms.item.dto.ItemResponse;
import com.pixelwms.item.service.ItemService;
import com.pixelplatform.core.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 품목 마스터 + 표준CT.
 *
 * <p>조회는 인증 없이 열려 있다(SecurityConfig) — factory의 OEE 계산기가 표준CT를 읽어야 하는데
 * 서비스 간 인증(M2M)이 아직 없기 때문이다. factory가 layout을 열어 둔 것과 같은 이유·같은 백로그.
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public ApiResponse<List<ItemResponse>> getItems() {
        return ApiResponse.ok(itemService.getItems());
    }

    @GetMapping("/{itemCode}")
    public ApiResponse<ItemResponse> getItem(@PathVariable String itemCode) {
        return ApiResponse.ok(itemService.getItem(itemCode));
    }
}
