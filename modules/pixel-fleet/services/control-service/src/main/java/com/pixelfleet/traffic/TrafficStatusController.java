package com.pixelfleet.traffic;

import com.pixelplatform.core.common.response.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구간 점유 현황 조회 — 교통 통제가 실제로 어떻게 걸려 있는지 확인용. */
@RestController
@RequestMapping("/api/traffic")
public class TrafficStatusController {

    private final TrafficController trafficController;

    public TrafficStatusController(TrafficController trafficController) {
        this.trafficController = trafficController;
    }

    /** 구간 ID → 점유 중인 로봇 ID */
    @GetMapping("/reservations")
    public ApiResponse<Map<String, Long>> reservations() {
        return ApiResponse.ok(trafficController.snapshot());
    }
}
