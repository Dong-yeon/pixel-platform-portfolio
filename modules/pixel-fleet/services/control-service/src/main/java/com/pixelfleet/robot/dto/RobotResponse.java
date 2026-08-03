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
        /** 적재(파렛트 있음) / 공차. 지도에서 "가지러 가는 중"과 "옮기는 중"을 구분한다. */
        boolean laden,
        /**
         * 일하는 층. 위층 로봇은 아래층과 <b>좌표가 겹치므로</b> 지도가 층으로 걸러 그린다
         * — 안 그러면 2층 로봇이 1층 통로를 달리는 것처럼 보인다.
         */
        short floorNo,
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
                live.laden(),
                master.getFloorNo(),
                live.lastHeartbeatAt()
        );
    }
}
