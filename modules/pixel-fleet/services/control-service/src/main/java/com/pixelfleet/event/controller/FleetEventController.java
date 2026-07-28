package com.pixelfleet.event.controller;

import com.pixelplatform.core.common.response.ApiResponse;
import com.pixelfleet.event.dto.FleetEventResponse;
import com.pixelfleet.event.service.FleetEventService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class FleetEventController {

    private final FleetEventService fleetEventService;

    public FleetEventController(FleetEventService fleetEventService) {
        this.fleetEventService = fleetEventService;
    }

    @GetMapping
    public ApiResponse<List<FleetEventResponse>> list(@RequestParam(required = false) Long taskId) {
        var events = (taskId == null ? fleetEventService.recent() : fleetEventService.forTask(taskId))
                .stream()
                .map(FleetEventResponse::from)
                .toList();
        return ApiResponse.ok(events);
    }
}
