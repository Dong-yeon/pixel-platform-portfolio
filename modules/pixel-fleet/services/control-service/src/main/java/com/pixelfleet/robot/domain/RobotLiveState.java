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
        LocalDateTime lastHeartbeatAt
) {

    /** State for a robot that has never reported telemetry yet. */
    public static RobotLiveState offline(String robotCode) {
        return new RobotLiveState(robotCode, RobotStatus.OFFLINE, 0, 0.0, 0.0, null);
    }

    public RobotLiveState withStatus(RobotStatus newStatus, LocalDateTime now) {
        return new RobotLiveState(robotCode, newStatus, batteryPercent, posX, posY, now);
    }

    public RobotLiveState withPosition(double x, double y, LocalDateTime now) {
        return new RobotLiveState(robotCode, status, batteryPercent, x, y, now);
    }

    public RobotLiveState withBattery(int percent, LocalDateTime now) {
        return new RobotLiveState(robotCode, status, percent, posX, posY, now);
    }
}
