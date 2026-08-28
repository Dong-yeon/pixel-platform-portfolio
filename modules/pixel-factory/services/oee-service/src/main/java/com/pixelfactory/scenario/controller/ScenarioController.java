package com.pixelfactory.scenario.controller;

import com.pixelfactory.quality.QualityProperties;
import com.pixelfactory.scenario.dto.DefectBurstRequest;
import com.pixelfactory.scenario.service.ScenarioService;
import com.pixelplatform.core.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 이벤트 주입 — 시연 중 설비 고장/불량을 버튼으로 강제한다(P15-1).
 *
 * <p><b>되돌리기 어려운 현장 개입이라 ADMIN만.</b> {@code MasterController}의 BOM
 * 개정 버튼과 같은 관례 — 조회는 인증된 누구나, 상태를 바꾸는 행위는 관리자만.
 */
@RestController
@RequestMapping("/api/scenario")
public class ScenarioController {

    private final ScenarioService scenarioService;
    private final QualityProperties qualityProperties;

    public ScenarioController(ScenarioService scenarioService, QualityProperties qualityProperties) {
        this.scenarioService = scenarioService;
        this.qualityProperties = qualityProperties;
    }

    @PostMapping("/equipment/{equipmentCode}/breakdown")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> breakdown(@PathVariable String equipmentCode) {
        scenarioService.injectBreakdown(equipmentCode);
        return ApiResponse.ok(null);
    }

    @PostMapping("/equipment/{equipmentCode}/recover")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> recover(@PathVariable String equipmentCode) {
        scenarioService.injectRecover(equipmentCode);
        return ApiResponse.ok(null);
    }

    @PostMapping("/equipment/{equipmentCode}/defect-burst")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> defectBurst(
            @PathVariable String equipmentCode,
            @RequestBody(required = false) DefectBurstRequest request
    ) {
        int count = (request != null && request.count() != null)
                ? request.count()
                : qualityProperties.getDefectThreshold();
        scenarioService.injectDefectBurst(equipmentCode, count);
        return ApiResponse.ok(null);
    }
}
