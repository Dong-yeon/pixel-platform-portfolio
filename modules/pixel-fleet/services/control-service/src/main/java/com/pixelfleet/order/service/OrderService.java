package com.pixelfleet.order.service;

import com.pixelfleet.command.RobotCommandPublisher;
import com.pixelfleet.event.domain.EventSeverity;
import com.pixelfleet.event.domain.FleetEventType;
import com.pixelfleet.event.domain.SourceType;
import com.pixelfleet.event.domain.TargetType;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.domain.OrderStatus;
import com.pixelfleet.order.domain.OrderStep;
import com.pixelfleet.order.domain.StepStatus;
import com.pixelfleet.order.repository.FleetOrderRepository;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.robot.service.RobotService;
import com.pixelfleet.task.dispatch.AssignmentPolicy;
import com.pixelfleet.task.event.TaskLifecycleChanged;
import com.pixelfleet.traffic.LaneGraph;
import com.pixelfleet.traffic.TrafficController;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 주문(다단 스텝) 실행 엔진 — 예전 TaskService의 일반화.
 *
 * <p><b>대기 상태가 DB에 산다.</b> 예전엔 "leg2 계획"과 "픽업 도착 사실"이 서비스 메모리
 * (pendingSecondLeg/arrivedAtPickup)에만 있어서 서버가 재시작하면 픽업에서 기다리던
 * 로봇이 워치독 타임아웃(300초)까지 방치됐다. 이제 "로봇이 스텝 k를 마치고 다음 레그를
 * 기다린다"는 사실은 {@code status=EXECUTING ∧ step[k]=DONE ∧ currentStepIndex=k+1 ∧
 * step[k+1]=EXECUTABLE}이라는 DB 술어 자체다 — 재시작 후 grant 패스가 DB만 보고 재개한다.
 * 메모리에 남는 것은 경로 계획 캐시 하나뿐이고, 잃어버려도 다시 계산하면 된다.
 *
 * <p><b>교착 안전성의 기둥: 놓고 나서 요청한다(release-before-request).</b> 스텝 경계마다
 * 로봇이 쥔 구간을 전부 반납한 뒤 다음 레그를 예약한다. 쥔 채로 요청하면 서로가 쥔 구간을
 * 기다리는 교착이 된다 — leg1/leg2 시절 실제로 로봇 4대가 픽업에서 전부 멈춘 사고의 교훈이며,
 * 스텝이 N개가 되어도 경계마다 같은 규율을 지키면 교착 요인이 늘지 않는다.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_RETRIES = 3;

    /** orderCode → 다음 레그 계획. <b>순수 캐시</b> — 잃어도 grant 패스가 다시 계산한다. */
    private final Map<String, LaneGraph.RoutePlan> nextLegCache = new ConcurrentHashMap<>();

    private final FleetOrderRepository orderRepository;
    private final RobotService robotService;
    private final FleetEventService fleetEventService;
    private final RobotCommandPublisher robotCommandPublisher;
    private final AssignmentPolicy assignmentPolicy;
    private final LaneGraph laneGraph;
    private final TrafficController trafficController;
    private final LocationRegistry locations;
    private final ApplicationEventPublisher eventPublisher;

    /** 화물 엘리베이터가 층을 옮기는 데 걸리는 시간. */
    private final int elevatorTravelSeconds;

    public OrderService(
            FleetOrderRepository orderRepository,
            RobotService robotService,
            FleetEventService fleetEventService,
            RobotCommandPublisher robotCommandPublisher,
            AssignmentPolicy assignmentPolicy,
            LaneGraph laneGraph,
            TrafficController trafficController,
            LocationRegistry locations,
            ApplicationEventPublisher eventPublisher,
            @Value("${fleet.elevator.travel-seconds:12}") int elevatorTravelSeconds
    ) {
        this.orderRepository = orderRepository;
        this.robotService = robotService;
        this.fleetEventService = fleetEventService;
        this.robotCommandPublisher = robotCommandPublisher;
        this.assignmentPolicy = assignmentPolicy;
        this.laneGraph = laneGraph;
        this.trafficController = trafficController;
        this.locations = locations;
        this.eventPublisher = eventPublisher;
        this.elevatorTravelSeconds = elevatorTravelSeconds;
    }

    /** 스텝 명세 — create의 입력 단위. */
    public record StepSpec(String location, boolean forLoad, boolean forUnload) {

        public static StepSpec load(String location) {
            return new StepSpec(location, true, false);
        }

        public static StepSpec unload(String location) {
            return new StepSpec(location, false, true);
        }
    }

    /** 최근 주문(화면용). 스텝까지 함께 읽어 트랜잭션 밖에서 응답을 만들어도 안전하다. */
    @Transactional(readOnly = true)
    public List<FleetOrder> findRecent() {
        return orderRepository.findTop200ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public FleetOrder getById(Long id) {
        return orderRepository.findWithStepsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "존재하지 않는 주문입니다. id=" + id));
    }

    /**
     * 주문 생성.
     *
     * <p>모든 스텝은 <b>같은 층</b>이어야 한다 — 로봇은 층을 오가지 못한다(엘리베이터는
     * 화물용). 예외 하나: <b>2스텝(싣고→내리는) 주문이 층을 넘으면</b> 오늘까지의 규칙대로
     * 승강장에서 두 주문으로 끊어 준다. 스텝이 셋 이상인데 층이 섞이면 어느 스텝까지가
     * 어느 층 몫인지 서버가 판단할 수 없으므로 명확히 거부한다 — 호출자가 봉인 주문
     * 여러 개로 스스로 끊는 것이 정직하다.
     */
    @Transactional
    public FleetOrder create(String orderCode, String externalId, List<StepSpec> stepSpecs,
                             int priority, boolean stepFixed) {
        if (stepSpecs == null || stepSpecs.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "스텝이 최소 하나는 필요합니다.");
        }
        orderRepository.findByOrderCode(orderCode).ifPresent(o -> {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 존재하는 주문 코드입니다: " + orderCode);
        });
        for (int i = 1; i < stepSpecs.size(); i++) {
            if (stepSpecs.get(i).location().equals(stepSpecs.get(i - 1).location())) {
                // 같은 노드로 이어지는 스텝은 길이 0짜리 레그가 된다 — 예약할 구간이 없어
                // "성공"하고, 로봇은 완료를 구분해 보고할 수 없다.
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "연속한 두 스텝이 같은 지점입니다: " + stepSpecs.get(i).location());
            }
        }

        List<Short> floors = stepSpecs.stream().map(s -> locations.floorOf(s.location())).toList();
        boolean crossFloor = floors.stream().distinct().count() > 1;

        if (crossFloor && isSimpleHaul(stepSpecs)) {
            return createSplitAtElevator(orderCode, externalId, stepSpecs, priority, floors);
        }
        if (crossFloor) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "층이 섞인 다단 스텝 주문은 지원하지 않습니다. 층별로 주문을 나눠 주세요. 층: " + floors);
        }

        FleetOrder order = new FleetOrder(orderCode, externalId, priority, stepFixed, floors.get(0));
        stepSpecs.forEach(s -> order.addStep(s.location(), s.forLoad(), s.forUnload()));
        orderRepository.save(order);
        recordCreated(order, null);
        return order;
    }

    private boolean isSimpleHaul(List<StepSpec> specs) {
        return specs.size() == 2 && specs.get(0).forLoad() && specs.get(1).forUnload();
    }

    /** 층을 넘는 단순 이송 — 출발 층 승강장까지의 앞 주문을 만들고 최종 목적지를 달아 둔다. */
    private FleetOrder createSplitAtElevator(String orderCode, String externalId,
                                             List<StepSpec> specs, int priority, List<Short> floors) {
        short originFloor = floors.get(0);
        String finalDestination = specs.get(1).location();

        FleetOrder order = new FleetOrder(orderCode, externalId, priority, true, originFloor);
        order.addStep(specs.get(0).location(), true, false);
        order.addStep(elevatorNode(originFloor), false, true);
        order.handOffTo(finalDestination);
        orderRepository.save(order);
        recordCreated(order, "[엘리베이터로 " + floors.get(1) + "층 " + finalDestination + "까지]");
        return order;
    }

    /** 층간 체인 뒷 주문 — 물건이 도착한 층에서 이어받는다. 완료 보고 중복에 대비해 멱등. */
    private void createHandoffOrder(FleetOrder finished) {
        String finalDestination = finished.getHandoffDestination();
        short arrivalFloor = locations.floorOf(finalDestination);
        String code = finished.getOrderCode() + "-F" + arrivalFloor;
        if (orderRepository.findByOrderCode(code).isPresent()) {
            return;
        }

        // externalId를 물려받는다 — 상류는 체인이 쪼개진 사정을 모르고, 자기가 낸
        // 번호 하나로 최종 결과를 기다린다.
        FleetOrder leg = new FleetOrder(code, finished.getExternalId(),
                finished.getPriority(), true, arrivalFloor);
        leg.addStep(elevatorNode(arrivalFloor), true, false);
        leg.addStep(finalDestination, false, true);
        leg.continues(finished.getOrderCode(), LocalDateTime.now().plusSeconds(elevatorTravelSeconds));
        orderRepository.save(leg);

        fleetEventService.record(
                FleetEventType.TASK_CREATED,
                SourceType.SYSTEM, null,
                TargetType.TASK, leg.getId(),
                leg.getId(), EventSeverity.INFO,
                "엘리베이터: " + finished.getOrderCode() + " 화물이 " + arrivalFloor + "층으로 이동 중 "
                        + "(" + elevatorTravelSeconds + "초 후 " + elevatorNode(arrivalFloor) + "에서 인수)", null);
    }

    private String elevatorNode(short floor) {
        return "WH-ELEV-" + floor + "F";
    }

    // ---- 배차 ----

    /**
     * 배차 한 번 — 앞에서부터 훑어 <b>배차 가능한 첫 주문</b>을 내보낸다.
     * 맨 앞 하나만 시도하고 끝내면 층별 배차에서 head-of-line 블로킹이 된다
     * (1층 주문이 큐 앞을 채우면 위층 로봇이 영원히 논다 — 실측했던 문제).
     */
    @Transactional
    public FleetOrder dispatchOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<FleetOrder> queue = orderRepository
                .findByStatusOrderByPriorityDescIdAsc(OrderStatus.TO_BE_ALLOCATED).stream()
                .filter(o -> !o.isSuspended() && !o.isFault() && o.isDispatchable(now)
                        && !o.getSteps().isEmpty())
                .toList();
        if (queue.isEmpty()) {
            return null;
        }

        // 로봇 상태(텔레메트리)만 믿지 않는다 — 배차 직후 MOVING 보고 전이면 IDLE로 보여
        // 이중 배차될 수 있다. 진행 중 주문 유무(DB)로 함께 거른다. PENDING 포함:
        // 미봉인 주문은 스텝을 소진해도 로봇을 쥔 채다.
        List<RobotResponse> available = robotService.findAvailable().stream()
                .filter(r -> !orderRepository.existsByAssignedRobotIdAndStatusIn(
                        r.id(), List.of(OrderStatus.ALLOCATED, OrderStatus.EXECUTING, OrderStatus.PENDING)))
                .toList();
        if (available.isEmpty()) {
            return null;
        }

        for (FleetOrder candidate : queue) {
            if (tryDispatch(candidate, available)) {
                return candidate;
            }
        }
        return null;
    }

    /** 주문 하나를 배차해 본다. 실패는 부작용 없이 false — 다음 주문으로 넘어가도 된다. */
    private boolean tryDispatch(FleetOrder order, List<RobotResponse> available) {
        RobotResponse robot = assignmentPolicy.selectRobot(order, available).orElse(null);
        if (robot == null) {
            return false;
        }

        // 접근 레그(로봇→step0)만 예약하고 보낸다 — 주문 전체를 잡으면 먼 로봇이
        // 공장을 가로지르는 구간을 통째로 요구해 다른 로봇이 거의 못 움직인다.
        OrderStep first = order.stepAt(0);
        LaneGraph.RoutePlan approach = laneGraph.planByNode(
                new double[]{robot.posX(), robot.posY()}, first.getLocationNode());
        if (!trafficController.tryReserve(robot.id(), approach.segments())) {
            log.info("Traffic: robot {} cannot take {} — approach segments busy {}",
                    robot.robotCode(), order.getOrderCode(), laneGraph.describe(approach.segments()));
            return false;
        }

        order.assignTo(robot.id());
        // 다음 배차 패스가 이중 배차하지 않도록 즉시 MOVING으로. 실제 생명주기는 텔레메트리가 끈다.
        robotService.changeStatus(robot.robotCode(), RobotStatus.MOVING, null);
        fleetEventService.record(
                FleetEventType.TASK_ASSIGNED,
                SourceType.SYSTEM, null,
                TargetType.TASK, order.getId(),
                order.getId(), EventSeverity.INFO,
                "Order " + order.getOrderCode() + " assigned to robot " + robot.robotCode(), null);

        publishAfterCommit(() -> robotCommandPublisher.sendGoto(
                robot.robotCode(), order.getOrderCode(), 0,
                first.getLocationNode(), first.isForLoad(), first.isForUnload(),
                approach.waypoints()));
        return true;
    }

    // ---- 로봇 보고 ----

    /** 시작 보고 — QoS1 중복에 대비해 멱등(FleetOrder.start가 EXECUTING이면 무시). */
    @Transactional
    public void markStarted(String orderCode) {
        FleetOrder order = requireByCode(orderCode);
        if (order.getStatus() == OrderStatus.EXECUTING) {
            return;
        }
        order.start();
        fleetEventService.record(
                FleetEventType.TASK_STARTED,
                SourceType.ROBOT, order.getAssignedRobotId(),
                TargetType.TASK, order.getId(),
                order.getId(), EventSeverity.INFO,
                "Order " + orderCode + " started", null);
    }

    /**
     * 스텝 완료 보고 — 엔진의 심장.
     *
     * <p>멱등 가드가 앞에 있다: MQTT는 최소 1회 전달이라 같은 step-done이 두 번 올 수
     * 있고, 첫 처리가 currentStepIndex를 이미 밀었으므로 중복은 여기서 걸러진다.
     * 로봇은 다음 레그를 기다리는 동안 step-done을 주기적으로 재발행하므로(유실 자가 치유),
     * <b>이미 닫힌 주문의 마지막 스텝 중복이면 ORDER_DONE을 다시 보내</b> 로봇을 풀어 준다
     * — ORDER_DONE 유실로 로봇이 영원히 서는 구멍(워치독 사각)을 막는 핸드셰이크다.
     */
    @Transactional
    public void markStepDone(String orderCode, int stepIndex) {
        FleetOrder order = requireByCode(orderCode);

        if (order.getStatus() == OrderStatus.DONE && order.isLastStep(stepIndex)) {
            robotCodeOf(order).ifPresent(code -> robotCommandPublisher.sendOrderDone(code, orderCode));
            return;
        }
        // step-done은 시작의 증거이기도 하다 — started가 유실됐거나(QoS1도 유실은 가능),
        // 접근 레그가 아주 짧아 started와 거의 동시에 도착한 경우 여기서 시작을 보정한다.
        if (order.getStatus() == OrderStatus.ALLOCATED && stepIndex == order.getCurrentStepIndex()) {
            order.start();
        }
        if (order.getStatus() != OrderStatus.EXECUTING
                || stepIndex != order.getCurrentStepIndex()
                || order.stepAt(stepIndex).getStatus() == StepStatus.DONE) {
            log.debug("Ignoring stale step-done: order {} step {} (status {}, current {})",
                    orderCode, stepIndex, order.getStatus(), order.getCurrentStepIndex());
            return;
        }

        order.stepDone();

        // 스텝 경계마다 무조건 전부 반납 — 그다음에야 다음 레그를 요청한다.
        // (정차 자리는 레인 밖이라 서 있어도 아무 길도 막지 않는다.)
        trafficController.release(order.getAssignedRobotId());

        if (order.isLastStep(stepIndex)) {
            finishSteps(order);
            return;
        }

        int next = stepIndex + 1;
        order.advanceToStep(next);
        grantNextLeg(order);
    }

    /** 마지막 스텝까지 끝났다 — 봉인이면 닫고, 미봉인이면 로봇을 쥔 채 기다린다. */
    private void finishSteps(FleetOrder order) {
        nextLegCache.remove(order.getOrderCode());

        if (!order.isStepFixed()) {
            // ORDER_DONE을 보내지 않는다 — 로봇은 주문을 쥔 채(짐을 실었을 수도 있다)
            // add-steps 를 기다린다. 외부 통지도 없다: 아직 끝난 것이 아니다.
            order.parkUnsealed();
            return;
        }

        order.complete();
        robotCodeOf(order).ifPresent(code ->
                publishAfterCommit(() -> robotCommandPublisher.sendOrderDone(code, order.getOrderCode())));
        fleetEventService.record(
                FleetEventType.TASK_COMPLETED,
                SourceType.ROBOT, order.getAssignedRobotId(),
                TargetType.TASK, order.getId(),
                order.getId(), EventSeverity.INFO,
                "Order " + order.getOrderCode() + " completed", null);

        // 층간 체인의 앞 구간이면 밖에 알리지 않는다 — 듣는 쪽(WMS)에게 완료는
        // "물건이 목적지에 도착했다"는 뜻이고, 승강장은 목적지가 아니다.
        if (order.getHandoffDestination() != null) {
            createHandoffOrder(order);
            return;
        }
        eventPublisher.publishEvent(TaskLifecycleChanged.completed(notificationCode(order)));
    }

    /**
     * 다음 레그를 예약해 내준다. 확보 못 하면 아무것도 하지 않는다 — 로봇은 정차 자리에서
     * 기다리고, 주문의 대기 상태는 DB에 있으므로 {@link #grantPendingNextLegs}가 재시도한다.
     */
    private void grantNextLeg(FleetOrder order) {
        OrderStep current = order.stepAt(order.getCurrentStepIndex());
        if (current.getStatus() != StepStatus.EXECUTABLE) {
            return;
        }

        LaneGraph.RoutePlan plan = nextLegCache.computeIfAbsent(order.getOrderCode(), k -> {
            OrderStep previous = order.stepAt(order.getCurrentStepIndex() - 1);
            return laneGraph.plan(
                    laneGraph.nodePosition(previous.getLocationNode()),
                    laneGraph.nodePosition(current.getLocationNode()));
        });

        if (!trafficController.tryReserve(order.getAssignedRobotId(), plan.segments())) {
            log.debug("Traffic: {} waiting for step {} — segments busy {}",
                    order.getOrderCode(), order.getCurrentStepIndex(), laneGraph.describe(plan.segments()));
            return;
        }
        nextLegCache.remove(order.getOrderCode());
        order.beginCurrentStep();

        int stepIndex = order.getCurrentStepIndex();
        robotCodeOf(order).ifPresent(code -> publishAfterCommit(() -> robotCommandPublisher.sendGoto(
                code, order.getOrderCode(), stepIndex,
                current.getLocationNode(), current.isForLoad(), current.isForUnload(),
                plan.waypoints())));
    }

    /**
     * 다음 레그를 기다리는 주문들에 재시도한다(배차 스케줄러가 매 패스 호출).
     * 우선순위·id 순으로 <b>결정적으로</b> 돈다 — 무순서로 돌면 경합이 심한 주문이
     * 계속 추월당해 기아에 빠질 수 있다.
     */
    @Transactional
    public void grantPendingNextLegs() {
        orderRepository.findByStatusOrderByPriorityDescIdAsc(OrderStatus.EXECUTING).stream()
                .filter(o -> !o.isSuspended() && !o.isFault() && o.getAssignedRobotId() != null)
                .filter(o -> o.getCurrentStepIndex() >= 0 && o.getCurrentStepIndex() < o.getSteps().size())
                .filter(o -> o.stepAt(o.getCurrentStepIndex()).getStatus() == StepStatus.EXECUTABLE)
                .forEach(this::grantNextLeg);
    }

    /** 실패 보고(로봇·워치독) — 예산이 남으면 전체 리셋 후 재큐, 소진이면 fault 동결. */
    @Transactional
    public void markFailed(String orderCode, String reason) {
        FleetOrder order = requireByCode(orderCode);
        if (order.getStatus().isTerminal() || order.isFault()) {
            return; // 중복 실패 보고
        }
        Long robotId = order.getAssignedRobotId();
        nextLegCache.remove(orderCode);
        trafficController.release(robotId);

        fleetEventService.record(
                FleetEventType.TASK_FAILED,
                SourceType.ROBOT, robotId,
                TargetType.TASK, order.getId(),
                order.getId(), EventSeverity.ERROR,
                "Order " + orderCode + " failed: " + reason, null);

        if (order.getFailureNum() < MAX_RETRIES) {
            order.resetForRetry(reason);
            fleetEventService.record(
                    FleetEventType.TASK_RETRIED,
                    SourceType.SYSTEM, null,
                    TargetType.TASK, order.getId(),
                    order.getId(), EventSeverity.WARNING,
                    "Order " + orderCode + " re-queued (attempt " + (order.getFailureNum() + 1) + ")", null);
            return;
        }

        order.faultOut(reason);
        // 최종 실패만 밖에 알린다 — 재시도할 주문을 실패로 알리면 받은 쪽이 전표를 성급히 정리한다.
        eventPublisher.publishEvent(TaskLifecycleChanged.failed(notificationCode(order), reason));
    }

    /** 밖에 알릴 때 쓰는 코드 — 상류가 낸 externalId가 있으면 그것, 없으면 주문 코드. */
    private String notificationCode(FleetOrder order) {
        return order.getExternalId() != null ? order.getExternalId() : order.getOrderCode();
    }

    /**
     * 로봇 명령은 <b>트랜잭션 커밋 후에</b> 발행한다.
     *
     * <p>커밋 전에 보내면 경합이 생긴다 — 로봇이 밀리초 안에 {@code started}로 응답하는데
     * 그 처리(별도 트랜잭션)가 아직 커밋되지 않은 배차를 보지 못해 상태 전이가 거부되고,
     * {@code started}는 재발행이 없어 주문이 워치독(30초)에 걸려 통째로 재큐된다.
     * 실측: 배차 직후 주문들이 "assignment not acknowledged"로 연쇄 재시도됐다.
     */
    private void publishAfterCommit(Runnable command) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    command.run();
                }
            });
        } else {
            command.run();
        }
    }

    private java.util.Optional<String> robotCodeOf(FleetOrder order) {
        if (order.getAssignedRobotId() == null) {
            return java.util.Optional.empty();
        }
        return robotService.findAll().stream()
                .filter(r -> r.id().equals(order.getAssignedRobotId()))
                .map(RobotResponse::robotCode)
                .findFirst();
    }

    private void recordCreated(FleetOrder order, String suffix) {
        String route = order.getSteps().stream()
                .map(OrderStep::getLocationNode)
                .reduce((a, b) -> a + " -> " + b)
                .orElse("");
        fleetEventService.record(
                FleetEventType.TASK_CREATED,
                SourceType.OPERATOR, null,
                TargetType.TASK, order.getId(),
                order.getId(), EventSeverity.INFO,
                "Order " + order.getOrderCode() + " created (" + route + ")"
                        + (suffix != null ? " " + suffix : ""), null);
    }

    private FleetOrder requireByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "존재하지 않는 주문입니다. code=" + orderCode));
    }
}
