package com.pixelqms.mrb.dto;

import jakarta.validation.constraints.NotNull;

/** 부적합을 심의에 올린다. 이 순간 현장이 멈춘다(설비 QUALITY_HOLD). */
public record MrbCreateRequest(@NotNull Long nonconformanceId) {
}
