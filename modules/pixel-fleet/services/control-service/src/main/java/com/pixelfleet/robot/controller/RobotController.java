package com.pixelfleet.robot.controller;

import com.pixelplatform.core.common.response.ApiResponse;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.robot.service.RobotService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    public ApiResponse<List<RobotResponse>> list() {
        return ApiResponse.ok(robotService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<RobotResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(robotService.getById(id));
    }

    @PostMapping("/{id}/off-duty")
    public ApiResponse<Void> offDuty(@PathVariable Long id) {
        robotService.setOffDuty(id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/on-duty")
    public ApiResponse<Void> onDuty(@PathVariable Long id) {
        robotService.setOffDuty(id, false);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        robotService.setDisabled(id, true);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        robotService.setDisabled(id, false);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/clear-alarm")
    public ApiResponse<Void> clearAlarm(@PathVariable Long id) {
        robotService.clearAlarm(id);
        return ApiResponse.ok();
    }
}
