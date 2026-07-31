package com.pixelfactory.master.dto;

import com.pixelfactory.master.domain.Part;
import com.pixelfactory.master.domain.PartType;

public record PartResponse(
        Long id,
        String partCode,
        String name,
        PartType partType,
        String unit,
        /** 차종 코드. 공용 부품이면 null. */
        String modelCode
) {

    public static PartResponse from(Part part, String modelCode) {
        return new PartResponse(part.getId(), part.getPartCode(), part.getName(),
                part.getPartType(), part.getUnit(), modelCode);
    }
}
