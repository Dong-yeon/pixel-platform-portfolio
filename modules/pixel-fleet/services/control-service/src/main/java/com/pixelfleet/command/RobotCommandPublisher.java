package com.pixelfleet.command;

import java.util.List;

/**
 * Downlink to the robots: how the control server issues movement commands.
 * Implemented by an MQTT adapter ({@code fleet/{robotCode}/command}); kept as an
 * interface so domain services depend on the contract, not the transport.
 */
public interface RobotCommandPublisher {

    /**
     * 스텝 하나(레그)의 주행을 지시한다. 다운링크가 꺼져 있으면 아무것도 하지 않는다.
     *
     * <p>경로(웨이포인트)는 <b>서버가 계산해서 넘긴다.</b> 로봇이 스스로 길을 정하면 서버가
     * 구간 점유를 통제할 수 없기 때문이다({@code traffic} 패키지 참고).
     *
     * @param location  이 레그의 목적지 노드 — 로봇이 정차 자리를 고를 때 쓴다
     * @param forLoad   이 스텝에서 싣는가 — 로봇의 적재 상태(laden)의 근원
     * @param waypoints 서버가 정한 주행 경로. 로봇은 이 점들을 순서대로 지난다
     */
    void sendGoto(
            String robotCode,
            String orderCode,
            int stepIndex,
            String location,
            boolean forLoad,
            boolean forUnload,
            List<double[]> waypoints);

    /**
     * 주문 종료를 알린다. <b>로봇은 마지막 스텝이 무엇인지 모른다</b> — 미봉인 주문은
     * 스텝이 이어질 수 있어서, 닫는 결정은 항상 서버가 명시적으로 내린다.
     */
    void sendOrderDone(String robotCode, String orderCode);
}
