package com.pixelfleet.sim.map;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공장 평면도(160 × 26) — 건물 5채. 통로는 둘이고, <b>창고동·생산동·품질동 세 건물을
 * 관통한다</b>(신관 두 채는 통로 뒤에 별도 체인으로 이어진다 — 아래 "신관" 참고).
 *
 * <pre>
 *   창고동(1~41)                   생산동(45~77)                  품질동(81~89)
 *   [렉]  [렉]  [렉]           [CNC-01][CNC-02][CNC-03][MCT-01]
 *   ○입고   ○도크1               ○A1    ○A2    ○A3    ○A4          ○QC-OUT(판정 출고)
 *   ══════════════════════ 상단 통로 (y=9) ══════════════════════════════
 *   [렉] ○피킹존
 *   ══════════════════════ 하단 통로 (y=18) ═════════════════════════════
 *   [렉] ○출하 ○도크2            ○B1    ○B2    ○B3    ○B4          ○QC-IN(검사 입고)
 *                             [ASM-01][ASM-02][INS-01][PKG-01]
 * </pre>
 *
 * <p><b>왜 통로가 건물을 관통하나.</b> 경로 규칙(수직→통로→수평→수직)이 고정이라, 통로가
 * 벽을 지나는 자리를 출입구로 삼아야 로봇이 벽을 뚫지 않는다. 모든 노드는 커넥터 x 위,
 * 통로 밖에 있다. 창고동 내부 연결로는 4·17·30 — 렉 27기(3열×3행×3개 층)가 그 사이에 선다.
 * 창고동이 생산동(32)보다 넓은 최대 건물이다(V16 — 폭 40).
 *
 * <p>물류 흐름: 창고동(자재) → 생산동(가공) → <b>품질동(전수 검사)</b> →
 * 합격이면 창고동 입고 / 불합격이면 생산동 재작업.
 *
 * <p><b>신관(BLDG-A/B, x=95~154)</b>은 통로 체인이 아니라 QC-OUT에서 시작하는 별도 체인으로
 * 이어진다(GATE-WH-A → MACH-1/2 → GATE-A-B → ASM-1 → LOGI-1). 시뮬레이션 활동은 없고,
 * 좌표 정합 테스트가 요구해서 여기 있다(신관 자체엔 아직 아무도 안 돌아다닌다).
 *
 * <p><b>좌표의 주인은 pixel-factory다</b>(layout_nodes / layout_settings). 여기는 그 값을
 * 받아 오지 않고 자기 복사본을 갖는다 — 시뮬레이터는 물리 세계를 흉내내는 쪽이라 실제 설비처럼
 * 서버가 알려주는 대로 위치를 바꾸지 않아야 하고, 서버가 죽어도 계속 돌아야 한다.
 *
 * <p>대신 {@code NodeMapLayoutConsistencyTest}가 서버 마스터(V16 마이그레이션 — 평면도를
 * 다시 그리는 마이그레이션마다 이 경로도 함께 옮긴다, V9→V12→V15→V16)와 대조해 <b>어긋나면
 * 빌드를 깨뜨린다.</b> 런타임 의존을 만들지 않으면서 조용한 불일치를 막는 방법이다.
 * 좌표를 바꿀 일이 있으면 마스터를 고치고 여기를 맞춘다(순서가 반대면 테스트가 잡아 준다).
 */
@Component
public class NodeMap {

    /** 평면도 가로. 서버 마스터(layout_settings.width)와 같아야 한다 — 대조 테스트가 확인한다. */
    public static final double MAX_X = 160.0;
    /** 평면도 세로. 서버 마스터(layout_settings.height)와 같아야 한다. */
    public static final double MAX_Y = 26.0;

    private static final Map<String, double[]> NODES = Map.ofEntries(
            // 창고동 1층 — 도크는 그대로(연결로 4는 V16에서도 안 움직인다)
            Map.entry("WH-DOCK-1", new double[]{4, 3}),
            Map.entry("WH-DOCK-2", new double[]{4, 5}),
            Map.entry("WH-DOCK-3", new double[]{4, 21}),
            Map.entry("WH-DOCK-4", new double[]{4, 23}),
            Map.entry("WH-RECV", new double[]{17, 6}),
            Map.entry("WH-PICK", new double[]{17, 13}),
            Map.entry("WH-SHIP", new double[]{30, 21}),
            Map.entry("WH-ELEV-1F", new double[]{30, 13}),
            // 창고동 2·3층 — **1층과 좌표가 겹친다**(샤프트·베이가 수직으로 같은 자리다).
            // 지상 로봇은 여기 오지 않는다(층을 오가지 못한다). 층별 AMR이 생기면 쓰인다.
            Map.entry("WH-DOCK-2F", new double[]{4, 3}),
            Map.entry("WH-2F-P1", new double[]{17, 6}),
            Map.entry("WH-2F-P2", new double[]{17, 13}),
            Map.entry("WH-ELEV-2F", new double[]{30, 13}),
            Map.entry("WH-DOCK-3F", new double[]{4, 3}),
            Map.entry("WH-3F-P1", new double[]{17, 6}),
            Map.entry("WH-3F-P2", new double[]{17, 13}),
            Map.entry("WH-ELEV-3F", new double[]{30, 13}),
            // 생산동 (V16에서 창고동이 다시 넓어진 만큼 +10 — 균일 이동이라 내부 상대거리는 그대로)
            Map.entry("PROD-A1", new double[]{49, 6}),
            Map.entry("PROD-A2", new double[]{56, 6}),
            Map.entry("PROD-A3", new double[]{63, 6}),
            Map.entry("PROD-A4", new double[]{70, 6}),
            Map.entry("PROD-B1", new double[]{49, 21}),
            Map.entry("PROD-B2", new double[]{56, 21}),
            Map.entry("PROD-B3", new double[]{63, 21}),
            Map.entry("PROD-B4", new double[]{70, 21}),
            // 품질동 (+10)
            Map.entry("QC-IN", new double[]{84, 21}),
            Map.entry("QC-OUT", new double[]{84, 6}),
            // 통로·연결로 교차점(JUNCTION) — V13에서 처음 생겼을 땐 대조 테스트가 V12만 읽어서
            // 검사 대상이 아니었다. V15로 정본 파일이 바뀌며(레이아웃을 다시 그리는 마이그레이션
            // 이라 노드·엣지를 한 파일에 전부 다시 선언해야 한다) 교차점도 같이 검사 대상이
            // 됐다 — robot-sim은 이 노드들을 실제로 쓰지 않지만(경로는 fleet이 계산해 준다),
            // "서버 마스터의 모든 노드가 일치해야 한다"는 테스트를 통과하려면 여기도 있어야 한다.
            Map.entry("JCT-4-U", new double[]{4, 9}),
            Map.entry("JCT-4-L", new double[]{4, 18}),
            Map.entry("JCT-9-U", new double[]{17, 9}),
            Map.entry("JCT-9-L", new double[]{17, 18}),
            Map.entry("JCT-14-U", new double[]{30, 9}),
            Map.entry("JCT-14-L", new double[]{30, 18}),
            Map.entry("JCT-27-U", new double[]{49, 9}),
            Map.entry("JCT-27-L", new double[]{49, 18}),
            Map.entry("JCT-34-U", new double[]{56, 9}),
            Map.entry("JCT-34-L", new double[]{56, 18}),
            Map.entry("JCT-41-U", new double[]{63, 9}),
            Map.entry("JCT-41-L", new double[]{63, 18}),
            Map.entry("JCT-48-U", new double[]{70, 9}),
            Map.entry("JCT-48-L", new double[]{70, 18}),
            Map.entry("JCT-62-U", new double[]{84, 9}),
            Map.entry("JCT-62-L", new double[]{84, 18}),
            // 신관(BLDG-A/B, V14) — V16에서 품질동과 안 겹치도록 균일 +10로 같이 옮겼다.
            // 시뮬레이션 활동은 없지만 좌표 정합 테스트 대상이라 둔다.
            Map.entry("GATE-WH-A", new double[]{95, 6}),
            Map.entry("MACH-1", new double[]{105, 6}),
            Map.entry("MACH-2", new double[]{115, 6}),
            Map.entry("GATE-A-B", new double[]{122, 6}),
            Map.entry("ASM-1", new double[]{132, 6}),
            Map.entry("LOGI-1", new double[]{145, 6})
    );

    /**
     * 충전 베이 — <b>지상(1층)만</b>. 위층 베이는 좌표가 1층과 같아서 넣으면 "가장 가까운 도크"가
     * 층을 넘나드는 엉뚱한 답을 준다. 지상 로봇은 층을 오가지 못한다.
     */
    private static final List<String> DOCKS = List.of("WH-DOCK-1", "WH-DOCK-2", "WH-DOCK-3", "WH-DOCK-4");

    /**
     * 유휴 로봇이 순찰할 지점 — 도크는 충전 자리이지 목적지가 아니므로 제외한다.
     * <b>층별로 나눠 둔다</b> — 로봇은 층을 오가지 못하므로 남의 층 노드로 순찰하면 안 된다.
     */
    private static final Map<Integer, List<String>> ROAM_NODES_BY_FLOOR = Map.of(
            1, List.of(
                    "WH-RECV", "WH-PICK", "WH-SHIP", "WH-ELEV-1F",
                    "PROD-A1", "PROD-A2", "PROD-A3", "PROD-A4",
                    "PROD-B1", "PROD-B2", "PROD-B3", "PROD-B4",
                    "QC-IN", "QC-OUT"),
            2, List.of("WH-2F-P1", "WH-2F-P2", "WH-ELEV-2F"),
            3, List.of("WH-3F-P1", "WH-3F-P2", "WH-ELEV-3F"));

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

    public String randomRoamNode(java.util.random.RandomGenerator rng, int floor) {
        List<String> nodes = ROAM_NODES_BY_FLOOR.getOrDefault(floor, ROAM_NODES_BY_FLOOR.get(1));
        return nodes.get(rng.nextInt(nodes.size()));
    }

    /** 상단 통로 — A열 담당. control-service LaneGraph와 같아야 한다. */
    public static final double UPPER_AISLE_Y = 9.0;
    /** 하단 통로 — B열 담당. */
    public static final double LOWER_AISLE_Y = 18.0;
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
