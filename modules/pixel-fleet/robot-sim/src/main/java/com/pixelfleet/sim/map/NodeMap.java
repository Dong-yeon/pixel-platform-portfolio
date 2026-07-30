package com.pixelfleet.sim.map;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공장 평면도(44 × 24). 통로는 둘이다 — 라인마다 하나씩.
 *
 * <pre>
 *   [CNC-01][CNC-02][CNC-03][MCT-01]      ← LINE-1 설비(대시보드가 그린다)
 *     ○A1     ○A2     ○A3     ○A4         ← 하역 지점
 *   ══════════ 상단 통로 (y=8.5) ══════════
 *   [창고]                        [출하]
 *   ══════════ 하단 통로 (y=15.5) ═════════
 *     ○B1     ○B2     ○B3     ○B4
 *   [ASM-01][ASM-02][INS-01][PKG-01]      ← LINE-2 설비
 * </pre>
 *
 * <p><b>좌표의 주인은 pixel-factory다</b>(layout_nodes / layout_settings). 여기는 그 값을
 * 받아 오지 않고 자기 복사본을 갖는다 — 시뮬레이터는 물리 세계를 흉내내는 쪽이라 실제 설비처럼
 * 서버가 알려주는 대로 위치를 바꾸지 않아야 하고, 서버가 죽어도 계속 돌아야 한다.
 *
 * <p>대신 {@code NodeMapLayoutConsistencyTest}가 서버 마스터(V7 마이그레이션)와 대조해
 * <b>어긋나면 빌드를 깨뜨린다.</b> 런타임 의존을 만들지 않으면서 조용한 불일치를 막는 방법이다.
 * 좌표를 바꿀 일이 있으면 마스터를 고치고 여기를 맞춘다(순서가 반대면 테스트가 잡아 준다).
 */
@Component
public class NodeMap {

    /** 평면도 가로. 서버 마스터(layout_settings.width)와 같아야 한다 — 대조 테스트가 확인한다. */
    public static final double MAX_X = 44.0;
    /** 평면도 세로. 서버 마스터(layout_settings.height)와 같아야 한다. */
    public static final double MAX_Y = 24.0;

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

    /** 이 시뮬레이터가 아는 노드 코드들. 서버 마스터와 대조하는 테스트가 쓴다. */
    public java.util.Set<String> knownNodeCodes() {
        return NODES.keySet();
    }

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

    /** 상단 통로 — LINE-1(A열) 담당. control-service LaneGraph와 같아야 한다. */
    public static final double UPPER_AISLE_Y = 8.5;
    /** 하단 통로 — LINE-2(B열) 담당. */
    public static final double LOWER_AISLE_Y = 15.5;
    private static final double MID_Y = (UPPER_AISLE_Y + LOWER_AISLE_Y) / 2;

    /**
     * 두 지점 사이의 주행 경로를 웨이포인트로 만든다.
     *
     * <p>운송 작업의 경로는 <b>서버가 계산해서 내려준다</b>(구간 점유 통제 때문).
     * 이 메서드는 서버 지시가 없는 이동 — 충전 복귀나 하위 호환 GOTO — 에만 쓰인다.
     * 그래도 통로를 따라야 설비를 관통하지 않으므로 서버와 같은 규칙을 유지한다.
     *
     * <p>목적지가 속한 쪽 통로를 탄다: 위쪽이면 상단 통로, 아래쪽이면 하단 통로.
     */
    public java.util.List<double[]> route(double[] from, double[] to) {
        // 세로로 거의 같은 줄이면 통로를 경유할 필요가 없다.
        if (Math.abs(from[0] - to[0]) < 0.6) {
            return java.util.List.of(to.clone());
        }
        double aisleY = to[1] < MID_Y ? UPPER_AISLE_Y : LOWER_AISLE_Y;
        return java.util.List.of(
                new double[]{from[0], aisleY},
                new double[]{to[0], aisleY},
                to.clone());
    }
}
