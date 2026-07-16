package com.pixelfactory.event.controller;

import com.pixelfactory.common.response.ApiResponse;
import com.pixelfactory.event.dto.FactoryEventCreateRequest;
import com.pixelfactory.event.dto.FactoryEventResponse;
import com.pixelfactory.event.service.FactoryEventService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class FactoryEventController {

    private final FactoryEventService factoryEventService;

    public FactoryEventController(FactoryEventService factoryEventService) {
        this.factoryEventService = factoryEventService;
    }

    @PostMapping
    public ApiResponse<FactoryEventResponse> create(@Valid @RequestBody FactoryEventCreateRequest request) {
        return ApiResponse.ok(factoryEventService.create(request));
    }

    @GetMapping("/recent")
    public ApiResponse<List<FactoryEventResponse>> getRecent(@RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(factoryEventService.getRecent(limit));
    }

    @GetMapping("/work-orders/{workOrderId}")
    public ApiResponse<List<FactoryEventResponse>> getByWorkOrder(@PathVariable Long workOrderId) {
        return ApiResponse.ok(factoryEventService.getByWorkOrder(workOrderId));
    }
}
