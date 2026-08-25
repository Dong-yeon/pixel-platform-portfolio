package com.pixelwms.stock.domain;

/** 파렛트 상태 (P23). */
public enum PalletStatus {
    /** 재고를 실은 채 로케이션에 있다. */
    LOADED,
    /** 운송 작업이 걸려 있다 — fleet에 넘어간 뒤부터 완료 통지 전까지. */
    IN_TRANSIT,
    /** 출고 완료로 재고를 다 내려 소진됐다. 회수·재사용은 범위 밖(design doc 8절). */
    RETIRED
}
