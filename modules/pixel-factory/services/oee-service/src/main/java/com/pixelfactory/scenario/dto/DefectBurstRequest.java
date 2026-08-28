package com.pixelfactory.scenario.dto;

import jakarta.validation.constraints.Min;

/** {@code count}를 생략하면 컨트롤러가 서버 불량임계 기본값을 채운다. */
public record DefectBurstRequest(@Min(1) Integer count) {
}
