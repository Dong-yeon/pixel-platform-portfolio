package com.pixelfleet.traffic;

/**
 * 레인그래프의 엣지 하나가 막히거나 풀렸다 (P20-4).
 *
 * <p>{@link com.pixelfleet.order.service.OrderService}가 이 이벤트를 듣고 경로 계획
 * 캐시({@code nextLegCache})를 비운다 — 그래야 대기 중인 주문이 <b>다음 재시도에서</b>
 * {@link LaneGraph}로 새로 경로를 계산해 막힌 엣지를 피한다. 어떤 엣지인지는 담지 않는다
 * (캐시를 전부 비우는 값싼 연산이라 굳이 걸러낼 필요가 없다) — 상세 근거는
 * {@code docs/p20-layout-routing-design.md} D5.
 */
public record LayoutObstacleChanged() {}
