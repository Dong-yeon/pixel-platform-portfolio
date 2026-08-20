package com.pixelfleet.task.dispatch;

import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.robot.domain.RobotType;
import com.pixelfleet.robot.dto.RobotResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 직선거리 기준 배차 (기본값). P20-5에서 그래프 비용 기준
 * {@link GraphCostAwareAssignmentPolicy}이 추가되며 {@code dispatch.policy}로
 * 전환 가능한 두 구현 중 하나가 됐다 — 기본을 이쪽으로 둔 이유는 회귀 시 즉시 롤백 수단이
 * 필요해서다(설계 근거: {@code docs/p20-layout-routing-design.md} D9).
 *
 * <p>Matching rule:
 * <ol>
 *   <li>다른 층 로봇은 후보에서 뺀다 — 로봇은 층을 오가지 못한다(엘리베이터는 화물용).
 *       거리로만 고르면 좌표가 겹치는 위층 로봇이 "가장 가깝다"고 뽑힌다.</li>
 *   <li>Skip robots below {@link #MIN_BATTERY_PERCENT} — they should be charging, not
 *       picking up work.</li>
 *   <li>Among the rest, pick the one closest to the task's origin node (least travel to
 *       pickup).</li>
 *   <li>Break ties by higher battery, so wear is spread toward the healthier robot.</li>
 * </ol>
 *
 * <p><b>지켜야 할 불변식: 로봇의 충전 복귀 기준 &gt; {@link #MIN_BATTERY_PERCENT}.</b>
 * 충전 결정은 로봇이 스스로 하는데(robot-sim {@code sim.low-battery-threshold}), 그 값이
 * 이 값보다 낮으면 사이에 사각지대가 생긴다 — 배차받기엔 낮고, 충전 가기엔 높고,
 * 유휴 상태에선 배터리가 닳지도 않아 로봇이 스스로 빠져나올 수 없다. 한 대씩 이 구간에
 * 빠지다 결국 함대 전체가 멈춘다(실제로 6대 전부 20~23%에서 멈춰 있었다).
 *
 * <p>두 값이 서로 다른 서비스에 있는 게 근본 원인이다. 충전도 서버가 배차하는 작업으로
 * 만들면 애초에 어긋날 수가 없어진다(백로그).
 */
@Component
@ConditionalOnProperty(name = "dispatch.policy", havingValue = "nearest", matchIfMissing = true)
public class NearestBatteryAwareAssignmentPolicy implements AssignmentPolicy {

    /** A robot needs at least this much charge to be handed a new task. */
    static final int MIN_BATTERY_PERCENT = 25;

    private final LocationRegistry locations;

    public NearestBatteryAwareAssignmentPolicy(LocationRegistry locations) {
        this.locations = locations;
    }

    @Override
    public Optional<RobotResponse> selectRobot(FleetOrder order, List<RobotResponse> candidates) {
        double[] origin = resolveOrigin(order);
        return candidates.stream()
                .filter(robot -> robot.floorNo() == order.getFloorNo())
                // P21: AGV 주문은 그 존 로봇만, AMR 주문은 AMR만 — 다른 로봇 종류는
                // 애초에 그 렉/레인망에 갈 수 없다(설계 근거: docs/p21-warehouse-rack-feeder-design.md D6).
                .filter(robot -> robot.robotType() == order.getRobotType())
                .filter(robot -> order.getRobotType() != RobotType.AGV
                        || order.getZoneCode().equals(robot.zoneCode()))
                .filter(robot -> robot.batteryPercent() >= MIN_BATTERY_PERCENT)
                .min(Comparator
                        .comparingDouble((RobotResponse robot) -> distanceSquared(robot, origin))
                        .thenComparing(Comparator.comparingInt(RobotResponse::batteryPercent).reversed()));
    }

    /** 주문 step0의 출발 좌표 — 렉 기원 주문(P21)이면 렉 접근점, 아니면 평소처럼 노드 좌표. */
    private double[] resolveOrigin(FleetOrder order) {
        String locationNode = order.getSteps().get(0).getLocationNode();
        return locations.isRackCode(locationNode)
                ? locations.rackApproachPoint(locationNode)
                : locations.resolve(locationNode);
    }

    private double distanceSquared(RobotResponse robot, double[] point) {
        double dx = robot.posX() - point[0];
        double dy = robot.posY() - point[1];
        return dx * dx + dy * dy;
    }
}
