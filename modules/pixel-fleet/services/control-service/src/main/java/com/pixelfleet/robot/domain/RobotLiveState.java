package com.pixelfleet.robot.domain;

import java.time.LocalDateTime;

/**
 * Hot, high-frequency robot state (position/battery/status). Lives in Redis, not Postgres:
 * telemetry updates it several times a second, and it is derived — the durable history is
 * the fleet_events log. Master data (id/code/name) stays in the {@link Robot} table.
 */
public record RobotLiveState(
        String robotCode,
        RobotStatus status,
        int batteryPercent,
        double posX,
        double posY,
        /**
         * 파렛트를 싣고 있는가(적재) 아닌가(공차).
         *
         * <p>물류는 절반이 "가지러 가는 중"(공차), 절반이 "옮기는 중"(적재)이다. 화면에서 이게
         * 구분되지 않으면 그냥 원이 돌아다니는 것으로만 보인다.
         *
         * <p>로봇이 위치 텔레메트리에 함께 실어 보낸다 — 서버도 leg 구조로 추론할 수 있지만
         * (leg1=공차 / leg2=적재), 실제 AMR이라면 파렛트 센서가 아는 물리 상태다.
         */
        boolean laden,
        LocalDateTime lastHeartbeatAt
) {

    /** State for a robot that has never reported telemetry yet. */
    public static RobotLiveState offline(String robotCode) {
        return new RobotLiveState(robotCode, RobotStatus.OFFLINE, 0, 0.0, 0.0, false, null);
    }

    public RobotLiveState withStatus(RobotStatus newStatus, LocalDateTime now) {
        return new RobotLiveState(robotCode, newStatus, batteryPercent, posX, posY, laden, now);
    }

    public RobotLiveState withPosition(double x, double y, boolean laden, LocalDateTime now) {
        return new RobotLiveState(robotCode, status, batteryPercent, x, y, laden, now);
    }

    public RobotLiveState withBattery(int percent, LocalDateTime now) {
        return new RobotLiveState(robotCode, status, percent, posX, posY, laden, now);
    }
}
