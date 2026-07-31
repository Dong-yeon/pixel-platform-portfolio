package com.pixelfactory.master.domain;

public enum PartType {
    /** 완제품 어셈블리 — 차종에 납품되는 단위. */
    PRODUCT,
    /** 가공 반제품 — 우리 라인에서 만들어 조립에 넣는다. */
    SEMI,
    /** 원자재 — 사다 쓴다. BOM의 잎이다. */
    MATERIAL
}
