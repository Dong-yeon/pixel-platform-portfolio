package com.pixelfleet.sim.map;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공장 평면도(44 × 24). 상단이 LINE-1 가공, 하단이 LINE-2 조립·검사이고
 * 가운데 통로로 AMR이 오간다.
 *
 * <pre>
 *   [CNC-01][CNC-02][CNC-03][MCT-01]      ← LINE-1 설비(대시보드가 그린다)
 *     ○A1     ○A2     ○A3     ○A4         ← 하역 지점
 *   [창고]          (통로)          [출하]
 *     ○B1     ○B2     ○B3     ○B4
 *   [ASM-01][ASM-02][INS-01][PKG-01]      ← LINE-2 설비
 * </pre>
 *
 * 좌표는 control-service의 LocationRegistry, 대시보드 types.ts와 반드시 일치해야 한다
 * (단일화는 플랫폼 BACKLOG 항목).
 */
@Component
public class NodeMap {

    private static final double MAX_X = 44.0;
    private static final double MAX_Y = 24.0;

    private static final Map<String, double[]> NODES = Map.ofEntries(
            Map.entry("DOCK-1", new double[]{3, 3}),
            Map.entry("DOCK-2", new double[]{3, 21}),
            Map.entry("WAREHOUSE", new double[]{3, 12}),
            Map.entry("STATION-A1", new double[]{11, 5.5}),
            Map.entry("STATION-A2", new double[]{18, 5.5}),
            Map.entry("STATION-A3", new double[]{25, 5.5}),
            Map.entry("STATION-A4", new double[]{32, 5.5}),
            Map.entry("STATION-B1", new double[]{11, 18.5}),
            Map.entry("STATION-B2", new double[]{18, 18.5}),
            Map.entry("STATION-B3", new double[]{25, 18.5}),
            Map.entry("STATION-B4", new double[]{32, 18.5}),
            Map.entry("SHIPPING", new double[]{41, 12})
    );

    private static final List<String> DOCKS = List.of("DOCK-1", "DOCK-2");

    /** 유휴 로봇이 순찰할 지점 — 도크는 충전 자리이지 목적지가 아니므로 제외한다. */
    private static final List<String> ROAM_NODES = List.of(
            "WAREHOUSE", "SHIPPING",
            "STATION-A1", "STATION-A2", "STATION-A3", "STATION-A4",
            "STATION-B1", "STATION-B2", "STATION-B3", "STATION-B4");

    public double[] resolve(String node) {
        double[] known = NODES.get(node);
        if (known != null) {
            return known.clone();
        }
        // 모르는 이름이어도 늘 같은 자리에 놓이도록 이름을 해시해 좌표를 만든다.
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
