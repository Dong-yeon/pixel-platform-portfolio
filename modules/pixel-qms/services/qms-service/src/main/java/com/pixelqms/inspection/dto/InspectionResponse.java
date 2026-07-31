package com.pixelqms.inspection.dto;

import com.pixelqms.inspection.domain.Inspection;
import com.pixelqms.inspection.domain.InspectionResult;
import com.pixelqms.inspection.domain.InspectionType;
import java.time.LocalDateTime;

public record InspectionResponse(
        Long id,
        String inspectionNo,
        InspectionType inspectionType,
        String equipmentCode,
        String workOrderNo,
        String lotNo,
        Long inspectorId,
        InspectionResult result,
        Integer inspectedQty,
        Integer defectQty,
        String note,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {

    public static InspectionResponse from(Inspection i) {
        return new InspectionResponse(
                i.getId(), i.getInspectionNo(), i.getInspectionType(), i.getEquipmentCode(),
                i.getWorkOrderNo(), i.getLotNo(), i.getInspectorId(), i.getResult(),
                i.getInspectedQty(), i.getDefectQty(), i.getNote(), i.getCompletedAt(), i.getCreatedAt());
    }
}
