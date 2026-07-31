package com.pixelfactory.quality.controller;

import com.pixelfactory.quality.dto.InspectionEventRequest;
import com.pixelfactory.quality.dto.QualityHoldRequest;
import com.pixelfactory.quality.dto.QualityReleaseRequest;
import com.pixelfactory.quality.service.QualityHoldService;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 품질 홀드 창구 — 외부 품질 시스템(QMS)이 현장을 멈추고 다시 돌린다.
 *
 * <p><b>factory는 호출자를 모른다.</b> "이 설비를 품질 홀드하라"는 계약만 노출한다.
 * 인증은 필요하다(플랫폼 토큰) — 생산을 멈추는 행위라 공개할 수 없다.
 */
@RestController
@RequestMapping("/api/quality")
public class QualityController {

    private final QualityHoldService qualityHoldService;

    public QualityController(QualityHoldService qualityHoldService) {
        this.qualityHoldService = qualityHoldService;
    }

    @PostMapping("/hold")
    public ApiResponse<Map<String, String>> hold(@Valid @RequestBody QualityHoldRequest request) {
        qualityHoldService.hold(request.equipmentCode(), request.workOrderNo(),
                request.reason(), request.referenceNo());
        return ApiResponse.ok(Map.of("status", "HELD", "equipmentCode", request.equipmentCode()));
    }

    @PostMapping("/release")
    public ApiResponse<Map<String, String>> release(@Valid @RequestBody QualityReleaseRequest request) {
        qualityHoldService.release(request.equipmentCode(), request.workOrderNo(),
                request.decision(), request.referenceNo());
        return ApiResponse.ok(Map.of("status", "RELEASED", "equipmentCode", request.equipmentCode()));
    }

    /** 검사 시작 통지 — 타임라인에 INSPECTION_STARTED 로 남는다. */
    @PostMapping("/inspection-started")
    public ApiResponse<Map<String, String>> inspectionStarted(@Valid @RequestBody InspectionEventRequest request) {
        qualityHoldService.recordInspectionStarted(request.equipmentCode(), request.workOrderNo(),
                request.lotNo(), request.inspectionNo());
        return ApiResponse.ok(Map.of("status", "RECORDED"));
    }

    /** 검사 판정 통지 — INSPECTION_PASSED / INSPECTION_FAILED. */
    @PostMapping("/inspection-result")
    public ApiResponse<Map<String, String>> inspectionResult(@Valid @RequestBody InspectionEventRequest request) {
        qualityHoldService.recordInspectionResult(request.equipmentCode(), request.workOrderNo(),
                request.lotNo(), request.inspectionNo(), Boolean.TRUE.equals(request.passed()));
        return ApiResponse.ok(Map.of("status", "RECORDED"));
    }
}
