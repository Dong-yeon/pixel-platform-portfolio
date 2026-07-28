package com.pixelfleet.location;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Server-side floor plan: node name -> 2D coordinates. Used by the assignment policy to
 * measure how far a robot is from a task's origin node.
 *
 * <p>IMPORTANT: these coordinates MUST match {@code robot-sim}'s NodeMap. Robot positions
 * arrive as telemetry in the simulator's coordinate space, so distance comparisons here
 * are only meaningful if both sides share the same map and the same unknown-node fallback.
 * TODO: unify the map (server-owned + published to robots, or a shared contract) to remove
 * this duplication. Tracked in docs/BACKLOG.md.
 */
@Component
public class LocationRegistry {

    private static final double MAX_X = 32.0;
    private static final double MAX_Y = 24.0;

    private static final Map<String, double[]> NODES = Map.of(
            "DOCK-1", new double[]{2, 2},
            "DOCK-2", new double[]{2, 22},
            "STATION-A", new double[]{12, 6},
            "STATION-B", new double[]{26, 9},
            "STATION-C", new double[]{19, 19},
            "WAREHOUSE", new double[]{30, 22}
    );

    public double[] resolve(String node) {
        double[] known = NODES.get(node);
        if (known != null) {
            return known.clone();
        }
        // Stable fallback identical to robot-sim's NodeMap: hash the name into map bounds.
        int h = Math.abs(node == null ? 0 : node.hashCode());
        double x = (h % 1000) / 1000.0 * MAX_X;
        double y = ((h / 1000) % 1000) / 1000.0 * MAX_Y;
        return new double[]{x, y};
    }
}
