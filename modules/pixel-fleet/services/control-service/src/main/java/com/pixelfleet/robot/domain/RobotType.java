package com.pixelfleet.robot.domain;

/**
 * 로봇 종류 (P21).
 *
 * <p>{@code AMR}은 공장 전체 레인망({@link com.pixelfleet.traffic.LaneGraph})을 탄다.
 * {@code RACK_FEEDER}(랙 피더)는 창고동 렉에서 물건을 꺼내 피킹존까지만 옮기는 로봇 —
 * 자기 존(zone) 밖의 렉에도, AMR의 레인망에도 올라가지 않는다(설계 근거:
 * {@code docs/p21-warehouse-rack-feeder-design.md} D1·D2).
 */
public enum RobotType {
    AMR,
    RACK_FEEDER
}
