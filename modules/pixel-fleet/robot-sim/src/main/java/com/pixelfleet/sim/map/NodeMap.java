package com.pixelfleet.sim.map;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공장 평면도(173 × 26) — 건물 5채. 통로는 둘이고, <b>창고동·생산동·품질동 세 건물을
 * 관통한다</b>(신관 두 채는 통로 뒤에 별도 체인으로 이어진다 — 아래 "신관" 참고).
 *
 * <pre>
 *   창고동(1~54, AGV 전용 1층)      게이트   생산동(58~90)                 품질동(94~102)
 *   [렉] [렉] [렉] [렉]               ╫    [CNC-01][CNC-02][CNC-03][MCT-01]
 *   ○입고 ○도크(AGV) ○보조출하          ╫    ○A1 ○도크(AMR) ○A2 ○A3 ○A4     ○QC-OUT(판정 출고)
 *   ═══════════════════ 상단 통로 (y=9) ═╫══════════════════════════════════
 *   [렉] ○피킹존                     ╫
 *   ═══════════════════ 하단 통로 (y=18)═╫══════════════════════════════════
 *   [렉] ○출하                       ╫    ○B1    ○B2    ○B3    ○B4          ○QC-IN(검사 입고)
 *                                         [ASM-01][ASM-02][INS-01][PKG-01]
 * </pre>
 *
 * <p><b>P22: AMR은 창고동에 들어오지 않는다.</b> 창고동(WH) 1층 안쪽은 AGV(옛 이름: 랙 피더)
 * 전용이고, 그 밖은 AMR 전용이다 — 경계는 {@code WH-GATE-U}/{@code WH-GATE-L} 두 노드뿐이다.
 * 창고동 2·3층은 범위 밖이라 좌표·역할이 그대로다(계속 AMR, P21 D10).
 *
 * <p><b>왜 통로가 건물을 관통하나.</b> 경로 규칙(수직→통로→수평→수직)이 고정이라, 통로가
 * 벽을 지나는 자리를 출입구로 삼아야 로봇이 벽을 뚫지 않는다. 모든 노드는 커넥터 x 위,
 * 통로 밖에 있다. 창고동 내부 연결로는 4·17·30·41(4번째는 P30 신설) — 렉 72기(4열×3행×3개
 * 층)가 그 사이에 선다. 창고동이 생산동(32)보다 넓은 최대 건물이다(V16 — P30으로 53까지 증설).
 *
 * <p><b>P30: 4번째 베이 + 순환 경로.</b> 옛 벽 자리(x=41)가 연결로(JCT-19)가 되고, 그 뒤에
 * 새 베이(렉 18기)가 생겼다. 렉은 여전히 {@code LaneGraph}를 안 타지만(P21 D2), 그 베이 안의
 * {@code WH-SHIP-2}(보조 출하장)는 명명 노드라 실제 주문이 그 구간을 지나간다 — 이게
 * "실제로 도는" 순환 경로다(설계 근거: docs/p30-warehouse-fourth-bay-design.md D3).
 *
 * <p>물류 흐름: 창고동(자재) → 생산동(가공) → <b>품질동(전수 검사)</b> →
 * 합격이면 창고동 입고 / 불합격이면 생산동 재작업.
 *
 * <p><b>신관(BLDG-A/B)</b>은 통로 체인이 아니라 QC-OUT에서 시작하는 별도 체인으로
 * 이어진다(GATE-WH-A → MACH-1/2 → GATE-A-B → ASM-1 → LOGI-1). 시뮬레이션 활동은 없고,
 * 좌표 정합 테스트가 요구해서 여기 있다(신관 자체엔 아직 아무도 안 돌아다닌다).
 *
 * <p><b>좌표의 주인은 pixel-factory다</b>(layout_nodes / layout_settings). 여기는 그 값을
 * 받아 오지 않고 자기 복사본을 갖는다 — 시뮬레이터는 물리 세계를 흉내내는 쪽이라 실제 설비처럼
 * 서버가 알려주는 대로 위치를 바꾸지 않아야 하고, 서버가 죽어도 계속 돌아야 한다.
 *
 * <p>대신 {@code NodeMapLayoutConsistencyTest}가 서버 마스터(V21 마이그레이션 — 평면도를
 * 다시 그리는 마이그레이션마다 이 경로도 함께 옮긴다, V9→V12→V15→V16→V17→V20→V21)와 대조해
 * <b>어긋나면 빌드를 깨뜨린다.</b> 런타임 의존을 만들지 않으면서 조용한 불일치를 막는 방법이다.
 * 좌표를 바꿀 일이 있으면 마스터를 고치고 여기를 맞춘다(순서가 반대면 테스트가 잡아 준다).
 */
@Component
public class NodeMap {

    /** 평면도 가로. 서버 마스터(layout_settings.width)와 같아야 한다 — 대조 테스트가 확인한다. */
    public static final double MAX_X = 173.0;
    /** 평면도 세로. 서버 마스터(layout_settings.height)와 같아야 한다. */
    public static final double MAX_Y = 26.0;

    private static final Map<String, double[]> NODES = Map.ofEntries(
            // 창고동 1층 — 도크 4개가 좌하단 한 코너로 모였다(P29, 참고 이미지의 CP 클러스터).
            Map.entry("WH-DOCK-1", new double[]{4, 19}),
            Map.entry("WH-DOCK-2", new double[]{4, 20.5}),
            Map.entry("WH-DOCK-3", new double[]{4, 21}),
            Map.entry("WH-DOCK-4", new double[]{4, 23}),
            Map.entry("WH-RECV", new double[]{17, 6}),
            Map.entry("WH-PICK", new double[]{17, 13}),
            Map.entry("WH-SHIP", new double[]{30, 21}),
            Map.entry("WH-ELEV-1F", new double[]{30, 13}),
            // P30: 4번째 베이의 보조 출하장 — 순환 경로에 실제 주문을 태우는 진짜 목적지.
            Map.entry("WH-SHIP-2", new double[]{41, 21}),
            // 창고동 2·3층 — **1층과 좌표가 겹친다**(샤프트·베이가 수직으로 같은 자리다).
            // P22 범위 밖 — 계속 AMR이 담당한다(P21 D10 그대로). 도크는 1층과 같은 코너로(P29).
            Map.entry("WH-DOCK-2F", new double[]{4, 21}),
            Map.entry("WH-2F-P1", new double[]{17, 6}),
            Map.entry("WH-2F-P2", new double[]{17, 13}),
            Map.entry("WH-ELEV-2F", new double[]{30, 13}),
            Map.entry("WH-DOCK-3F", new double[]{4, 21}),
            Map.entry("WH-3F-P1", new double[]{17, 6}),
            Map.entry("WH-3F-P2", new double[]{17, 13}),
            Map.entry("WH-ELEV-3F", new double[]{30, 13}),
            // P22: AMR ↔ AGV 게이트 — 창고동 벽 밖, 생산동 벽 앞의 중립 지대. P30으로
            // 창고동 폭이 늘어난 만큼(+13) 균일 이동(x=43→56).
            Map.entry("WH-GATE-U", new double[]{56, 9}),
            Map.entry("WH-GATE-L", new double[]{56, 18}),
            // P22: 생산동 쪽 AMR 충전 베이 — P30으로 균일 +13.
            Map.entry("PROD-DOCK-1", new double[]{62, 3}),
            Map.entry("PROD-DOCK-2", new double[]{62, 5}),
            Map.entry("PROD-DOCK-3", new double[]{62, 21}),
            Map.entry("PROD-DOCK-4", new double[]{62, 23}),
            // 생산동 (P30 — 창고동 4번째 베이 신설로 +13, 균일 이동이라 내부 상대거리는 그대로)
            Map.entry("PROD-A1", new double[]{62, 6}),
            Map.entry("PROD-A2", new double[]{69, 6}),
            Map.entry("PROD-A3", new double[]{76, 6}),
            Map.entry("PROD-A4", new double[]{83, 6}),
            Map.entry("PROD-B1", new double[]{62, 21}),
            Map.entry("PROD-B2", new double[]{69, 21}),
            Map.entry("PROD-B3", new double[]{76, 21}),
            Map.entry("PROD-B4", new double[]{83, 21}),
            // 품질동 (+13)
            Map.entry("QC-IN", new double[]{97, 21}),
            Map.entry("QC-OUT", new double[]{97, 6}),
            // 통로·연결로 교차점(JUNCTION) — robot-sim은 이 노드들을 실제로 쓰지 않지만
            // (경로는 fleet이 계산해 준다), "서버 마스터의 모든 노드가 일치해야 한다"는
            // 테스트를 통과하려면 여기도 있어야 한다.
            Map.entry("JCT-4-U", new double[]{4, 9}),
            Map.entry("JCT-4-L", new double[]{4, 18}),
            Map.entry("JCT-9-U", new double[]{17, 9}),
            Map.entry("JCT-9-L", new double[]{17, 18}),
            Map.entry("JCT-14-U", new double[]{30, 9}),
            Map.entry("JCT-14-L", new double[]{30, 18}),
            // P30: 4번째 베이 연결로(옛 벽 자리, x=41).
            Map.entry("JCT-19-U", new double[]{41, 9}),
            Map.entry("JCT-19-L", new double[]{41, 18}),
            Map.entry("JCT-27-U", new double[]{62, 9}),
            Map.entry("JCT-27-L", new double[]{62, 18}),
            Map.entry("JCT-34-U", new double[]{69, 9}),
            Map.entry("JCT-34-L", new double[]{69, 18}),
            Map.entry("JCT-41-U", new double[]{76, 9}),
            Map.entry("JCT-41-L", new double[]{76, 18}),
            Map.entry("JCT-48-U", new double[]{83, 9}),
            Map.entry("JCT-48-L", new double[]{83, 18}),
            Map.entry("JCT-62-U", new double[]{97, 9}),
            Map.entry("JCT-62-L", new double[]{97, 18}),
            // 신관(BLDG-A/B, V14) — P30으로 품질동과 나란히 균일 +13.
            // 시뮬레이션 활동은 없지만 좌표 정합 테스트 대상이라 둔다.
            Map.entry("GATE-WH-A", new double[]{108, 6}),
            Map.entry("MACH-1", new double[]{118, 6}),
            Map.entry("MACH-2", new double[]{128, 6}),
            Map.entry("GATE-A-B", new double[]{135, 6}),
            Map.entry("ASM-1", new double[]{145, 6}),
            Map.entry("LOGI-1", new double[]{158, 6})
    );

    /**
     * 충전 베이 — <b>지상(1층)만</b>, 로봇 종류별로 갈린다(P22). AGV는 창고동 도크,
     * AMR은 생산동 도크. 위층 베이는 좌표가 1층과 같아서 넣지 않는다(로봇은 층을 못 넘는다).
     */
    private static final List<String> DOCKS_AGV = List.of("WH-DOCK-1", "WH-DOCK-2", "WH-DOCK-3", "WH-DOCK-4");
    private static final List<String> DOCKS_AMR = List.of(
            "PROD-DOCK-1", "PROD-DOCK-2", "PROD-DOCK-3", "PROD-DOCK-4");

    /**
     * 유휴 로봇이 순찰할 지점 — 도크는 충전 자리이지 목적지가 아니므로 제외한다.
     * <b>층별로 나눠 둔다</b> — 로봇은 층을 오가지 못하므로 남의 층 노드로 순찰하면 안 된다.
     * 1층 AMR은 이제 창고동 안(WH-RECV 등)을 순찰하지 않는다(P22) — 생산동·품질동만 돈다.
     */
    private static final Map<Integer, List<String>> ROAM_NODES_BY_FLOOR = Map.of(
            1, List.of(
                    "PROD-A1", "PROD-A2", "PROD-A3", "PROD-A4",
                    "PROD-B1", "PROD-B2", "PROD-B3", "PROD-B4",
                    "QC-IN", "QC-OUT"),
            2, List.of("WH-2F-P1", "WH-2F-P2", "WH-ELEV-2F"),
            3, List.of("WH-3F-P1", "WH-3F-P2", "WH-ELEV-3F"));

    /** 1층 AGV 전용 순찰 지점(P22) — 창고동 안쪽만. WH-SHIP-2는 P30에서 4번째 베이에 추가됐다. */
    private static final List<String> ROAM_NODES_AGV_1F = List.of(
            "WH-RECV", "WH-PICK", "WH-SHIP", "WH-SHIP-2", "WH-ELEV-1F");

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

    /** 지상 충전 베이 중 가장 가까운 곳. {@code agv}가 참이면 창고동 도크, 거짓이면 생산동 도크. */
    public String nearestDock(double x, double y, boolean agv) {
        List<String> docks = agv ? DOCKS_AGV : DOCKS_AMR;
        String best = docks.get(0);
        double bestDist = Double.MAX_VALUE;
        for (String dock : docks) {
            double[] p = NODES.get(dock);
            double d = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y);
            if (d < bestDist) {
                bestDist = d;
                best = dock;
            }
        }
        return best;
    }

    public String randomRoamNode(java.util.random.RandomGenerator rng, int floor, boolean agv) {
        if (floor == 1 && agv) {
            return ROAM_NODES_AGV_1F.get(rng.nextInt(ROAM_NODES_AGV_1F.size()));
        }
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
