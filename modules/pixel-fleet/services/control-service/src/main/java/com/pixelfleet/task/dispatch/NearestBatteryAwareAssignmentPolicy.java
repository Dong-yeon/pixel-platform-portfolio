package com.pixelfleet.task.dispatch;

import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.robot.dto.RobotResponse;
import com.pixelfleet.task.domain.TransportTask;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default matching rule:
 * <ol>
 *   <li>Skip robots below {@link #MIN_BATTERY_PERCENT} — they should be charging, not
 *       picking up work (and the simulator will soon send them to a dock).</li>
 *   <li>Among the rest, pick the one closest to the task's origin node (least travel to
 *       pickup).</li>
 *   <li>Break ties by higher battery, so wear is spread toward the healthier robot.</li>
 * </ol>
 */
@Component
public class NearestBatteryAwareAssignmentPolicy implements AssignmentPolicy {

    /** A robot needs at least this much charge to be handed a new task. */
    static final int MIN_BATTERY_PERCENT = 25;

    private final LocationRegistry locations;

    public NearestBatteryAwareAssignmentPolicy(LocationRegistry locations) {
        this.locations = locations;
    }

    @Override
    public Optional<RobotResponse> selectRobot(TransportTask task, List<RobotResponse> candidates) {
        double[] origin = locations.resolve(task.getOriginNode());
        return candidates.stream()
                .filter(robot -> robot.batteryPercent() >= MIN_BATTERY_PERCENT)
                .min(Comparator
                        .comparingDouble((RobotResponse robot) -> distanceSquared(robot, origin))
                        .thenComparing(Comparator.comparingInt(RobotResponse::batteryPercent).reversed()));
    }

    private double distanceSquared(RobotResponse robot, double[] point) {
        double dx = robot.posX() - point[0];
        double dy = robot.posY() - point[1];
        return dx * dx + dy * dy;
    }
}
