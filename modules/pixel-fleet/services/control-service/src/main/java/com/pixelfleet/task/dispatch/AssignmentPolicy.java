package com.pixelfleet.task.dispatch;

import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.robot.dto.RobotResponse;
import java.util.List;
import java.util.Optional;

/**
 * Chooses which robot should execute an order, given the currently available robots (with
 * their live state). Kept as a strategy interface so the matching rule can evolve (nearest,
 * battery-aware, traffic-aware, ...) without touching the dispatch flow in OrderService.
 */
public interface AssignmentPolicy {

    /**
     * @return the robot to assign, or empty if none of the candidates is suitable
     *         (e.g. all too low on battery) — dispatch then waits for a better moment.
     */
    Optional<RobotResponse> selectRobot(FleetOrder order, List<RobotResponse> candidates);
}
