package com.pixelfactory.equipment.dto;

import com.pixelfactory.equipment.domain.ProductionLine;

public record ProductionLineResponse(
        Long id,
        String lineCode,
        String name
) {
    public static ProductionLineResponse from(ProductionLine line) {
        return new ProductionLineResponse(line.getId(), line.getLineCode(), line.getName());
    }
}
