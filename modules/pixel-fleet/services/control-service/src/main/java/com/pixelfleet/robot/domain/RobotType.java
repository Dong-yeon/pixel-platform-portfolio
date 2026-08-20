package com.pixelfleet.robot.domain;

/**
 * 로봇 종류 (P21, P22).
 *
 * <p>{@code AMR}은 공장 레인망({@link com.pixelfleet.traffic.LaneGraph})을 타지만
 * <b>창고동(WH) 1층 안쪽에는 들어가지 않는다</b>. {@code AGV}(옛 이름: 랙 피더)는 그 반대다
 * — 창고동 1층 안쪽(렉·입고장·피킹존·출하장·도크)에서만 돌고, AMR의 레인망에는 올라가지
 * 않는다(로컬 직선 이동). 두 로봇이 만나는 유일한 접점은 {@code WH-GATE-U}/{@code WH-GATE-L}
 * 두 노드다.
 *
 * <p><b>P22에서 P21(랙 피더)을 이렇게 확장했다.</b> 처음엔 AGV(당시 이름 RACK_FEEDER)가
 * 렉→피킹존 구간만 맡았고, 그 밖의 창고동 이동(입고장·출하장 등)은 여전히 AMR 몫이었다.
 * "AMR이 창고동에 아예 들어오지 않는다"는 요구가 나오면서 AGV의 담당 구역을 창고동 1층
 * 전체로 넓혔다 — 이름도 그에 맞게 바꿨다(설계 근거: {@code docs/p22-amr-agv-boundary-design.md}).
 * 창고동 2·3층은 이번 확장 범위 밖이다 — 지금도 렉 전용 좁은 존 배치 그대로이고, AMR이
 * 계속 담당한다({@code docs/p21-warehouse-rack-feeder-design.md} D10 그대로).
 */
public enum RobotType {
    AMR,
    AGV
}
