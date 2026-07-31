package com.pixelqms.inspection.domain;

public enum InspectionType {
    /** 수입검사 — 들어온 자재. */
    INCOMING,
    /** 공정검사 — 가공 중. 불량 임계 초과로 자동 생성되는 것이 이 유형이다. */
    IN_PROCESS,
    /** 최종검사 — 출하 전. */
    FINAL
}
