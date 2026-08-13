package com.pixelfleet.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelfleet.command.RobotCommandPublisher;
import com.pixelfleet.event.service.FleetEventService;
import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.repository.FleetOrderRepository;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.domain.RobotType;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.robot.service.RobotService;
import com.pixelfleet.task.dispatch.AssignmentPolicy;
import com.pixelfleet.traffic.LaneGraph;
import com.pixelfleet.traffic.TrafficController;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 실제로 겪은 장애 3건 중 2건(hold-and-wait 교착, leg2/다음 스텝 오배달)의 회귀 테스트.
 * 세 번째(배터리 사각지대)는 {@link com.pixelfleet.task.dispatch.NearestBatteryAwareAssignmentPolicy}와
 * robot-sim {@code application.yml}의 불변식 테스트로 별도 존재한다.
 *
 * <p>{@link TrafficController}는 mock하지 않고 실제 인스턴스를 쓴다 — 구간 예약이
 * 진짜로 풀리고 잡히는지 봐야 교착이 재현/방지되는지 검증할 수 있다. {@link FleetOrder}도
 * 실제 도메인 객체를 만들어 진짜 상태 전이를 태운다. 그 외(저장소·메시징·경로계산)만
 * mock한다 — I/O 경계만 잘라내고 오케스트레이션 로직은 실제 코드로 돈다.
 */
class OrderServiceRegressionTest {

    private static final long ROBOT_1 = 1L;
    private static final long ROBOT_2 = 2L;

    /** 로봇1의 접근 레그가 쥔 구간 — 로봇2의 다음 레그가 필요로 한다(교차 의존). */
    private static final String SEG_HELD_BY_ROBOT_1 = "SEG-1";
    private static final String SEG_HELD_BY_ROBOT_2 = "SEG-2";

    private FleetOrderRepository orderRepository;
    private RobotService robotService;
    private RobotCommandPublisher robotCommandPublisher;
    private LaneGraph laneGraph;
    private TrafficController trafficController;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(FleetOrderRepository.class);
        robotService = mock(RobotService.class);
        FleetEventService fleetEventService = mock(FleetEventService.class);
        robotCommandPublisher = mock(RobotCommandPublisher.class);
        AssignmentPolicy assignmentPolicy = mock(AssignmentPolicy.class);
        laneGraph = mock(LaneGraph.class);
        trafficController = new TrafficController(); // 실제 구현 — 교착은 진짜로 재현/방지돼야 한다.
        LocationRegistry locations = mock(LocationRegistry.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        when(laneGraph.nodePosition(anyString())).thenReturn(new double[]{0, 0});

        when(robotService.findAll()).thenReturn(List.of(
                robot(ROBOT_1, "AMR-01"),
                robot(ROBOT_2, "AMR-02")
        ));

        orderService = new OrderService(
                orderRepository, robotService, fleetEventService, robotCommandPublisher,
                assignmentPolicy, laneGraph, trafficController, locations, eventPublisher, 12);
    }

    private static RobotResponse robot(long id, String code) {
        return new RobotResponse(id, code, code, RobotStatus.MOVING, 80, 0, 0, true, (short) 1,
                LocalDateTime.now(), false, false, RobotType.AMR, null);
    }

    /** 픽업→하역 2스텝 주문. 접근 레그(step0)를 이미 확보하고 EXECUTING까지 진행시켜 둔다. */
    private FleetOrder inProgressOrder(String code, long robotId, String initiallyHeldSegment) {
        FleetOrder order = new FleetOrder(code, null, 1, true, (short) 1);
        order.addStep("PICKUP-" + code, true, false);
        order.addStep("DROPOFF-" + code, false, true);
        order.assignTo(robotId); // step0 EXECUTING, currentStepIndex=0
        order.start();           // EXECUTING
        boolean reserved = trafficController.tryReserve(robotId, List.of(initiallyHeldSegment));
        assertThat(reserved).as("테스트 전제: 접근 레그 구간을 미리 쥐고 있어야 한다").isTrue();
        when(orderRepository.findByOrderCode(code)).thenReturn(Optional.of(order));
        return order;
    }

    /**
     * <b>이 테스트가 실제로 무엇을 검증하는지 정직하게 적어 둔다.</b> 순차 호출(단일
     * 스레드)이라 release-before-request 순서가 뒤바뀌어도 이 테스트 자체는 통과한다 —
     * 직접 확인했다(운영 코드에서 release를 grantNextLeg 뒤로 옮기고 돌려봤더니 이 테스트는
     * 그대로 통과, 바로 아래 {@code release_before_request_순서_...}만 실패했다). 이유는
     * 순차 실행에선 로봇1이 늦게라도 결국 반납하고, 로봇2가 그 뒤에 실행되니 시점이 맞아
     * 떨어져 버리기 때문이다 — 진짜 동시성 교착은 두 로봇이 "동시에" 서로의 구간을 요구할
     * 때만 재현된다.
     *
     * <p>그래서 <b>순서 자체를 못박는 건 아래 {@code release_before_request_순서} 테스트</b>고,
     * 이 테스트는 그 위에 얹는 종단 간 확인(교차 의존이 있어도 재시도 패스를 거쳐 결국 둘 다
     * 완료된다)이다 — 그 자체로 가치가 있지만 "교착 방지"의 유일한 근거로 삼지 않는다.
     */
    @Test
    void hold_and_wait_시나리오_두_로봇이_서로의_구간을_필요로_해도_결국_둘_다_진행한다() {
        // 로봇1은 SEG-1을 쥔 채 다음 레그에서 SEG-2가 필요하고,
        // 로봇2는 SEG-2를 쥔 채 다음 레그에서 SEG-1이 필요하다 — 실제 사고와 같은 교차 의존.
        FleetOrder order1 = inProgressOrder("O-1", ROBOT_1, SEG_HELD_BY_ROBOT_1);
        FleetOrder order2 = inProgressOrder("O-2", ROBOT_2, SEG_HELD_BY_ROBOT_2);

        when(laneGraph.planByNode(any(), any()))
                .thenReturn(new LaneGraph.RoutePlan(List.of(new double[]{1, 1}), List.of(SEG_HELD_BY_ROBOT_2), 1.0))
                .thenReturn(new LaneGraph.RoutePlan(List.of(new double[]{2, 2}), List.of(SEG_HELD_BY_ROBOT_1), 1.0));

        // grantPendingNextLegs()가 재시도 대상을 이걸로 훑는다 — 실제 리포지토리라면
        // EXECUTING 상태 쿼리 한 번으로 나올 결과를 그대로 흉내낸다.
        when(orderRepository.findByStatusOrderByPriorityDescIdAsc(com.pixelfleet.order.domain.OrderStatus.EXECUTING))
                .thenReturn(List.of(order1, order2));

        // 1) 로봇1이 픽업 완료를 보고한다.
        orderService.markStepDone("O-1", 0);

        // release-before-request가 지켜졌다면: SEG-1은 이미 반납됐고, 로봇1은 SEG-2가
        // 아직 로봇2 소유라 다음 레그를 못 잡아 대기한다(이건 정상 — 버그가 아니다).
        assertThat(trafficController.snapshot()).doesNotContainKey(SEG_HELD_BY_ROBOT_1);
        verify(robotCommandPublisher, never()).sendGoto(eq("AMR-01"), any(), eq(1), any(), anyBoolean(), anyBoolean(), any());

        // 2) 로봇2가 픽업 완료를 보고한다 — SEG-2를 반납하고 SEG-1(로봇1이 막 반납한)을 잡는다.
        orderService.markStepDone("O-2", 0);

        assertThat(trafficController.snapshot()).doesNotContainKey(SEG_HELD_BY_ROBOT_2);
        verify(robotCommandPublisher).sendGoto(eq("AMR-02"), eq("O-2"), eq(1), any(), anyBoolean(), anyBoolean(), any());

        // 3) 재시도 패스 — 이제 SEG-2가 풀렸으니 로봇1도 마저 잡는다.
        orderService.grantPendingNextLegs();

        verify(robotCommandPublisher).sendGoto(eq("AMR-01"), eq("O-1"), eq(1), any(), anyBoolean(), anyBoolean(), any());

        assertThat(order1.getCurrentStepIndex()).isEqualTo(1);
        assertThat(order2.getCurrentStepIndex()).isEqualTo(1);
    }

    /**
     * <b>hold-and-wait 교착의 실제 회귀 가드는 이 테스트다.</b> {@code release}가
     * {@code tryReserve}보다 항상 먼저 호출되는지를 InOrder로 직접 확인한다 — 값이 아니라
     * "먼저 놓고 나서 잡는다"는 규율 자체를 본다. 직접 검증했다: 운영 코드에서 release를
     * grantNextLeg 뒤로 옮겨 봤더니 이 테스트만 정확히 실패했다(위 시나리오 테스트는
     * 순차 실행이라 통과해버림 — 그 테스트의 Javadoc 참고).
     */
    @Test
    void release_before_request_순서_다음_레그_예약_전에_반드시_먼저_반납한다() {
        TrafficController spyTraffic = mock(TrafficController.class);
        when(spyTraffic.tryReserve(any(), any())).thenReturn(true);

        FleetOrderRepository repo = mock(FleetOrderRepository.class);
        RobotService robots = mock(RobotService.class);
        when(robots.findAll()).thenReturn(List.of(robot(ROBOT_1, "AMR-01")));
        LaneGraph graph = mock(LaneGraph.class);
        when(graph.nodePosition(anyString())).thenReturn(new double[]{0, 0});
        when(graph.planByNode(any(), any())).thenReturn(
                new LaneGraph.RoutePlan(List.of(new double[]{1, 1}), List.of("SEG-X"), 1.0));

        OrderService service = new OrderService(
                repo, robots, mock(FleetEventService.class), mock(RobotCommandPublisher.class),
                mock(AssignmentPolicy.class), graph, spyTraffic, mock(LocationRegistry.class),
                mock(ApplicationEventPublisher.class), 12);

        FleetOrder order = new FleetOrder("O-ORDER", null, 1, true, (short) 1);
        order.addStep("PICKUP", true, false);
        order.addStep("DROPOFF", false, true);
        order.assignTo(ROBOT_1);
        order.start();
        when(repo.findByOrderCode("O-ORDER")).thenReturn(Optional.of(order));

        service.markStepDone("O-ORDER", 0);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(spyTraffic);
        inOrder.verify(spyTraffic).release(ROBOT_1);
        inOrder.verify(spyTraffic).tryReserve(eq(ROBOT_1), anyList());
    }

    @Test
    void 다음_스텝_오배달_회귀_같은_스텝_완료가_중복_도착해도_두_번_진행하지_않는다() {
        // MQTT는 최소 1회 전달이라 같은 step-done이 두 번 올 수 있다(실제로 겪었다).
        FleetOrder order = inProgressOrder("O-DUP", ROBOT_1, SEG_HELD_BY_ROBOT_1);
        when(laneGraph.planByNode(any(), any())).thenReturn(
                new LaneGraph.RoutePlan(List.of(new double[]{1, 1}), List.of("SEG-NEXT"), 1.0));

        orderService.markStepDone("O-DUP", 0); // 정상 진행 — step1로.
        assertThat(order.getCurrentStepIndex()).isEqualTo(1);
        verify(robotCommandPublisher, times(1))
                .sendGoto(anyString(), eq("O-DUP"), eq(1), any(), anyBoolean(), anyBoolean(), any());

        orderService.markStepDone("O-DUP", 0); // 유실 재전송으로 같은 step 0 완료가 또 온다.

        // 이미 지나간 step 0 완료를 다시 처리해 step1을 또 예약하면 안 된다.
        verify(robotCommandPublisher, times(1))
                .sendGoto(anyString(), eq("O-DUP"), eq(1), any(), anyBoolean(), anyBoolean(), any());
        assertThat(order.getCurrentStepIndex()).isEqualTo(1);
    }

    @Test
    void 다음_스텝_오배달_회귀_아직_실행중인_스텝의_완료로_착각한_보고는_무시한다() {
        // 로봇이 아직 step0(접근 레그)을 달리는 중인데, 상태만 보고 다음 레그를 내주면
        // 실제로 그 레그를 달리는 로봇이 아닌 엉뚱한 처리로 이어질 수 있다 — leg2 오배달의
        // 근본 원인이었다. currentStepIndex와 일치하지 않는 완료 보고는 조용히 무시돼야 한다.
        FleetOrder order = inProgressOrder("O-STALE", ROBOT_1, SEG_HELD_BY_ROBOT_1);

        orderService.markStepDone("O-STALE", 1); // 아직 안 온 스텝의 완료 보고(순서 꼬임 가정).

        assertThat(order.getCurrentStepIndex()).isEqualTo(0); // 진행 안 됨.
        verify(robotCommandPublisher, never()).sendGoto(anyString(), any(), anyInt(), any(), anyBoolean(), anyBoolean(), any());
        assertThat(trafficController.snapshot()).containsKey(SEG_HELD_BY_ROBOT_1); // 구간도 그대로 쥔 채.
    }

    /** 스텝 완료 시 구간을 넘겨받은 로봇 코드로 정확히 전달되는지 — captor로 인자 자체를 본다. */
    @Test
    void 다음_레그_명령은_해당_주문에_배정된_로봇에게만_간다() {
        FleetOrder order = inProgressOrder("O-TARGET", ROBOT_2, SEG_HELD_BY_ROBOT_2);
        when(laneGraph.planByNode(any(), any())).thenReturn(
                new LaneGraph.RoutePlan(List.of(new double[]{5, 5}), List.of("SEG-FREE"), 1.0));

        orderService.markStepDone("O-TARGET", 0);

        ArgumentCaptor<String> robotCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(robotCommandPublisher).sendGoto(robotCodeCaptor.capture(), eq("O-TARGET"), eq(1),
                any(), anyBoolean(), anyBoolean(), any());
        assertThat(robotCodeCaptor.getValue()).isEqualTo("AMR-02");
    }
}
