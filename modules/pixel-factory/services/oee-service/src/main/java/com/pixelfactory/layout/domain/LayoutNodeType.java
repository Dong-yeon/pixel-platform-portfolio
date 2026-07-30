package com.pixelfactory.layout.domain;

/**
 * 노드의 성격. 대시보드가 모양을 구분하고, fleet이 "여기는 충전 자리인가 목적지인가"를 가른다.
 */
public enum LayoutNodeType {
    /** 충전 도크 — 충전 자리이지 운송 목적지가 아니다. */
    DOCK,
    /** 자재 창고 */
    WAREHOUSE,
    /** 설비 옆 하역 지점 */
    STATION,
    /** 출하장 */
    SHIPPING
}
