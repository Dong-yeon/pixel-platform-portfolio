package com.pixelqms.inspection.controller;

import com.pixelqms.auth.CurrentUserProvider;
import com.pixelqms.inspection.dto.InspectionCompleteRequest;
import com.pixelqms.inspection.dto.InspectionResponse;
import com.pixelqms.inspection.repository.DefectTypeRepository;
import com.pixelqms.inspection.service.InspectionService;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InspectionController {

    private final InspectionService inspectionService;
    private final DefectTypeRepository defectTypeRepository;
    private final CurrentUserProvider currentUserProvider;

    public InspectionController(
            InspectionService inspectionService,
            DefectTypeRepository defectTypeRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.inspectionService = inspectionService;
        this.defectTypeRepository = defectTypeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/inspections")
    public ApiResponse<List<InspectionResponse>> getInspections() {
        return ApiResponse.ok(inspectionService.getInspections());
    }

    /** 검사 대기 목록 — INSPECTOR 진입 화면이 쓴다. */
    @GetMapping("/inspections/pending")
    public ApiResponse<List<InspectionResponse>> getPending() {
        return ApiResponse.ok(inspectionService.getPending());
    }

    /** 판정은 검사원(또는 관리자)만. */
    @PostMapping("/inspections/{id}/complete")
    @PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
    public ApiResponse<InspectionResponse> complete(
            @PathVariable Long id,
            @Valid @RequestBody InspectionCompleteRequest request
    ) {
        return ApiResponse.ok(inspectionService.complete(id, request, currentUserProvider.currentUserIdOrNull()));
    }

    @GetMapping("/defect-types")
    public ApiResponse<List<Map<String, Object>>> getDefectTypes() {
        return ApiResponse.ok(defectTypeRepository.findAllByOrderByDefectCodeAsc().stream()
                .map(d -> Map.<String, Object>of("id", d.getId(), "defectCode", d.getDefectCode(), "name", d.getName()))
                .toList());
    }
}
