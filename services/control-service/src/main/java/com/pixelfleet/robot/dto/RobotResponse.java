package com.pixelfleet.robot.dto;

import com.pixelfleet.robot.domain.Robot;
import com.pixelfleet.robot.domain.RobotStatus;
import java.time.LocalDateTime;

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

    public static RobotResponse from(Robot r) {
        return new RobotResponse(
                r.getId(),
                r.getRobotCode(),
                r.getName(),
                r.getStatus(),
                r.getBatteryPercent(),
                r.getPosX(),
                r.getPosY(),
                r.getLastHeartbeatAt()
        );
    }
}
