package com.pixelfleet.sim.map;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A tiny fixed floor plan: named nodes with 2D coordinates. Tasks reference nodes by
 * name (originNode/destinationNode); the simulator resolves them to points here.
 * Unknown names get stable pseudo-coordinates so demo tasks with arbitrary nodes still run.
 */
@Component
public class NodeMap {

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

    private static final List<String> DOCKS = List.of("DOCK-1", "DOCK-2");
    private static final List<String> ROAM_NODES =
            List.of("STATION-A", "STATION-B", "STATION-C", "WAREHOUSE");

    public double[] resolve(String node) {
        double[] known = NODES.get(node);
        if (known != null) {
            return known.clone();
        }
        // Stable fallback: hash the name into the map bounds so the same node is always
        // the same place across ticks and runs.
        int h = Math.abs(node == null ? 0 : node.hashCode());
        double x = (h % 1000) / 1000.0 * MAX_X;
        double y = ((h / 1000) % 1000) / 1000.0 * MAX_Y;
        return new double[]{x, y};
    }

    public String nearestDock(double x, double y) {
        String best = DOCKS.get(0);
        double bestDist = Double.MAX_VALUE;
        for (String dock : DOCKS) {
            double[] p = NODES.get(dock);
            double d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (d < bestDist) {
                bestDist = d;
                best = dock;
            }
        }
        return best;
    }

    public String randomRoamNode(java.util.random.RandomGenerator rng) {
        return ROAM_NODES.get(rng.nextInt(ROAM_NODES.size()));
    }
}
