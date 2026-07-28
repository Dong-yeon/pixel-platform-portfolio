package com.pixelfleet.command;

import java.util.List;

/**
 * Downlink to the robots: how the control server issues movement commands.
 * Implemented by an MQTT adapter ({@code fleet/{robotCode}/command}); kept as an
 * interface so domain services depend on the contract, not the transport.
 */
public interface RobotCommandPublisher {

    /**
     * 로봇에게 운송 작업 수행을 지시한다. 다운링크가 꺼져 있으면 아무것도 하지 않는다.
     *
     * <p>경로(웨이포인트)는 <b>서버가 계산해서 넘긴다.</b> 로봇이 스스로 길을 정하면 서버가
     * 구간 점유를 통제할 수 없기 때문이다({@code traffic} 패키지 참고).
     *
     * @param waypoints 서버가 정한 주행 경로. 로봇은 이 점들을 순서대로 지난다.
     */
    void sendGoto(
            String robotCode,
            String taskCode,
            String origin,
            String destination,
            List<double[]> waypoints);
}
