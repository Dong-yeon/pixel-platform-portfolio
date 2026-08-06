package com.pixelfleet.task.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.robot.domain.RobotStatus;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.traffic.LaneGraph;
import com.pixelfleet.traffic.ObstacleStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * P20-5 회귀 검증 — 장애물이 있을 때 "직선 최근접"과 "실제 경로 비용"이 서로 다른 로봇을
 * 고르는지 확인한다. Spring 컨텍스트 없는 순수 단위 테스트다({@link LaneGraphTest} 참고).
 */
class GraphCostAwareAssignmentPolicyTest {

    private final LocationRegistry locations = new LocationRegistry("http://unused:0/api/layout");
    private final ObstacleStore obstacles = Mockito.mock(ObstacleStore.class);
    private final LaneGraph laneGraph = new LaneGraph(locations, obstacles);
    private final GraphCostAwareAssignmentPolicy graphCostPolicy =
            new GraphCostAwareAssignmentPolicy(locations, laneGraph);
    private final NearestBatteryAwareAssignmentPolicy nearestPolicy =
            new NearestBatteryAwareAssignmentPolicy(locations);

    @Test
    void 장애물이_있으면_직선최근접과_그래프비용_최적이_서로_다른_로봇을_고른다() {
        // 작업 출발지: PROD-B1(27,21). 후보 둘 다 생산동 상단(A열)에 있다.
        //   - AMR-NEAR = PROD-A1(27,6) — PROD-B1과 같은 연결로. 직선거리 15로 더 가깝다.
        //   - AMR-FAR  = PROD-A2(34,6) — 직선거리 √274≈16.55로 더 멀다.
        // 연결로 27의 중간 대역(JCT-27-U~JCT-27-L)을 막으면 AMR-NEAR는 연결로 34를 거쳐
        // 돌아가야 한다(비용 29) — 반면 AMR-FAR는 애초에 연결로 34 중간 대역을 거치므로
        // 이 장애물의 영향을 받지 않는다(비용 22). 그래서 실제로는 AMR-FAR가 더 싸다.
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("JCT-27-U", "JCT-27-L"))).thenReturn(true);

        FleetOrder order = new FleetOrder("P20-5-TEST", null, 1, true, (short) 1);
        order.addStep("PROD-B1", true, false);
        order.addStep("WH-RECV", false, true); // 목적지는 이 테스트와 무관, 스텝 2개만 있으면 됨

        RobotResponse near = robot(1L, "AMR-NEAR", 27, 6);
        RobotResponse far = robot(2L, "AMR-FAR", 34, 6);
        List<RobotResponse> candidates = List.of(near, far);

        // 직선거리 정책은 더 가까운 AMR-NEAR를 고른다 — 장애물을 모른다.
        assertThat(nearestPolicy.selectRobot(order, candidates))
                .contains(near);

        // 그래프 비용 정책은 실제로 더 싸게 먹히는 AMR-FAR를 고른다 — 장애물을 반영한다.
        assertThat(graphCostPolicy.selectRobot(order, candidates))
                .contains(far);
    }

    @Test
    void 장애물이_없으면_두_정책_다_직선최근접과_같은_로봇을_고른다() {
        // 같은 연결로 위(같은 x)라 장애물이 없으면 직선거리와 그래프 비용이 같은 순위를 낸다 —
        // 정책을 바꿔도 평소엔 결과가 달라지지 않는다는 걸 확인한다(회귀 없음).
        FleetOrder order = new FleetOrder("P20-5-TEST-2", null, 1, true, (short) 1);
        order.addStep("PROD-B1", true, false);
        order.addStep("WH-RECV", false, true);

        RobotResponse near = robot(1L, "AMR-NEAR", 27, 6);
        RobotResponse far = robot(2L, "AMR-FAR", 34, 6);
        List<RobotResponse> candidates = List.of(near, far);

        assertThat(nearestPolicy.selectRobot(order, candidates)).contains(near);
        assertThat(graphCostPolicy.selectRobot(order, candidates)).contains(near);
    }

    @Test
    void 배터리가_낮으면_그래프비용_정책도_후보에서_뺀다() {
        // 정책만 바뀌었지, 배터리 안전 기준(NearestBatteryAwareAssignmentPolicy.MIN_BATTERY_PERCENT)은
        // 그대로 지켜야 한다 — 충전 사각지대 방지 규칙이 정책 교체로 없어지면 안 된다.
        FleetOrder order = new FleetOrder("P20-5-TEST-3", null, 1, true, (short) 1);
        order.addStep("PROD-B1", true, false);
        order.addStep("WH-RECV", false, true);

        RobotResponse lowBattery = new RobotResponse(1L, "AMR-LOW", "1호기", RobotStatus.IDLE,
                10, 27, 6, false, (short) 1, LocalDateTime.now());

        assertThat(graphCostPolicy.selectRobot(order, List.of(lowBattery))).isEmpty();
    }

    private RobotResponse robot(long id, String code, double x, double y) {
        return new RobotResponse(id, code, code, RobotStatus.IDLE, 80, x, y, false, (short) 1,
                LocalDateTime.now());
    }
}
