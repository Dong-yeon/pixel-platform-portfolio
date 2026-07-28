package com.pixelfleet.robot.service;

import com.pixelfleet.common.exception.BusinessException;
import com.pixelfleet.common.exception.ErrorCode;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.realtime.RealtimePublisher;
import com.pixelfleet.robot.domain.Robot;
import com.pixelfleet.robot.domain.RobotLiveState;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.robot.livestate.RobotLiveStateStore;
import com.pixelfleet.robot.repository.RobotRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Applies robot telemetry. Live state (status/battery/position) is written to Redis via
 * {@link RobotLiveStateStore} — telemetry no longer touches Postgres per tick. Discrete
 * changes (status, battery-low) are still recorded as durable fleet events, and every
 * update is fanned out to dashboards via {@link RealtimePublisher}.
 *
 * <p>Master data (id/code/name) is immutable seed data, so it is cached in memory to keep
 * the high-frequency telemetry path off the database entirely.
 */
@Service
public class RobotService {

    /** Below this level the control server should schedule a return-to-charge task. */
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final RobotRepository robotRepository;
    private final RobotLiveStateStore liveStateStore;
    private final FleetEventService fleetEventService;
    private final RealtimePublisher realtimePublisher;

    private volatile Map<String, Robot> masterCache;

    public RobotService(
            RobotRepository robotRepository,
            RobotLiveStateStore liveStateStore,
            FleetEventService fleetEventService,
            RealtimePublisher realtimePublisher
    ) {
        this.robotRepository = robotRepository;
        this.liveStateStore = liveStateStore;
        this.fleetEventService = fleetEventService;
        this.realtimePublisher = realtimePublisher;
    }

    public List<RobotResponse> findAll() {
        return masters().values().stream().map(this::toResponse).toList();
    }

    public RobotResponse getById(Long id) {
        Robot master = masters().values().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 로봇입니다. id=" + id));
        return toResponse(master);
    }

    public List<RobotResponse> findAvailable() {
        return masters().values().stream()
                .map(this::toResponse)
                .filter(r -> r.status() == RobotStatus.IDLE)
                .toList();
    }

    public void changeStatus(String robotCode, RobotStatus status, String payloadJson) {
        Robot master = master(robotCode);
        RobotLiveState current = liveStateStore.findOrOffline(robotCode);
        LocalDateTime now = LocalDateTime.now();

        if (current.status() == status) {
            // Idempotent telemetry: refresh the heartbeat only, no event or push.
            liveStateStore.save(current.withStatus(status, now));
            return;
        }

        RobotLiveState updated = current.withStatus(status, now);
        liveStateStore.save(updated);

        EventSeverity severity = switch (status) {
            case ERROR -> EventSeverity.ERROR;
            case OFFLINE -> EventSeverity.WARNING;
            default -> EventSeverity.INFO;
        };
        fleetEventService.record(
                FleetEventType.ROBOT_STATUS_CHANGED,
                SourceType.ROBOT, master.getId(),
                TargetType.ROBOT, master.getId(),
                null, severity,
                "Robot " + robotCode + " changed to " + status, payloadJson);
        realtimePublisher.publishRobot(RobotResponse.of(master, updated));
    }

    public void updatePosition(String robotCode, double posX, double posY, String payloadJson) {
        Robot master = master(robotCode);
        RobotLiveState updated = liveStateStore.findOrOffline(robotCode).withPosition(posX, posY, LocalDateTime.now());
        liveStateStore.save(updated); // Redis only — no per-tick Postgres write.
        realtimePublisher.publishRobot(RobotResponse.of(master, updated));
    }

    public void updateBattery(String robotCode, int batteryPercent, String payloadJson) {
        Robot master = master(robotCode);
        RobotLiveState current = liveStateStore.findOrOffline(robotCode);
        boolean wasAboveThreshold = current.batteryPercent() >= LOW_BATTERY_THRESHOLD;
        RobotLiveState updated = current.withBattery(batteryPercent, LocalDateTime.now());
        liveStateStore.save(updated);

        if (wasAboveThreshold && batteryPercent < LOW_BATTERY_THRESHOLD) {
            // TODO: enqueue an automatic return-to-charge task for this robot.
            fleetEventService.record(
                    FleetEventType.ROBOT_BATTERY_LOW,
                    SourceType.ROBOT, master.getId(),
                    TargetType.ROBOT, master.getId(),
                    null, EventSeverity.WARNING,
                    "Robot " + robotCode + " battery low: " + batteryPercent + "%", payloadJson);
        }
        realtimePublisher.publishRobot(RobotResponse.of(master, updated));
    }

    private RobotResponse toResponse(Robot master) {
        return RobotResponse.of(master, liveStateStore.findOrOffline(master.getRobotCode()));
    }

    private Robot master(String robotCode) {
        Robot master = masters().get(robotCode);
        if (master == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 로봇입니다. code=" + robotCode);
        }
        return master;
    }

    /** Lazily loaded, cached master list (seed data, effectively immutable). */
    private Map<String, Robot> masters() {
        Map<String, Robot> cached = masterCache;
        if (cached == null) {
            cached = robotRepository.findAllByOrderByRobotCodeAsc().stream()
                    .collect(Collectors.toMap(Robot::getRobotCode, Function.identity(),
                            (a, b) -> a, LinkedHashMap::new));
            masterCache = cached;
        }
        return cached;
    }
}
