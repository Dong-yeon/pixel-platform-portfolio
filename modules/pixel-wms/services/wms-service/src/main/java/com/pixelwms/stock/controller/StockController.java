package com.pixelwms.stock.controller;

import com.pixelwms.stock.dto.StockResponse;
import com.pixelwms.stock.service.StockService;
import com.pixelplatform.core.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public ApiResponse<List<StockResponse>> getStocks() {
        return ApiResponse.ok(stockService.getStocks());
    }
}
