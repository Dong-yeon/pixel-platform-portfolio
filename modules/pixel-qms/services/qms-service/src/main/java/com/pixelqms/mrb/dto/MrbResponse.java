package com.pixelqms.mrb.dto;

import com.pixelqms.mrb.domain.MrbDecision;
import com.pixelqms.mrb.domain.MrbReview;
import com.pixelqms.mrb.domain.MrbStatus;
import java.time.LocalDateTime;

public record MrbResponse(
        Long id,
        String mrbNo,
        Long nonconformanceId,
        String equipmentCode,
        String workOrderNo,
        String lotNo,
        MrbStatus status,
        MrbDecision decision,
        String decisionNote,
        /** factory에 홀드가 실제로 걸려 있는지 — factory가 꺼져 있으면 false로 남는다. */
        Boolean holdApplied,
        LocalDateTime decidedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt
) {

    public static MrbResponse from(MrbReview m) {
        return new MrbResponse(
                m.getId(), m.getMrbNo(), m.getNonconformanceId(), m.getEquipmentCode(),
                m.getWorkOrderNo(), m.getLotNo(), m.getStatus(), m.getDecision(), m.getDecisionNote(),
                m.getHoldApplied(), m.getDecidedAt(), m.getClosedAt(), m.getCreatedAt());
    }
}
