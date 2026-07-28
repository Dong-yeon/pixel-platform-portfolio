package com.pixelfleet.robot.dto;

import com.pixelfleet.robot.domain.Robot;
import com.pixelfleet.robot.domain.RobotLiveState;
import com.pixelfleet.robot.domain.RobotStatus;
import java.time.LocalDateTime;

/**
 * Robot view = master (id/code/name from Postgres) + live state (status/battery/position
 * from Redis).
 */
public record RobotResponse(
        Long id,
        String robotCode,
        String name,
        RobotStatus status,
        int batteryPercent,
        double posX,
        double posY,
        LocalDateTime lastHeartbeatAt
) {

    public static RobotResponse of(Robot master, RobotLiveState live) {
        return new RobotResponse(
                master.getId(),
                master.getRobotCode(),
                master.getName(),
                live.status(),
                live.batteryPercent(),
                live.posX(),
                live.posY(),
                live.lastHeartbeatAt()
        );
    }
}
