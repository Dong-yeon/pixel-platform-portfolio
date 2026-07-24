package com.pixelfleet.robot.service;

import com.pixelfleet.common.exception.BusinessException;
import com.pixelfleet.common.exception.ErrorCode;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.robot.domain.Robot;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.repository.RobotRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies robot telemetry to master state and records the matching fleet event.
 * The MQTT handler is the main caller; every mutation goes through {@link FleetEventService}.
 */
@Service
public class RobotService {

    /** Below this level the control server should schedule a return-to-charge task. */
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final RobotRepository robotRepository;
    private final FleetEventService fleetEventService;

    public RobotService(RobotRepository robotRepository, FleetEventService fleetEventService) {
        this.robotRepository = robotRepository;
        this.fleetEventService = fleetEventService;
    }

    @Transactional(readOnly = true)
    public List<Robot> findAll() {
        return robotRepository.findAllByOrderByRobotCodeAsc();
    }

    @Transactional(readOnly = true)
    public Robot getById(Long id) {
        return robotRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 로봇입니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Robot> findAvailable() {
        return robotRepository.findByStatus(RobotStatus.IDLE);
    }

    @Transactional
    public void changeStatus(String robotCode, RobotStatus status, String payloadJson) {
        Robot robot = requireByCode(robotCode);
        if (robot.getStatus() == status) {
            // Idempotent telemetry (robot re-reports the same status): refresh heartbeat, no event.
            robot.changeStatus(status);
            return;
        }
        robot.changeStatus(status);

        EventSeverity severity = switch (status) {
            case ERROR -> EventSeverity.ERROR;
            case OFFLINE -> EventSeverity.WARNING;
            default -> EventSeverity.INFO;
        };
        fleetEventService.record(
                FleetEventType.ROBOT_STATUS_CHANGED,
                SourceType.ROBOT, robot.getId(),
                TargetType.ROBOT, robot.getId(),
                null, severity,
                "Robot " + robotCode + " changed to " + status, payloadJson);
    }

    @Transactional
    public void updatePosition(String robotCode, double posX, double posY, String payloadJson) {
        Robot robot = requireByCode(robotCode);
        robot.updatePosition(posX, posY);
        // Position is high-frequency live telemetry, not a discrete state transition, so we keep only
        // the last-known value on the robot and do NOT append a fleet_event per tick (retention cost).
        // Phase 2 pushes the updated position straight to dashboards over WebSocket instead.
    }

    @Transactional
    public void updateBattery(String robotCode, int batteryPercent, String payloadJson) {
        Robot robot = requireByCode(robotCode);
        boolean wasAboveThreshold = robot.getBatteryPercent() >= LOW_BATTERY_THRESHOLD;
        robot.updateBattery(batteryPercent);

        if (wasAboveThreshold && batteryPercent < LOW_BATTERY_THRESHOLD) {
            // TODO(Phase 2): enqueue an automatic return-to-charge task for this robot.
            fleetEventService.record(
                    FleetEventType.ROBOT_BATTERY_LOW,
                    SourceType.ROBOT, robot.getId(),
                    TargetType.ROBOT, robot.getId(),
                    null, EventSeverity.WARNING,
                    "Robot " + robotCode + " battery low: " + batteryPercent + "%", payloadJson);
        }
    }

    private Robot requireByCode(String robotCode) {
        return robotRepository.findByRobotCode(robotCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 로봇입니다. code=" + robotCode));
    }
}
