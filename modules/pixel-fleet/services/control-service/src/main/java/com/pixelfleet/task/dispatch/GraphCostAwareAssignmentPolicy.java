package com.pixelfleet.task.dispatch;

import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.robot.domain.RobotType;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.traffic.LaneGraph;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 직선거리 대신 <b>실제 주행 경로 비용</b>으로 후보를 고른다 (P20-5).
 *
 * <p><b>왜 필요한가.</b> {@link NearestBatteryAwareAssignmentPolicy}는 좌표 사이의 직선거리로
 * "가장 가까운" 로봇을 고른다. 통로가 둘뿐이고 장애물이 없을 땐 직선거리와 실제 이동거리의
 * 순위가 거의 항상 같아서 문제가 안 됐다. 그런데 P20-4로 엣지가 막힐 수 있게 되면서 상황이
 * 달라졌다 — 직선으로는 가장 가까운 로봇이 막힌 엣지 때문에 실제로는 훨씬 돌아가야 할 수
 * 있고, 직선으로는 더 먼 로봇이 뚫린 길로 가면 오히려 빠를 수 있다(설계 근거:
 * {@code docs/p20-layout-routing-design.md} D9 "주의" 항목).
 *
 * <p>후보 로봇 수가 적어(수십 대 수준) 후보마다 {@link LaneGraph#plan}(다익스트라)을 부르는
 * 비용은 무시할 만하다.
 *
 * <p>{@code dispatch.policy=graph-cost}로 켠다 — 기본은 여전히
 * {@link NearestBatteryAwareAssignmentPolicy}다(회귀 시 즉시 롤백 수단).
 */
@Component
@ConditionalOnProperty(name = "dispatch.policy", havingValue = "graph-cost")
public class GraphCostAwareAssignmentPolicy implements AssignmentPolicy {

    /** {@link NearestBatteryAwareAssignmentPolicy}와 같은 값 — 배차 정책만 바뀌지, 배터리 안전
     * 기준(로봇 자체 충전 임계치와의 사각지대 방지)은 그대로 지켜야 한다. */
    static final int MIN_BATTERY_PERCENT = 25;

    private final LocationRegistry locations;
    private final LaneGraph laneGraph;

    public GraphCostAwareAssignmentPolicy(LocationRegistry locations, LaneGraph laneGraph) {
        this.locations = locations;
        this.laneGraph = laneGraph;
    }

    @Override
    public Optional<RobotResponse> selectRobot(FleetOrder order, List<RobotResponse> candidates) {
        String locationNode = order.getSteps().get(0).getLocationNode();
        boolean rackOrigin = locations.isRackCode(locationNode);
        double[] origin = rackOrigin ? locations.rackApproachPoint(locationNode) : locations.resolve(locationNode);
        return candidates.stream()
                .filter(robot -> robot.floorNo() == order.getFloorNo())
                // P21: AGV 주문은 그 존 로봇만, AMR 주문은 AMR만(NearestBatteryAwareAssignmentPolicy와
                // 같은 필터 — 두 정책 모두 같은 배차 불변식을 지켜야 한다).
                .filter(robot -> robot.robotType() == order.getRobotType())
                .filter(robot -> order.getRobotType() != RobotType.AGV
                        || order.getZoneCode().equals(robot.zoneCode()))
                .filter(robot -> robot.batteryPercent() >= MIN_BATTERY_PERCENT)
                .min(Comparator
                        .comparingDouble((RobotResponse robot) -> routeCost(robot, origin, rackOrigin))
                        .thenComparing(Comparator.comparingInt(RobotResponse::batteryPercent).reversed()));
    }

    /**
     * 로봇의 현재 위치에서 작업 출발지까지의 비용. AMR은 지금 장애물 상황을 반영한 실제
     * 경로 비용({@link LaneGraph#plan}), AGV는 그래프에 올라가지 않으므로(D2) 직선거리다
     * — {@code LaneGraph}로 렉을 route하면 로봇 위치가 진입점(anchor)으로 잘못 편입될 위험이
     * 있다(설계 근거: docs/p21-warehouse-rack-feeder-design.md D2).
     */
    private double routeCost(RobotResponse robot, double[] origin, boolean rackOrigin) {
        if (rackOrigin) {
            double dx = robot.posX() - origin[0];
            double dy = robot.posY() - origin[1];
            return Math.hypot(dx, dy);
        }
        return laneGraph.plan(new double[]{robot.posX(), robot.posY()}, origin).cost();
    }
}
