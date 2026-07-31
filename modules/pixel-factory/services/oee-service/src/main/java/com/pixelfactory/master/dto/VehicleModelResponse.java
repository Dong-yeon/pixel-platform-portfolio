package com.pixelfactory.master.dto;

import com.pixelfactory.master.domain.VehicleModel;

public record VehicleModelResponse(Long id, String modelCode, String name, boolean inProduction) {

    public static VehicleModelResponse from(VehicleModel model) {
        return new VehicleModelResponse(model.getId(), model.getModelCode(), model.getName(),
                Boolean.TRUE.equals(model.getInProduction()));
    }
}
