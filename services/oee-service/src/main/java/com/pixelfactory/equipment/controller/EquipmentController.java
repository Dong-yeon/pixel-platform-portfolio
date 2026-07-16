package com.pixelfactory.equipment.controller;

import com.pixelfactory.common.response.ApiResponse;
import com.pixelfactory.equipment.dto.EquipmentResponse;
import com.pixelfactory.equipment.dto.ProductionLineResponse;
import com.pixelfactory.equipment.service.EquipmentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping("/lines")
    public ApiResponse<List<ProductionLineResponse>> getLines() {
        return ApiResponse.ok(equipmentService.getLines());
    }

    @GetMapping("/equipments")
    public ApiResponse<List<EquipmentResponse>> getEquipments() {
        return ApiResponse.ok(equipmentService.getEquipments());
    }
}
