package com.pixelfleet.realtime.ws.query;

import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.dto.RobotResponse;

/** {@link RobotResponse}보다 의도적으로 좁다 — 이름대로 위치 관련 필드만(배터리·이름 없음). */
public record RobotPositionOnly(
        String robotCode,
        double posX,
        double posY,
        short floorNo,
        RobotStatus status
) {

    public static RobotPositionOnly from(RobotResponse r) {
        return new RobotPositionOnly(r.robotCode(), r.posX(), r.posY(), r.floorNo(), r.status());
    }
}
