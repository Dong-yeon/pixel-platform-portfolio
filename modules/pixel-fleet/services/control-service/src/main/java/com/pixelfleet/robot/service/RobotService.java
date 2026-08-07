package com.pixelfleet.robot.service;

import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.realtime.RealtimePublisher;
import com.pixelfleet.traffic.LaneGraph;
import com.pixelfleet.traffic.TrafficController;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies robot telemetry. Live state (status/battery/position) is written to Redis via
 * {@link RobotLiveStateStore} — telemetry no longer touches Postgres per tick. Discrete
 * changes (status, battery-low) are still recorded as durable fleet events, and every
 * update is fanned out to dashboards via {@link RealtimePublisher}.
 *
 * <p>Master data (id/code/name/floorNo) is effectively-immutable seed data, so it is cached
 * in memory to keep the high-frequency telemetry path off the database entirely. The one
 * exception is the operator-set off-duty/disabled flags (see {@link #setOffDuty}/
 * {@link #setDisabled}) — those writes invalidate the cache explicitly.
 */
@Service
public class RobotService {

    /** Below this level the control server should schedule a return-to-charge task. */
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final RobotRepository robotRepository;
    private final RobotLiveStateStore liveStateStore;
    private final FleetEventService fleetEventService;
    private final RealtimePublisher realtimePublisher;
    private final LaneGraph laneGraph;
    private final TrafficController trafficController;

    private volatile Map<String, Robot> masterCache;

    public RobotService(
            RobotRepository robotRepository,
            RobotLiveStateStore liveStateStore,
            FleetEventService fleetEventService,
            RealtimePublisher realtimePublisher,
            LaneGraph laneGraph,
            TrafficController trafficController
    ) {
        this.robotRepository = robotRepository;
        this.liveStateStore = liveStateStore;
        this.fleetEventService = fleetEventService;
        this.realtimePublisher = realtimePublisher;
        this.laneGraph = laneGraph;
        this.trafficController = trafficController;
    }

    public List<RobotResponse> findAll() {
        return masters().values().stream().map(this::toResponse).toList();
    }

    public RobotResponse getById(Long id) {
        return toResponse(masterById(id));
    }

    public List<RobotResponse> findAvailable() {
        return masters().values().stream()
                .map(this::toResponse)
                .filter(r -> r.status() == RobotStatus.IDLE)
                .filter(r -> !r.offDuty() && !r.disabled())
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

    public void updatePosition(String robotCode, double posX, double posY, boolean laden, String payloadJson) {
        Robot master = master(robotCode);
        RobotLiveState updated = liveStateStore.findOrOffline(robotCode)
                .withPosition(posX, posY, laden, LocalDateTime.now());
        liveStateStore.save(updated); // Redis only — no per-tick Postgres write.

        // 지나온 레인 구간을 곧바로 반납한다 — 뒤따르는 로봇이 바로 쓸 수 있어야 통로 하나로도
        // 여러 대가 줄지어 다닌다. 위치 보고가 곧 주행 진척 보고다.
        trafficController.progress(master.getId(), laneGraph.segmentAt(posX, posY));

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

    /** 배차 대상에서 뺀다(휴무). 이동/작업 중인 로봇을 지금 당장 멈추지는 않는다 — 다음 배차부터 제외될 뿐이다. */
    @Transactional
    public void setOffDuty(Long id, boolean value) {
        Robot master = masterById(id);
        if (value) {
            master.markOffDuty();
        } else {
            master.markOnDuty();
        }
        robotRepository.save(master);
        masterCache = null; // 캐시가 낡은 값을 계속 돌려주지 않도록 즉시 무효화.
        fleetEventService.record(
                value ? FleetEventType.ROBOT_OFF_DUTY : FleetEventType.ROBOT_ON_DUTY,
                SourceType.OPERATOR, null,
                TargetType.ROBOT, master.getId(),
                null, EventSeverity.INFO,
                "Robot " + master.getRobotCode() + (value ? " set off-duty" : " returned on-duty"), null);
        realtimePublisher.publishRobot(toResponse(master));
    }

    /** 완전히 잠근다(고장/점검 등). off-duty보다 강한 배제 — 의미는 같은 필터에서 함께 걸러진다. */
    @Transactional
    public void setDisabled(Long id, boolean value) {
        Robot master = masterById(id);
        if (value) {
            master.disable();
        } else {
            master.enable();
        }
        robotRepository.save(master);
        masterCache = null;
        fleetEventService.record(
                value ? FleetEventType.ROBOT_DISABLED : FleetEventType.ROBOT_ENABLED,
                SourceType.OPERATOR, null,
                TargetType.ROBOT, master.getId(),
                null, value ? EventSeverity.WARNING : EventSeverity.INFO,
                "Robot " + master.getRobotCode() + (value ? " disabled" : " enabled"), null);
        realtimePublisher.publishRobot(toResponse(master));
    }

    /** ERROR 상태를 사람이 확인하고 IDLE로 되돌린다. changeStatus()는 재사용하지 않는다 — 그건 SourceType.ROBOT 고정. */
    @Transactional
    public void clearAlarm(Long id) {
        Robot master = masterById(id);
        RobotLiveState current = liveStateStore.findOrOffline(master.getRobotCode());
        if (current.status() != RobotStatus.ERROR) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "ERROR 상태가 아닌 로봇은 clear-alarm으로 되돌릴 수 없습니다. status=" + current.status());
        }
        RobotLiveState updated = current.withStatus(RobotStatus.IDLE, LocalDateTime.now());
        liveStateStore.save(updated);
        fleetEventService.record(
                FleetEventType.ROBOT_ALARM_CLEARED,
                SourceType.OPERATOR, null,
                TargetType.ROBOT, master.getId(),
                null, EventSeverity.INFO,
                "Robot " + master.getRobotCode() + " alarm cleared", null);
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

    private Robot masterById(Long id) {
        return masters().values().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 로봇입니다. id=" + id));
    }

    /** Lazily loaded, cached master list (seed data — off_duty/disabled excepted, see setOffDuty/setDisabled). */
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
