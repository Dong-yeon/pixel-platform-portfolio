package com.pixelwms.stock.controller;

import com.pixelwms.stock.dto.PalletResponse;
import com.pixelwms.stock.service.StockService;
import com.pixelplatform.core.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 파렛트 조회(P23-3) — "이 로케이션에 파렛트 몇 장" 확인용. */
@RestController
@RequestMapping("/api/pallets")
public class PalletController {

    private final StockService stockService;

    public PalletController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public ApiResponse<List<PalletResponse>> getPallets(
            @RequestParam(required = false) String locationCode) {
        return ApiResponse.ok(stockService.getPallets(locationCode));
    }
}
