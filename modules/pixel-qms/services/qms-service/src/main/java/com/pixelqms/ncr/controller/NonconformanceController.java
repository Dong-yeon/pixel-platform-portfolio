package com.pixelqms.ncr.controller;

import com.pixelqms.ncr.domain.Nonconformance;
import com.pixelqms.ncr.repository.NonconformanceRepository;
import com.pixelplatform.core.common.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nonconformances")
public class NonconformanceController {

    private final NonconformanceRepository repository;

    public NonconformanceController(NonconformanceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<NcrResponse>> getAll() {
        return ApiResponse.ok(repository.findByOrderByIdDesc().stream().map(NcrResponse::from).toList());
    }

    public record NcrResponse(
            Long id, String ncrNo, Long inspectionId, Long defectTypeId,
            String equipmentCode, String workOrderNo, String lotNo,
            Integer defectQty, String description
    ) {
        static NcrResponse from(Nonconformance n) {
            return new NcrResponse(n.getId(), n.getNcrNo(), n.getInspectionId(), n.getDefectTypeId(),
                    n.getEquipmentCode(), n.getWorkOrderNo(), n.getLotNo(), n.getDefectQty(), n.getDescription());
        }
    }
}
