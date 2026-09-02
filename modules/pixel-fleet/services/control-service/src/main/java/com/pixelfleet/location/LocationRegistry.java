package com.pixelfleet.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 서버가 아는 공장 평면도 — 노드 이름 → 2D 좌표 <b>그리고 (P20) 노드 사이의 연결(그래프)</b>.
 * 배차 정책이 "작업 출발지에서 가장 가까운 로봇"을 고를 때, {@link com.pixelfleet.traffic.LaneGraph}가
 * 경로를 계산할 때 쓴다.
 *
 * <p><b>좌표·연결의 주인은 pixel-factory다.</b> 평면도는 공장의 것이지 물류만의 것이 아니라서
 * factory가 마스터를 갖고(정적 토폴로지), 여기서는 {@code GET /api/layout}으로 받아 캐시한다.
 * "지금 이 엣지가 막혔는가" 같은 동적 사실은 여기 두지 않는다 — 그건 별도의 라이브 캐시다
 * (설계 근거: {@code docs/p20-layout-routing-design.md} D2·D4, P20-4에서 붙인다).
 *
 * <p><b>폴백을 남겨 둔다.</b> factory가 안 떠 있어도 fleet은 배차·경로계산을 계속해야 한다.
 * 못 받으면 아래 하드코딩 값(노드 <b>+ 엣지</b>)을 쓰고 WARN을 남기며, 주기 갱신이 성공하면
 * 그때 교체된다. 예전에는 노드 좌표만 폴백이 있으면 됐다 — 경로 계산이 컴파일 상수로 된
 * 별도 알고리즘(LaneGraph)이었기 때문이다. 이제 경로 계산 자체가 이 그래프를 읽으므로,
 * 폴백에 엣지가 없으면 factory가 죽었을 때 로봇이 <b>전혀 못 움직인다</b> — 그래서 엣지도
 * 마이그레이션 시드와 같은 값으로 폴백을 둔다.
 *
 * <p>미지 노드 해시 폴백은 robot-sim {@code NodeMap}과 <b>동일해야</b> 한다 — 로봇 위치는
 * 시뮬레이터 좌표계로 들어오므로 양쪽이 같은 자리를 가리켜야 거리 비교가 의미를 갖는다.
 */
@Component
public class LocationRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocationRegistry.class);

    private static final double MAX_X = 173.0;
    /** P32로 26→74 — 창고동 1층이 밴드 12개(빗 구조)로 세로로 훨씬 길어졌다. */
    private static final double MAX_Y = 74.0;

    /** 좌표 일치 판정 허용오차. 부동소수 비교와 "거의 그 자리" 판정에 같이 쓴다. */
    private static final double EPSILON = 0.05;

    /**
     * 두 노드 사이의 연결. {@code cost}는 기본 통행 비용(대략 거리) — factory
     * {@code layout_edges}와 같다. {@code widthMm}은 통로 폭(P25, {@code LaneGraph}가
     * 로딩 상태별 최소폭과 비교해 라우팅을 강제하는 데 쓴다) — {@link #UNCONSTRAINED_WIDTH_MM}은
     * "폭 제약 없음"(구버전 factory 응답, 폴백, 가상 노드 국소 접근 엣지가 이 값을 쓴다).
     */
    public record Edge(String to, double cost, double widthMm) {

        public static final double UNCONSTRAINED_WIDTH_MM = Double.MAX_VALUE;

        public Edge(String to, double cost) {
            this(to, cost, UNCONSTRAINED_WIDTH_MM);
        }
    }

    /**
     * factory에서 못 받았을 때 쓰는 노드 폴백. V22 마이그레이션 시드와 같은 값이다 —
     * 명명된 노드(1층만, 위층은 배차 대상이 아니라 제외) + 교차점(JUNCTION).
     *
     * <p>P32로 창고동 내부는 "좌우 스파인 + 밴드 12개 진입 노드"로 바뀌었다(D1) —
     * {@code WH-B01-L}~{@code WH-B12-R}. 옛 JCT-4/9/14/19는 이 스파인에 자리를 내주고
     * 없어졌다. PROD/QC 쪽 교차점은 무변경.
     */
    private static final Map<String, double[]> FALLBACK_NODES = Map.ofEntries(
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
            // 우측 스파인이 엘리베이터와 만나는 접속점(P32 D8, V23) — WH-B02-R/WH-B03-R 사이.
            Map.entry("WH-SPINE-R-ELEV", new double[]{52, 13.5}),
            // 기능 노드 — 입고·피킹·출하는 가까운 밴드 진입 노드 옆에.
            Map.entry("WH-RECV", new double[]{2, 3.00}),
            Map.entry("WH-PICK", new double[]{2, 33.50}),
            Map.entry("WH-SHIP", new double[]{52, 65.70}),
            // 엘리베이터 1층 — P32 D8(V23)로 우측 스파인 위(52,13.5)로 재배치. 원래(30,13)는
            // 밴드 그리드 한복판에 파묻혀 있었다(구현 후 스크린샷으로 발견).
            Map.entry("WH-ELEV-1F", new double[]{52, 13.5}),
            // 충전 도크 8개 — 좌하단 코너(밴드12 아래) 클러스터(P29 패턴 재사용, D4로 4→8).
            Map.entry("WH-DOCK-1", new double[]{2.0, 68.5}),
            Map.entry("WH-DOCK-2", new double[]{3.5, 68.5}),
            Map.entry("WH-DOCK-3", new double[]{5.0, 68.5}),
            Map.entry("WH-DOCK-4", new double[]{6.5, 68.5}),
            Map.entry("WH-DOCK-5", new double[]{2.0, 70.0}),
            Map.entry("WH-DOCK-6", new double[]{3.5, 70.0}),
            Map.entry("WH-DOCK-7", new double[]{5.0, 70.0}),
            Map.entry("WH-DOCK-8", new double[]{6.5, 70.0}),
            // P22: AMR ↔ AGV 게이트 — 창고동 벽 밖, 생산동 벽 앞의 중립 지대. P32에서도 무변경
            // (우측 스파인이 y=9/18에서 그대로 접점을 만든다, D1).
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
            // 품질동 — 가공이 끝난 물건은 무조건 여기를 거친다 (+13)
            Map.entry("QC-IN", new double[]{97, 21}),
            Map.entry("QC-OUT", new double[]{97, 6}),
            // 통로·연결로 교차점 (P20) — 로봇이 정차하는 자리가 아니라 경로 그래프의 분기점.
            // 창고동 내부 4개(JCT-4/9/14/19)는 P32로 스파인에 자리를 내주고 사라졌다.
            // PROD/QC 쪽은 V22__warehouse_band_relayout.sql과 같은 값(무변경).
            Map.entry("JCT-27-U", new double[]{62, 9}),
            Map.entry("JCT-27-L", new double[]{62, 18}),
            Map.entry("JCT-34-U", new double[]{69, 9}),
            Map.entry("JCT-34-L", new double[]{69, 18}),
            Map.entry("JCT-41-U", new double[]{76, 9}),
            Map.entry("JCT-41-L", new double[]{76, 18}),
            Map.entry("JCT-48-U", new double[]{83, 9}),
            Map.entry("JCT-48-L", new double[]{83, 18}),
            // P33 — 생산동·품질동 통합(D2/D3). JCT-48↔JCT-62 사이(x=90)에 새 교차점을
            // 끼워 넣고, 물류(WIP 스테이징) 노드를 매단다. QC는 더 이상 별도 건물이
            // 아니다(PROD로 흡수, building_code만 변경 — 여기 nodes 맵엔 건물 개념이
            // 없으므로 좌표 자체는 무영향).
            Map.entry("JCT-55-U", new double[]{90, 9}),
            Map.entry("JCT-55-L", new double[]{90, 18}),
            Map.entry("PROD-L1", new double[]{90, 13.5}),
            Map.entry("JCT-62-U", new double[]{97, 9}),
            Map.entry("JCT-62-L", new double[]{97, 18})
    );

    /**
     * 폴백 엣지 — V16 마이그레이션의 엣지 중 위 폴백 노드(1층 + 교차점)만으로 이뤄진 것들.
     * {@code {from, to, cost}} 3항. 양방향 취급은 로딩 시 자동으로 반대 방향도 추가한다.
     */
    private static final List<Object[]> FALLBACK_EDGES = List.of(
            // ---- 좌측 스파인 — 밴드 12개 진입 노드를 y순으로 잇는 수직 체인(P32, D1) ----
            new Object[]{"WH-B01-L", "WH-B02-L", 5.7}, new Object[]{"WH-B02-L", "WH-B03-L", 5.7},
            new Object[]{"WH-B03-L", "WH-B04-L", 5.7}, new Object[]{"WH-B04-L", "WH-B05-L", 5.7},
            new Object[]{"WH-B05-L", "WH-B06-L", 5.7}, new Object[]{"WH-B06-L", "WH-B07-L", 5.7},
            new Object[]{"WH-B07-L", "WH-B08-L", 5.7}, new Object[]{"WH-B08-L", "WH-B09-L", 5.7},
            new Object[]{"WH-B09-L", "WH-B10-L", 5.7}, new Object[]{"WH-B10-L", "WH-B11-L", 5.7},
            new Object[]{"WH-B11-L", "WH-B12-L", 5.7},
            // ---- 우측 스파인 — 게이트 접속점 2개를 y순서대로 끼워 넣는다 ----
            new Object[]{"WH-B01-R", "WH-SPINE-R-GATE-U", 5.0},
            new Object[]{"WH-SPINE-R-GATE-U", "WH-B02-R", 0.7},
            new Object[]{"WH-B02-R", "WH-SPINE-R-ELEV", 3.8},
            new Object[]{"WH-SPINE-R-ELEV", "WH-B03-R", 1.9},
            new Object[]{"WH-B03-R", "WH-SPINE-R-GATE-L", 2.6},
            new Object[]{"WH-SPINE-R-GATE-L", "WH-B04-R", 3.1},
            new Object[]{"WH-B04-R", "WH-B05-R", 5.7}, new Object[]{"WH-B05-R", "WH-B06-R", 5.7},
            new Object[]{"WH-B06-R", "WH-B07-R", 5.7}, new Object[]{"WH-B07-R", "WH-B08-R", 5.7},
            new Object[]{"WH-B08-R", "WH-B09-R", 5.7}, new Object[]{"WH-B09-R", "WH-B10-R", 5.7},
            new Object[]{"WH-B10-R", "WH-B11-R", 5.7}, new Object[]{"WH-B11-R", "WH-B12-R", 5.7},
            // 게이트 접속 — D1 계약 유지(WH-GATE-U/L 좌표 무변경).
            new Object[]{"WH-SPINE-R-GATE-U", "WH-GATE-U", 4.0},
            new Object[]{"WH-SPINE-R-GATE-L", "WH-GATE-L", 4.0},
            // ---- 밴드 아이슬(가로) — D3에서 TrafficController가 배타 잠금을 거는 세그먼트 ----
            new Object[]{"WH-B01-L", "WH-B01-R", 50.0}, new Object[]{"WH-B02-L", "WH-B02-R", 50.0},
            new Object[]{"WH-B03-L", "WH-B03-R", 50.0}, new Object[]{"WH-B04-L", "WH-B04-R", 50.0},
            new Object[]{"WH-B05-L", "WH-B05-R", 50.0}, new Object[]{"WH-B06-L", "WH-B06-R", 50.0},
            new Object[]{"WH-B07-L", "WH-B07-R", 50.0}, new Object[]{"WH-B08-L", "WH-B08-R", 50.0},
            new Object[]{"WH-B09-L", "WH-B09-R", 50.0}, new Object[]{"WH-B10-L", "WH-B10-R", 50.0},
            new Object[]{"WH-B11-L", "WH-B11-R", 50.0}, new Object[]{"WH-B12-L", "WH-B12-R", 50.0},
            // 교차점 내부 수직(상단↔하단, 통로 사이) — PROD/QC 쪽만(창고동은 스파인으로 대체).
            new Object[]{"JCT-27-U", "JCT-27-L", 9.0},
            new Object[]{"JCT-34-U", "JCT-34-L", 9.0}, new Object[]{"JCT-41-U", "JCT-41-L", 9.0},
            new Object[]{"JCT-48-U", "JCT-48-L", 9.0}, new Object[]{"JCT-62-U", "JCT-62-L", 9.0},
            // P33 — JCT-55-U/L(물류 교차점, D2/D3)도 다른 JCT와 같은 패턴(수직 Δy=9).
            new Object[]{"JCT-55-U", "JCT-55-L", 9.0},
            // 통로(가로) — PROD 내부(무변경). 창고동↔게이트는 스파인 접속 엣지로 대체됐다(위).
            new Object[]{"WH-GATE-U", "JCT-27-U", 6.0},
            new Object[]{"JCT-27-U", "JCT-34-U", 7.0},
            new Object[]{"JCT-34-U", "JCT-41-U", 7.0}, new Object[]{"JCT-41-U", "JCT-48-U", 7.0},
            // P33 — JCT-48↔JCT-62 직결(옛 비용14)을 JCT-55(물류 교차점, x=90) 경유로
            // 쪼갰다: 7+7.
            new Object[]{"JCT-48-U", "JCT-55-U", 7.0}, new Object[]{"JCT-55-U", "JCT-62-U", 7.0},
            new Object[]{"WH-GATE-L", "JCT-27-L", 6.0},
            new Object[]{"JCT-27-L", "JCT-34-L", 7.0},
            new Object[]{"JCT-34-L", "JCT-41-L", 7.0}, new Object[]{"JCT-41-L", "JCT-48-L", 7.0},
            new Object[]{"JCT-48-L", "JCT-55-L", 7.0}, new Object[]{"JCT-55-L", "JCT-62-L", 7.0},
            // 기능 노드 → 가장 가까운 스파인 진입 노드(P32). 엘리베이터는 D8(V23)로 스파인
            // 접속점과 같은 자리가 돼서 비용이 작다(옛 (30,13)일 때는 25.3/24.4였다).
            new Object[]{"WH-RECV", "WH-B01-L", 1.0},
            new Object[]{"WH-PICK", "WH-B06-L", 1.0},
            new Object[]{"WH-SHIP", "WH-B12-R", 1.0},
            new Object[]{"WH-ELEV-1F", "WH-SPINE-R-ELEV", 1.0},
            // 충전 도크 8개 → 밴드12 좌측 진입(P32, D4).
            new Object[]{"WH-DOCK-1", "WH-B12-L", 1.8}, new Object[]{"WH-DOCK-2", "WH-B12-L", 1.8},
            new Object[]{"WH-DOCK-3", "WH-B12-L", 1.8}, new Object[]{"WH-DOCK-4", "WH-B12-L", 1.8},
            new Object[]{"WH-DOCK-5", "WH-B12-L", 3.3}, new Object[]{"WH-DOCK-6", "WH-B12-L", 3.3},
            new Object[]{"WH-DOCK-7", "WH-B12-L", 3.3}, new Object[]{"WH-DOCK-8", "WH-B12-L", 3.3},
            // P22: 생산동 AMR 충전 베이
            new Object[]{"PROD-DOCK-1", "JCT-27-U", 6.0}, new Object[]{"PROD-DOCK-2", "JCT-27-U", 4.0},
            new Object[]{"PROD-DOCK-3", "JCT-27-L", 3.0}, new Object[]{"PROD-DOCK-4", "JCT-27-L", 5.0},
            new Object[]{"PROD-A1", "JCT-27-U", 3.0}, new Object[]{"PROD-A2", "JCT-34-U", 3.0},
            new Object[]{"PROD-A3", "JCT-41-U", 3.0}, new Object[]{"PROD-A4", "JCT-48-U", 3.0},
            new Object[]{"PROD-B1", "JCT-27-L", 3.0}, new Object[]{"PROD-B2", "JCT-34-L", 3.0},
            new Object[]{"PROD-B3", "JCT-41-L", 3.0}, new Object[]{"PROD-B4", "JCT-48-L", 3.0},
            new Object[]{"QC-OUT", "JCT-62-U", 3.0}, new Object[]{"QC-IN", "JCT-62-L", 3.0},
            // P33 — 물류(L) 구역: PROD-L1을 JCT-55-U/L 양쪽에 짧게 매단다(Δy=4.5).
            new Object[]{"PROD-L1", "JCT-55-U", 4.5}, new Object[]{"PROD-L1", "JCT-55-L", 4.5}
    );

    /**
     * 렉 하나의 물리 정보(P21). {@code nodes}/{@code floors}/{@code adjacency}와 <b>절대 섞지
     * 않는다</b> — 렉을 AMR의 레인 그래프에 노드로 넣으면 진입점(anchor) 탐색이 렉 좌표를
     * 가까운 연결로로 잘못 고를 위험이 있다(P20-3에서 실제로 겪은 종류의 버그, 설계 근거:
     * {@code docs/p21-warehouse-rack-feeder-design.md} D2). AGV는 이 맵만 보고 로컬
     * 직선 이동을 한다 — {@code LaneGraph}/{@code TrafficController}를 타지 않는다.
     */
    public record RackInfo(String rackCode, short floorNo, double[] pos, String orientation) {}

    /**
     * 피킹존(AGV가 취출한 물건을 AMR에게 넘기는 자리) 노드 코드 관례 — {@code WH-PICK}
     * (1층) 또는 {@code WH-{층}F-P{n}}(위층). 렉이 어느 피킹존과 가까운지는 WMS가 아니라
     * 이 관례로 fleet이 좌표로 계산한다(D4 — WMS는 렉→피킹존 구간이 있다는 사실 자체를
     * 몰라야 한다). {@code elevatorNode(floor)}(OrderService)와 같은 성격의 명명 관례다.
     */
    private static final Pattern PICK_NODE_PATTERN = Pattern.compile("^WH-(?:PICK|\\d+F-P\\d+)$");

    /**
     * 창고동 1층 안쪽 명명 노드(P22) — 도크(P32로 4→8)·입고장·피킹존·출하장·엘리베이터
     * 승강장. AMR은 이제 여기 못 들어간다({@code requiredPool}이 이 패턴이면 AGV 풀을
     * 준다). <b>{@code WH-GATE-*}·{@code WH-B*}(밴드 진입 노드)는 일부러 뺐다</b> — 게이트는
     * AMR·AGV 둘 다의 경계 정차 자리라 항상 AMR 풀(일반 노드 처리)로 보고, 밴드 진입 노드는
     * 실제 주문의 출발지/목적지로 쓰인 적이 없는 순수 라우팅 경유점이라 예전 JCT-*와 같은
     * 취급이다. 2·3층 노드({@code WH-2F-P1} 등)도 뺐다 — 범위 밖이라 계속 AMR이다(P21 D10).
     * P30의 {@code WH-SHIP-2}는 P32로 은퇴했다(밴드 12개 전부가 이미 실제 목적지 노드).
     */
    private static final Pattern WH_1F_INTERIOR_PATTERN =
            Pattern.compile("^WH-(?:DOCK-[1-8]|RECV|PICK|SHIP|ELEV-1F)$");

    /** 창고동 1층 안쪽 명명 노드인가(P22) — {@link #WH_1F_INTERIOR_PATTERN} 참고. */
    public boolean isWarehouseFloor1Node(String code) {
        return code != null && WH_1F_INTERIOR_PATTERN.matcher(code).matches();
    }

    /**
     * factory에서 못 받았을 때 쓰는 렉 폴백 — factory V22 마이그레이션 시드와 같은 값
     * (912기: 1층 864 · 2층 24 · 3층 24). 렉 피더가 factory 없이도 계속 취출 동작을 하려면
     * 좌표가 있어야 한다({@code FALLBACK_NODES}와 같은 이유). 1층 864개는 리터럴로 나열하지
     * 않고 {@link #buildFallbackRacks()}가 {@code RackMap}과 같은 공식(밴드 12 × 열 72)으로
     * 생성한다 — 이 리스트에는 2·3층(D5, 무변경)만 남는다.
     */
    private static final List<Object[]> FALLBACK_RACKS = List.of(
            new Object[]{"WH-2F-R01", (short) 2, 8.0, 4.0, "V"}, new Object[]{"WH-2F-R02", (short) 2, 21.0, 4.0, "V"},
            new Object[]{"WH-2F-R03", (short) 2, 33.5, 4.0, "V"}, new Object[]{"WH-2F-R04", (short) 2, 8.0, 13.5, "V"},
            new Object[]{"WH-2F-R05", (short) 2, 21.0, 13.5, "V"}, new Object[]{"WH-2F-R06", (short) 2, 33.5, 13.5, "V"},
            new Object[]{"WH-2F-R07", (short) 2, 8.0, 22.0, "V"}, new Object[]{"WH-2F-R08", (short) 2, 21.0, 22.0, "V"},
            new Object[]{"WH-2F-R09", (short) 2, 33.5, 22.0, "V"},
            new Object[]{"WH-2F-R10", (short) 2, 13.0, 4.0, "V"}, new Object[]{"WH-2F-R11", (short) 2, 26.0, 4.0, "V"},
            new Object[]{"WH-2F-R12", (short) 2, 38.5, 4.0, "V"}, new Object[]{"WH-2F-R13", (short) 2, 13.0, 13.5, "V"},
            new Object[]{"WH-2F-R14", (short) 2, 26.0, 13.5, "V"}, new Object[]{"WH-2F-R15", (short) 2, 38.5, 13.5, "V"},
            new Object[]{"WH-2F-R16", (short) 2, 13.0, 22.0, "V"}, new Object[]{"WH-2F-R17", (short) 2, 26.0, 22.0, "V"},
            new Object[]{"WH-2F-R18", (short) 2, 38.5, 22.0, "V"},
            new Object[]{"WH-3F-R01", (short) 3, 8.0, 4.0, "V"}, new Object[]{"WH-3F-R02", (short) 3, 21.0, 4.0, "V"},
            new Object[]{"WH-3F-R03", (short) 3, 33.5, 4.0, "V"}, new Object[]{"WH-3F-R04", (short) 3, 8.0, 13.5, "V"},
            new Object[]{"WH-3F-R05", (short) 3, 21.0, 13.5, "V"}, new Object[]{"WH-3F-R06", (short) 3, 33.5, 13.5, "V"},
            new Object[]{"WH-3F-R07", (short) 3, 8.0, 22.0, "V"}, new Object[]{"WH-3F-R08", (short) 3, 21.0, 22.0, "V"},
            new Object[]{"WH-3F-R09", (short) 3, 33.5, 22.0, "V"},
            new Object[]{"WH-3F-R10", (short) 3, 13.0, 4.0, "V"}, new Object[]{"WH-3F-R11", (short) 3, 26.0, 4.0, "V"},
            new Object[]{"WH-3F-R12", (short) 3, 38.5, 4.0, "V"}, new Object[]{"WH-3F-R13", (short) 3, 13.0, 13.5, "V"},
            new Object[]{"WH-3F-R14", (short) 3, 26.0, 13.5, "V"}, new Object[]{"WH-3F-R15", (short) 3, 38.5, 13.5, "V"},
            new Object[]{"WH-3F-R16", (short) 3, 13.0, 22.0, "V"}, new Object[]{"WH-3F-R17", (short) 3, 26.0, 22.0, "V"},
            new Object[]{"WH-3F-R18", (short) 3, 38.5, 22.0, "V"},
            // P30: 4번째 베이(x=45/50) — 18기 증설(2·3층만, 1층은 P32로 밴드 구조에 흡수됐다).
            new Object[]{"WH-2F-R19", (short) 2, 45.0, 4.0, "V"}, new Object[]{"WH-2F-R20", (short) 2, 50.0, 4.0, "V"},
            new Object[]{"WH-2F-R21", (short) 2, 45.0, 13.5, "V"}, new Object[]{"WH-2F-R22", (short) 2, 50.0, 13.5, "V"},
            new Object[]{"WH-2F-R23", (short) 2, 45.0, 22.0, "V"}, new Object[]{"WH-2F-R24", (short) 2, 50.0, 22.0, "V"},
            new Object[]{"WH-3F-R19", (short) 3, 45.0, 4.0, "V"}, new Object[]{"WH-3F-R20", (short) 3, 50.0, 4.0, "V"},
            new Object[]{"WH-3F-R21", (short) 3, 45.0, 13.5, "V"}, new Object[]{"WH-3F-R22", (short) 3, 50.0, 13.5, "V"},
            new Object[]{"WH-3F-R23", (short) 3, 45.0, 22.0, "V"}, new Object[]{"WH-3F-R24", (short) 3, 50.0, 22.0, "V"}
    );

    private final Map<String, double[]> nodes = new ConcurrentHashMap<>(FALLBACK_NODES);

    private final Map<String, RackInfo> racks = new ConcurrentHashMap<>(buildFallbackRacks());

    /**
     * 노드 → 층. 좌표만으로는 층을 알 수 없다 — 위층 노드는 아래층과 <b>같은 자리</b>에 있다
     * (WH-DOCK-1과 WH-DOCK-2F는 둘 다 4,3). 배차가 "같은 층 로봇"을 고르려면 이 표가 필요하다.
     * 폴백 노드는 전부 1층이라 초기값이 없다 — 여기 없는 노드는 1층으로 본다.
     */
    private final Map<String, Short> floors = new ConcurrentHashMap<>();

    /** 인접 리스트(양방향 모두 반영) — {@link com.pixelfleet.traffic.LaneGraph}의 경로 탐색 입력. */
    private final Map<String, List<Edge>> adjacency = new ConcurrentHashMap<>(buildAdjacency(FALLBACK_EDGES));

    /** 상단/하단 통로 y. 폴백 기본값은 V13 시드와 같다. */
    private volatile double upperAisleY = 9.0;
    private volatile double lowerAisleY = 18.0;

    private volatile boolean loadedFromMaster = false;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * factory의 평면도 주소. 게이트웨이를 경유하지 않고 <b>모듈에 직접</b> 붙는다 —
     * 프라이빗 네트워크 안이라 굳이 게이트웨이를 안 거친다(QMS→factory, WMS→fleet과
     * 같은 방식). P16 WP2부터 {@link ServiceTokenProvider}가 발급한 서비스 토큰을 실어
     * 보낸다 — factory {@code /api/layout}도 이제 인증을 요구한다.
     */
    private final String layoutUrl;

    private final ServiceTokenProvider tokenProvider;

    public LocationRegistry(
            @Value("${layout.url:http://localhost:9001/api/layout}") String layoutUrl,
            ServiceTokenProvider tokenProvider
    ) {
        this.layoutUrl = layoutUrl;
        this.tokenProvider = tokenProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refresh();
    }

    /**
     * 주기 갱신. 두 가지를 겸한다 — 기동 시 factory가 늦게 떠서 실패한 경우의 복구, 그리고
     * 평면도가 바뀌었을 때의 반영. 노드·엣지는 자주 안 바뀌므로 간격은 넉넉하게 둔다.
     */
    @Scheduled(fixedDelayString = "${layout.refresh-interval-ms:300000}")
    public void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(layoutUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + tokenProvider.token())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                warnFallback("HTTP " + response.statusCode());
                return;
            }

            JsonNode data = objectMapper.readTree(response.body()).path("data");

            Map<String, double[]> loadedNodes = new HashMap<>();
            Map<String, Short> loadedFloors = new HashMap<>();
            for (JsonNode node : data.path("nodes")) {
                String code = node.path("nodeCode").asText(null);
                if (code != null) {
                    loadedNodes.put(code, new double[]{node.path("posX").asDouble(), node.path("posY").asDouble()});
                    loadedFloors.put(code, (short) node.path("floorNo").asInt(1));
                }
            }

            if (loadedNodes.isEmpty()) {
                warnFallback("노드가 비어 있음");
                return;
            }

            Map<String, List<Edge>> loadedAdjacency = new HashMap<>();
            for (JsonNode edge : data.path("edges")) {
                String from = edge.path("fromNode").asText(null);
                String to = edge.path("toNode").asText(null);
                double cost = edge.path("baseCost").asDouble(Double.NaN);
                boolean bidirectional = edge.path("bidirectional").asBoolean(true);
                // 필드가 없으면(구버전 factory) 무제한 취급 — racks 필드가 없을 때 폴백을
                // 그대로 두는 것과 같은 하위호환 패턴(P21 D3, P25 design doc D3).
                double widthMm = edge.path("widthMm").asDouble(Edge.UNCONSTRAINED_WIDTH_MM);
                if (from == null || to == null || Double.isNaN(cost)) {
                    continue;
                }
                loadedAdjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, cost, widthMm));
                if (bidirectional) {
                    loadedAdjacency.computeIfAbsent(to, k -> new ArrayList<>()).add(new Edge(from, cost, widthMm));
                }
            }

            Map<String, RackInfo> loadedRacks = new HashMap<>();
            for (JsonNode rack : data.path("racks")) {
                String code = rack.path("rackCode").asText(null);
                if (code == null) {
                    continue;
                }
                loadedRacks.put(code, new RackInfo(code, (short) rack.path("floorNo").asInt(1),
                        new double[]{rack.path("posX").asDouble(), rack.path("posY").asDouble()},
                        rack.path("orientation").asText("V")));
            }

            // 마스터에서 사라진 노드는 캐시에서도 지운다(폴백 값이 유령으로 남지 않게).
            nodes.keySet().retainAll(loadedNodes.keySet());
            nodes.putAll(loadedNodes);
            floors.keySet().retainAll(loadedFloors.keySet());
            floors.putAll(loadedFloors);
            adjacency.clear();
            adjacency.putAll(loadedAdjacency);
            if (!loadedRacks.isEmpty()) {
                // 응답에 racks 필드가 없거나(구버전 factory) 비어 있으면 폴백을 그대로 둔다 —
                // 렉이 통째로 사라졌다고 보고 AGV를 전부 못 움직이게 만들 이유는 없다.
                racks.clear();
                racks.putAll(loadedRacks);
            }

            double upper = data.path("upperAisleY").asDouble(Double.NaN);
            double lower = data.path("lowerAisleY").asDouble(Double.NaN);
            if (!Double.isNaN(upper) && !Double.isNaN(lower)) {
                upperAisleY = upper;
                lowerAisleY = lower;
            }

            if (!loadedFromMaster) {
                log.info("Loaded {} layout nodes / {} edge-sources from {}",
                        loadedNodes.size(), loadedAdjacency.size(), layoutUrl);
            }
            loadedFromMaster = true;
        } catch (Exception e) {
            warnFallback(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void warnFallback(String reason) {
        // 이미 마스터에서 받아 둔 값이 있으면 그걸 계속 쓴다(일시적 장애로 좌표를 되돌리지 않는다).
        if (loadedFromMaster) {
            log.warn("평면도 갱신 실패({}) — 마지막으로 받은 노드·엣지를 계속 쓴다.", reason);
        } else {
            log.warn("평면도를 가져오지 못했다({}) — 하드코딩 폴백 노드·엣지로 동작한다. "
                            + "factory({})가 떠 있는지 확인할 것. 마스터와 어긋나면 배차 거리·경로가 틀어진다.",
                    reason, layoutUrl);
        }
    }

    /**
     * 이 노드가 몇 층인가. 모르는 노드는 1층으로 본다 — 지상이 기본이고, 1층으로 잘못 봐도
     * 배차가 헛돌 뿐 위층 로봇이 아래층에 나타나지는 않는다.
     */
    public short floorOf(String node) {
        Short known = floors.get(node);
        return known == null ? 1 : known;
    }

    public double[] resolve(String node) {
        double[] known = nodes.get(node);
        if (known != null) {
            return known.clone();
        }
        // robot-sim의 NodeMap과 동일한 폴백이어야 한다.
        int h = Math.abs(node == null ? 0 : node.hashCode());
        double x = (h % 1000) / 1000.0 * MAX_X;
        double y = ((h / 1000) % 1000) / 1000.0 * MAX_Y;
        return new double[]{x, y};
    }

    /** 이 좌표에 정확히 있는 노드 코드. 없으면 {@code null} (P20 — LaneGraph의 그래프 진입점 탐색용). */
    public String exactNodeAt(double x, double y) {
        for (Map.Entry<String, double[]> entry : nodes.entrySet()) {
            double[] pos = entry.getValue();
            if (Math.abs(pos[0] - x) < EPSILON && Math.abs(pos[1] - y) < EPSILON) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 이 노드에서 갈 수 있는 인접 노드들(양방향 반영 완료). 모르는 노드면 빈 리스트. */
    public List<Edge> edgesFrom(String node) {
        return adjacency.getOrDefault(node, List.of());
    }

    /**
     * 세로 연결로가 있는 x좌표들(오름차순, 중복 없음) — 캐시된 노드 좌표에서 뽑는다.
     * 모든 노드는 연결로 x 위에 있다는 평면도 전제(V9)를 그대로 이용한다 — 굳이 JUNCTION
     * 타입만 걸러내지 않아도 같은 집합이 나온다.
     */
    public double[] columns() {
        TreeSet<Double> xs = new TreeSet<>();
        for (double[] pos : nodes.values()) {
            xs.add(Math.round(pos[0] * 100.0) / 100.0);
        }
        double[] result = new double[xs.size()];
        int i = 0;
        for (double x : xs) {
            result[i++] = x;
        }
        return result;
    }

    /** 주어진 연결로 x 위에 있는 노드들을, y 오름차순으로. */
    public List<Map.Entry<String, double[]>> nodesOnColumn(double columnX) {
        List<Map.Entry<String, double[]>> result = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : nodes.entrySet()) {
            if (Math.abs(entry.getValue()[0] - columnX) < 0.5) {
                result.add(entry);
            }
        }
        result.sort(Comparator.comparingDouble(e -> e.getValue()[1]));
        return result;
    }

    public double upperAisleY() {
        return upperAisleY;
    }

    public double lowerAisleY() {
        return lowerAisleY;
    }

    /**
     * 이 좌표가 속한 창고동 밴드의 배타 잠금 세그먼트 ID(P32 D3) — {@code null}이면 어느
     * 밴드에도 속하지 않는다(밴드 12개 범위 밖, 예: 게이트 근처).
     *
     * <p><b>왜 필요한가.</b> AGV 이동은 {@code LaneGraph}를 안 타서(P21 D2, {@code
     * OrderService#planLeg}) 그래프 엣지를 지나며 자동으로 구간을 예약하는 매커니즘이
     * AGV에는 애초에 적용되지 않는다 — 밴드 아이슬을 그래프 엣지로만 만들어 두면 D3가
     * 요구하는 "밴드당 AGV 1대"가 AMR 쪽에만(즉 아무도 안 쓰는 경로에만) 적용되고 정작
     * 밴드를 실제로 오가는 AGV에는 효과가 없다. 그래서 AGV 레그가 이 메서드로 시작점·
     * 도착점이 속한 밴드를 직접 찾아 {@link com.pixelfleet.traffic.TrafficController}에
     * 명시적으로 예약을 건다({@code OrderService#planLeg} 참고) — 세그먼트 ID 형식은
     * {@code LaneGraph.horizontalAisle}과 똑같이 맞춰서({@code "A{y}:2-52"}) 그래프 쪽
     * 예약과 문자열이 우연히라도 갈리지 않게 한다.
     *
     * <p>정확한 물리적 경계(렉 발자국)까지는 안 따진다 — 가장 가까운 밴드 아이슬 y가
     * {@code BAND_PITCH} 이내면 그 밴드 소속으로 본다(도크·엘리베이터처럼 밴드 열 사이에
     * 낀 지점도 가장 가까운 밴드에 자연스럽게 포함된다). 그보다 멀면(게이트 근처 등)
     * {@code null} — 어차피 그 구간은 밴드 통로가 아니다.
     */
    public String bandSegmentFor(double[] pos) {
        int band = bandNumberFor(pos);
        if (band < 0) {
            return null;
        }
        return String.format("A%.0f:2-52", bandAisleY(band));
    }

    /**
     * 이 좌표가 속한 창고동 밴드의 존 코드(P32 D10, {@code "WH-1F-B01"} 형식) —
     * {@code null}이면 {@link #bandSegmentFor}와 같은 이유로 밴드 밖.
     *
     * <p>{@link com.pixelfleet.order.service.OrderService#requiredPool}이 예전엔 창고동
     * 1층 전체를 존 하나({@code "WH-PICK"})로 뭉뚱그렸다 — 밴드 12개를 배타 잠금(D3)으로
     * 나눠 놓고 배차 존은 그대로 하나면, 로봇이 자기가 못 가는 밴드의 작업까지 받아 대기만
     * 하다 다른 로봇 기회를 막는 비효율이 생긴다. 존을 밴드 단위로 쪼개면 배차 시점부터
     * "이 로봇은 이 밴드 담당"이 갈린다({@code robots.zone_code}, fleet V13 마이그레이션).
     */
    public String bandZoneCodeFor(double[] pos) {
        int band = bandNumberFor(pos);
        return band < 0 ? null : String.format("WH-1F-B%02d", band);
    }

    /**
     * 이 좌표가 속한 밴드 아이슬의 y좌표(P32 D10) — {@code null}이면 어느 밴드에도
     * 안 속함(다른 두 메서드와 같은 기준). {@code OrderService#planLeg}가 밴드가 다른
     * 두 지점 사이를 이동할 때, 좌측 스파인(x=2, 모든 렉 열보다 왼쪽이라 항상 비어 있음)을
     * 거쳐 가는 경유 웨이포인트를 만드는 데 쓴다 — 직선으로 이으면 다른 밴드의 렉을
     * 가로지르기 때문이다(구현 후 실측으로 발견).
     */
    public Double bandAisleYFor(double[] pos) {
        int band = bandNumberFor(pos);
        return band < 0 ? null : bandAisleY(band);
    }

    /** {@link #bandSegmentFor}/{@link #bandZoneCodeFor}가 공유하는 "가장 가까운 밴드" 계산. */
    private int bandNumberFor(double[] pos) {
        int nearestBand = -1;
        double bestDy = Double.MAX_VALUE;
        for (int band = 1; band <= BAND_COUNT; band++) {
            double dy = Math.abs(pos[1] - bandAisleY(band));
            if (dy < bestDy) {
                bestDy = dy;
                nearestBand = band;
            }
        }
        return (nearestBand < 0 || bestDy > BAND_PITCH) ? -1 : nearestBand;
    }

    private static double bandAisleY(int band) {
        return BAND1_AISLE_Y + (band - 1) * BAND_PITCH;
    }

    // ---- 렉(P21) ----

    public boolean isRackCode(String code) {
        return racks.containsKey(code);
    }

    /** @throws NullPointerException 존재하지 않는 렉 코드 — 호출부가 {@link #isRackCode}로 먼저 걸러야 한다. */
    public RackInfo rack(String rackCode) {
        RackInfo info = racks.get(rackCode);
        if (info == null) {
            throw new IllegalArgumentException("알 수 없는 렉 코드입니다: " + rackCode);
        }
        return info;
    }

    /**
     * AGV가 이 렉을 서비스하려 설 자리 — 렉 중심에서 방향(세로/가로)에 수직으로
     * 한 걸음 뗀 점. 로컬 이동 전용 좌표라 {@code LaneGraph}가 쓰는 연결로·통로 개념과
     * 무관하다(D2) — 정확한 간격보다 "렉 앞에 선다"는 사실 자체가 중요하다.
     */
    public double[] rackApproachPoint(String rackCode) {
        RackInfo info = rack(rackCode);
        double offset = 1.0;
        return "H".equals(info.orientation())
                ? new double[]{info.pos()[0], info.pos()[1] + offset}
                : new double[]{info.pos()[0] + offset, info.pos()[1]};
    }

    /**
     * 이 렉이 물건을 넘길 피킹존 노드 — 같은 층의 피킹존 노드(D4의 명명 관례) 중 렉과
     * 가장 가까운 것. WMS는 이 매핑을 몰라도 된다(설계 근거: design doc D4 — 예전엔 WMS
     * 마이그레이션이 렉마다 이 매핑을 손으로 갖고 있었다).
     *
     * <p>같은 층 후보가 하나도 없으면(폴백 모드 — {@code FALLBACK_NODES}는 위층 노드를
     * 안 갖는다, 기존 1층 전용 폴백과 같은 한계) {@code "WH-PICK"}으로 물러선다 — 좌표가
     * 정확하지 않을 수 있지만, {@link #resolve}가 모르는 노드도 절대 예외를 던지지 않고
     * 해시 좌표를 주는 것과 같은 "로봇을 완전히 세우는 것보다 낫다" 원칙을 따른다.
     */
    public String nearestPickNode(String rackCode) {
        RackInfo info = rack(rackCode);
        String best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<String, double[]> entry : nodes.entrySet()) {
            String code = entry.getKey();
            if (!PICK_NODE_PATTERN.matcher(code).matches() || floorOf(code) != info.floorNo()) {
                continue;
            }
            double dx = entry.getValue()[0] - info.pos()[0];
            double dy = entry.getValue()[1] - info.pos()[1];
            double dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = code;
            }
        }
        if (best == null) {
            log.warn("렉 {}({}층)에 대응하는 피킹존 노드를 못 찾았다 — WH-PICK으로 대체한다 "
                    + "(factory 폴백 모드에서 위층 노드가 비어 있을 때 발생할 수 있음).", rackCode, info.floorNo());
            return "WH-PICK";
        }
        return best;
    }

    /** P32 밴드 구조 상수 — factory V22 마이그레이션·robot-sim {@code RackMap}과 반드시 같아야 한다. */
    private static final int BAND_COUNT = 12;
    private static final int RACKS_PER_BAND = 72;
    private static final double LEFT_SPINE_X = 2.0;
    private static final double COL_OFFSET = 1.75;
    private static final double COL_PITCH = 1.3;
    private static final double BAND1_AISLE_Y = 4.0;
    private static final double BAND_PITCH = 5.7;
    private static final double ROW_OFFSET_Y = 1.75;
    /**
     * P32 D9(V23) — 밴드12 뒷줄 앞쪽 4칸이 충전존(CZ-1F, x∈[1,8])과 좌표상 겹쳐서
     * 생성에서 건너뛴다. factory V23·robot-sim {@code RackMap}과 같은 예외 조건.
     */
    private static final int EXCLUDED_BAND = 12;
    private static final Set<Integer> EXCLUDED_COLS = Set.of(37, 38, 39, 40);

    private static Map<String, RackInfo> buildFallbackRacks() {
        Map<String, RackInfo> result = new HashMap<>();
        for (Object[] row : FALLBACK_RACKS) {
            String code = (String) row[0];
            short floor = (short) row[1];
            double x = (double) row[2];
            double y = (double) row[3];
            String orientation = (String) row[4];
            result.put(code, new RackInfo(code, floor, new double[]{x, y}, orientation));
        }
        // 창고동 1층 860기(P32, D9로 4기 제외) — factory V22+V23의 생성 공식과 정확히
        // 같아야 한다(열 1~36=앞줄, 37~72=뒷줄, orientation='VD').
        for (int band = 1; band <= BAND_COUNT; band++) {
            double aisleY = BAND1_AISLE_Y + (band - 1) * BAND_PITCH;
            for (int col = 1; col <= RACKS_PER_BAND; col++) {
                if (band == EXCLUDED_BAND && EXCLUDED_COLS.contains(col)) {
                    continue;
                }
                int colInRow = (col - 1) % 36;
                double x = LEFT_SPINE_X + COL_OFFSET + colInRow * COL_PITCH;
                double y = col <= 36 ? aisleY - ROW_OFFSET_Y : aisleY + ROW_OFFSET_Y;
                String code = String.format("WH-1F-B%02d-R%02d", band, col);
                result.put(code, new RackInfo(code, (short) 1, new double[]{x, y}, "VD"));
            }
        }
        return result;
    }

    private static Map<String, List<Edge>> buildAdjacency(List<Object[]> rows) {
        Map<String, List<Edge>> adjacency = new HashMap<>();
        for (Object[] row : rows) {
            String from = (String) row[0];
            String to = (String) row[1];
            double cost = (double) row[2];
            adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, cost));
            adjacency.computeIfAbsent(to, k -> new ArrayList<>()).add(new Edge(from, cost));
        }
        return adjacency;
    }
}
