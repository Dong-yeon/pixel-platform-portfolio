package com.pixelqms.mrb.dto;

import com.pixelqms.mrb.domain.MrbDecision;
import jakarta.validation.constraints.NotNull;

public record MrbDecisionRequest(@NotNull MrbDecision decision, String decisionNote) {
}
