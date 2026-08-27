package com.pixelfleet.sim.map;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 공장 평면도(173 × 74) — 건물 5채. 창고동(WH) 1층만 P32에서 "빗(comb)" 구조로 전면
 * 재설계됐다 — 좌우 세로 스파인(수직) + 밴드 12개(밴드마다 자체 아이슬 통로 + 렉 2행).
 * 생산동·품질동·신관, 창고동 2·3층은 전부 무변경(D5) — 이 파일 전체를 다시 봐도 상단
 * 통로(y=9)·하단 통로(y=18) 규칙과 그 밖의 건물은 예전 그대로다.
 *
 * <pre>
 *   창고동 1층 — 좌측 스파인(x=2) ─── 밴드1~12(각자 아이슬, y=4.0~66.7) ─── 우측 스파인(x=52)
 *   [밴드1  렉행·렉행]
 *   [밴드2  렉행·렉행]                                게이트   생산동(58~90)      품질동(94~102)
 *      ...                                              ╫    [CNC-01..MCT-01]
 *   [밴드12 렉행·렉행]                                    ╫    ○A1..A4 ○B1..B4    ○QC-IN/OUT
 *   ○충전 도크 8개(좌하단 코너)
 * </pre>
 *
 * <p><b>P32: 왜 창고동만 세로로 훨씬 길어졌는가.</b> 밴드 12개(밴드마다 아이슬 + 렉 2행)를
 * 실측 밀도로 채우면 세로(y) 방향 소요가 옛 3행 구조(26)보다 훨씬 크다. 대신 가로(x)는
 * 스파인 간 50유닛이 기존 창고동 폭(53) 안에 들어가서 {@code MAX_X}·다른 건물 x좌표는
 * 전혀 안 바뀐다 — 캔버스가 세로로만 길어진다(설계 근거:
 * docs/p32-warehouse-realistic-relayout-design.md D1/D7).
 *
 * <p><b>P22: AMR은 창고동에 들어오지 않는다.</b> 창고동(WH) 1층 안쪽은 AGV(옛 이름: 랙 피더)
 * 전용이고, 그 밖은 AMR 전용이다 — 경계는 {@code WH-GATE-U}/{@code WH-GATE-L} 두 노드뿐이다.
 * 좌표는 P32에서도 그대로다(우측 스파인이 y=9·18에서 그대로 접점을 만든다, D1 계약 유지).
 * 창고동 2·3층은 범위 밖이라 좌표·역할이 그대로다(계속 AMR, P21 D10).
 *
 * <p><b>렉은 여전히 {@code LaneGraph}를 안 탄다(P21 D2).</b> AGV는 밴드 진입 노드
 * ({@code WH-B01-L}~{@code WH-B12-R})까지만 그래프로 가고, 그 안의 렉 864기까지는 로컬
 * 직선 이동이다 — 그래서 그래프 자체(이 파일의 {@code NODES})는 밴드당 진입 노드 2개만
 * 늘고(24개), 렉 폭발과 무관하게 작게 유지된다. P30의 {@code WH-SHIP-2}(순환 경로 전용
 * 노드)는 이제 필요 없다 — 밴드 12개 전부가 이미 실제 주문이 지나가는 명명 노드다.
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
 * <p>대신 {@code NodeMapLayoutConsistencyTest}가 서버 마스터(V22 마이그레이션 — 평면도를
 * 다시 그리는 마이그레이션마다 이 경로도 함께 옮긴다, V9→V12→V15→V16→V17→V20→V21→V22)와
 * 대조해 <b>어긋나면 빌드를 깨뜨린다.</b> 런타임 의존을 만들지 않으면서 조용한 불일치를 막는
 * 방법이다. 좌표를 바꿀 일이 있으면 마스터를 고치고 여기를 맞춘다(순서가 반대면 테스트가
 * 잡아 준다).
 */
@Component
public class NodeMap {

    /** 평면도 가로. 서버 마스터(layout_settings.width)와 같아야 한다 — 대조 테스트가 확인한다. */
    public static final double MAX_X = 173.0;
    /** 평면도 세로. 서버 마스터(layout_settings.height)와 같아야 한다. P32로 26→74. */
    public static final double MAX_Y = 74.0;

    private static final Map<String, double[]> NODES = Map.ofEntries(
            // ---- 창고동 1층 — 좌우 스파인(수직) + 밴드 12개 진입 노드(P32) ----
            Map.entry("WH-B01-L", new double[]{2, 4.00}),
            Map.entry("WH-B01-R", new double[]{52, 4.00}),
            Map.entry("WH-B02-L", new double[]{2, 9.70}),
            Map.entry("WH-B02-R", new double[]{52, 9.70}),
            Map.entry("WH-B03-L", new double[]{2, 15.40}),
            Map.entry("WH-B03-R", new double[]{52, 15.40}),
            Map.entry("WH-B04-L", new double[]{2, 21.10}),
            Map.entry("WH-B04-R", new double[]{52, 21.10}),
            Map.entry("WH-B05-L", new double[]{2, 26.80}),
            Map.entry("WH-B05-R", new double[]{52, 26.80}),
            Map.entry("WH-B06-L", new double[]{2, 32.50}),
            Map.entry("WH-B06-R", new double[]{52, 32.50}),
            Map.entry("WH-B07-L", new double[]{2, 38.20}),
            Map.entry("WH-B07-R", new double[]{52, 38.20}),
            Map.entry("WH-B08-L", new double[]{2, 43.90}),
            Map.entry("WH-B08-R", new double[]{52, 43.90}),
            Map.entry("WH-B09-L", new double[]{2, 49.60}),
            Map.entry("WH-B09-R", new double[]{52, 49.60}),
            Map.entry("WH-B10-L", new double[]{2, 55.30}),
            Map.entry("WH-B10-R", new double[]{52, 55.30}),
            Map.entry("WH-B11-L", new double[]{2, 61.00}),
            Map.entry("WH-B11-R", new double[]{52, 61.00}),
            Map.entry("WH-B12-L", new double[]{2, 66.70}),
            Map.entry("WH-B12-R", new double[]{52, 66.70}),
            // 우측 스파인이 게이트(y=9/18)와 만나는 접속점 — D1 게이트 좌표 무변경 요구사항.
            Map.entry("WH-SPINE-R-GATE-U", new double[]{52, 9}),
            Map.entry("WH-SPINE-R-GATE-L", new double[]{52, 18}),
            // 기능 노드 — 입고·피킹·출하는 가까운 밴드 진입 노드 옆에.
            Map.entry("WH-RECV", new double[]{2, 3.00}),
            Map.entry("WH-PICK", new double[]{2, 33.50}),
            Map.entry("WH-SHIP", new double[]{52, 65.70}),
            // 엘리베이터 1층 — 좌표(30,13) 무변경(2·3층 샤프트와 같은 자리, D5).
            Map.entry("WH-ELEV-1F", new double[]{30, 13}),
            // 충전 도크 8개 — 좌하단 코너(밴드12 아래) 클러스터(P29 패턴 재사용, D4).
            Map.entry("WH-DOCK-1", new double[]{2.0, 68.5}),
            Map.entry("WH-DOCK-2", new double[]{3.5, 68.5}),
            Map.entry("WH-DOCK-3", new double[]{5.0, 68.5}),
            Map.entry("WH-DOCK-4", new double[]{6.5, 68.5}),
            Map.entry("WH-DOCK-5", new double[]{2.0, 70.0}),
            Map.entry("WH-DOCK-6", new double[]{3.5, 70.0}),
            Map.entry("WH-DOCK-7", new double[]{5.0, 70.0}),
            Map.entry("WH-DOCK-8", new double[]{6.5, 70.0}),
            // 창고동 2·3층 — **좌표 무변경**(D5, 1층과 물리적으로 같은 자리).
            Map.entry("WH-DOCK-2F", new double[]{4, 21}),
            Map.entry("WH-2F-P1", new double[]{17, 6}),
            Map.entry("WH-2F-P2", new double[]{17, 13}),
            Map.entry("WH-ELEV-2F", new double[]{30, 13}),
            Map.entry("WH-DOCK-3F", new double[]{4, 21}),
            Map.entry("WH-3F-P1", new double[]{17, 6}),
            Map.entry("WH-3F-P2", new double[]{17, 13}),
            Map.entry("WH-ELEV-3F", new double[]{30, 13}),
            // P22: AMR ↔ AGV 게이트 — 창고동 벽 밖, 생산동 벽 앞의 중립 지대. P32에서도 무변경.
            Map.entry("WH-GATE-U", new double[]{56, 9}),
            Map.entry("WH-GATE-L", new double[]{56, 18}),
            // P22: 생산동 쪽 AMR 충전 베이.
            Map.entry("PROD-DOCK-1", new double[]{62, 3}),
            Map.entry("PROD-DOCK-2", new double[]{62, 5}),
            Map.entry("PROD-DOCK-3", new double[]{62, 21}),
            Map.entry("PROD-DOCK-4", new double[]{62, 23}),
            // 생산동
            Map.entry("PROD-A1", new double[]{62, 6}),
            Map.entry("PROD-A2", new double[]{69, 6}),
            Map.entry("PROD-A3", new double[]{76, 6}),
            Map.entry("PROD-A4", new double[]{83, 6}),
            Map.entry("PROD-B1", new double[]{62, 21}),
            Map.entry("PROD-B2", new double[]{69, 21}),
            Map.entry("PROD-B3", new double[]{76, 21}),
            Map.entry("PROD-B4", new double[]{83, 21}),
            // 품질동
            Map.entry("QC-IN", new double[]{97, 21}),
            Map.entry("QC-OUT", new double[]{97, 6}),
            // 통로·연결로 교차점(JUNCTION) — 창고동 내부 4개(JCT-4/9/14/19)는 P32로 스파인에
            // 자리를 내주고 사라졌다. PROD/QC 쪽은 무변경.
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
            // 신관(BLDG-A/B, V14) — 무변경.
            Map.entry("GATE-WH-A", new double[]{108, 6}),
            Map.entry("MACH-1", new double[]{118, 6}),
            Map.entry("MACH-2", new double[]{128, 6}),
            Map.entry("GATE-A-B", new double[]{135, 6}),
            Map.entry("ASM-1", new double[]{145, 6}),
            Map.entry("LOGI-1", new double[]{158, 6})
    );

    /**
     * 충전 베이 — <b>지상(1층)만</b>, 로봇 종류별로 갈린다(P22). AGV는 창고동 도크(P32로
     * 4개→8개), AMR은 생산동 도크. 위층 베이는 좌표가 1층과 같아서 넣지 않는다.
     */
    private static final List<String> DOCKS_AGV = List.of(
            "WH-DOCK-1", "WH-DOCK-2", "WH-DOCK-3", "WH-DOCK-4",
            "WH-DOCK-5", "WH-DOCK-6", "WH-DOCK-7", "WH-DOCK-8");
    private static final List<String> DOCKS_AMR = List.of(
            "PROD-DOCK-1", "PROD-DOCK-2", "PROD-DOCK-3", "PROD-DOCK-4");

    /**
     * 유휴 로봇이 순찰할 지점 — 도크는 충전 자리이지 목적지가 아니므로 제외한다.
     * <b>층별로 나눠 둔다</b> — 로봇은 층을 오가지 못하므로 남의 층 노드로 순찰하면 안 된다.
     * 1층 AMR은 창고동 안을 순찰하지 않는다(P22) — 생산동·품질동만 돈다.
     */
    private static final Map<Integer, List<String>> ROAM_NODES_BY_FLOOR = Map.of(
            1, List.of(
                    "PROD-A1", "PROD-A2", "PROD-A3", "PROD-A4",
                    "PROD-B1", "PROD-B2", "PROD-B3", "PROD-B4",
                    "QC-IN", "QC-OUT"),
            2, List.of("WH-2F-P1", "WH-2F-P2", "WH-ELEV-2F"),
            3, List.of("WH-3F-P1", "WH-3F-P2", "WH-ELEV-3F"));

    /**
     * 1층 AGV 전용 순찰 지점(P22) — 창고동 안쪽만. P32로 밴드 진입 노드 일부(1·6·12번,
     * 위/중간/아래를 고르게)를 더해 순찰이 밴드 전체에 퍼지게 한다. P30의 WH-SHIP-2는
     * 은퇴했다(모든 밴드가 이미 실제 목적지 노드라 더 이상 필요 없다).
     */
    private static final List<String> ROAM_NODES_AGV_1F = List.of(
            "WH-RECV", "WH-PICK", "WH-SHIP", "WH-ELEV-1F",
            "WH-B01-L", "WH-B01-R", "WH-B06-L", "WH-B06-R", "WH-B12-L", "WH-B12-R");

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
