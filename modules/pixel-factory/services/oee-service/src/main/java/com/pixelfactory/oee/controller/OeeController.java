package com.pixelfactory.oee.controller;

import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import com.pixelfactory.oee.dto.LineOeeResponse;
import com.pixelfactory.oee.service.OeeService;
import com.pixelplatform.core.common.response.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OEE 조회. 저장된 값이 아니라 <b>이벤트에서 매번 계산</b>한 결과다.
 *
 * <p>{@code from}/{@code to}는 발생시각({@code occurred_at}) 기준이며 로컬 시각으로 받는다.
 */
@RestController
@RequestMapping("/api/oee")
public class OeeController {

    private final OeeService oeeService;

    public OeeController(OeeService oeeService) {
        this.oeeService = oeeService;
    }

    @GetMapping("/equipments/{equipmentCode}")
    public ApiResponse<EquipmentOeeResponse> equipment(
            @PathVariable String equipmentCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.ok(oeeService.ofEquipment(equipmentCode, from, to));
    }

    @GetMapping("/lines/{lineCode}")
    public ApiResponse<LineOeeResponse> line(
            @PathVariable String lineCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ApiResponse.ok(oeeService.ofLine(lineCode, from, to));
    }

    /** 현재 교대 기준 전 설비 요약 — 대시보드용. */
    @GetMapping("/current")
    public ApiResponse<List<EquipmentOeeResponse>> current() {
        return ApiResponse.ok(oeeService.current(LocalDateTime.now()));
    }
}
