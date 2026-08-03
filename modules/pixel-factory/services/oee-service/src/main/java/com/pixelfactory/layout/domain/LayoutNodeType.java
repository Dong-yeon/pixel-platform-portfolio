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
    SHIPPING,
    /**
     * 검사 기착지 — 가공이 끝난 물건은 무조건 여기를 거친다.
     * 합격이면 창고동으로, 불합격이면 생산동으로 되돌아간다.
     */
    INSPECTION,
    /**
     * 엘리베이터 승강장 — 층마다 하나씩, 같은 자리에 선다.
     * <b>물건만</b> 오르내린다. AMR은 자기 층에 머물며 여기서 싣고 내린다.
     */
    ELEVATOR
}
