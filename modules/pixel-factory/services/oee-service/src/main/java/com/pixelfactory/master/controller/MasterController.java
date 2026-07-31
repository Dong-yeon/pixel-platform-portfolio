package com.pixelfactory.master.controller;

import com.pixelfactory.master.dto.BomNodeResponse;
import com.pixelfactory.master.dto.PartResponse;
import com.pixelfactory.master.dto.VehicleModelResponse;
import com.pixelfactory.master.service.BomService;
import com.pixelfactory.master.service.PartService;
import com.pixelplatform.core.common.response.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 생산 기준정보 — 차종 · 품번 · BOM.
 *
 * <p>조회는 인증된 누구나. <b>개정은 되돌리기 어려운 마스터 변경</b>이라 ADMIN만 한다.
 */
@RestController
@RequestMapping("/api")
public class MasterController {

    private final PartService partService;
    private final BomService bomService;

    public MasterController(PartService partService, BomService bomService) {
        this.partService = partService;
        this.bomService = bomService;
    }

    @GetMapping("/vehicle-models")
    public ApiResponse<List<VehicleModelResponse>> getModels() {
        return ApiResponse.ok(partService.getModels());
    }

    /** @param modelCode 지정하면 그 차종 전용 품번만(공용 부품 제외). */
    @GetMapping("/parts")
    public ApiResponse<List<PartResponse>> getParts(@RequestParam(required = false) String modelCode) {
        return ApiResponse.ok(partService.getParts(modelCode));
    }

    /** 최신 BOM 트리. 중첩 구조로 내려 화면이 부모를 추측하지 않게 한다. */
    @GetMapping("/boms/{partCode}")
    public ApiResponse<BomNodeResponse> getBomTree(@PathVariable String partCode) {
        return ApiResponse.ok(bomService.getTree(partCode));
    }

    @GetMapping("/boms/{partCode}/revisions")
    public ApiResponse<List<BomService.RevisionSummary>> getRevisions(@PathVariable String partCode) {
        return ApiResponse.ok(bomService.getRevisions(partCode));
    }

    /**
     * BOM 개정 — 최신 구성을 다음 rev로 복사한다.
     *
     * <p>대상 rev를 <b>요청에서 받지 않는다.</b> 화면이 보낸 rev를 믿고 +1 하면 최신이 아닌
     * rev에서 개정할 때 중복이 적재된다(실 운영 MES 사고). 서버가 DB의 MAX+1로 정한다.
     */
    @PostMapping("/boms/{partCode}/revisions")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> revise(@PathVariable String partCode) {
        int newRev = bomService.copyRevision(partCode);
        return ApiResponse.ok(Map.of("partCode", partCode, "revNo", newRev));
    }
}
