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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final double MAX_X = 68.0;
    private static final double MAX_Y = 26.0;

    /** 좌표 일치 판정 허용오차. 부동소수 비교와 "거의 그 자리" 판정에 같이 쓴다. */
    private static final double EPSILON = 0.05;

    /** 두 노드 사이의 연결. {@code cost}는 기본 통행 비용(대략 거리) — factory {@code layout_edges}와 같다. */
    public record Edge(String to, double cost) {}

    /**
     * factory에서 못 받았을 때 쓰는 노드 폴백. V13 마이그레이션 시드와 같은 값이다 —
     * 명명된 노드(1층만, 위층은 배차 대상이 아니라 제외) + 교차점(JUNCTION) 16개.
     */
    private static final Map<String, double[]> FALLBACK_NODES = Map.ofEntries(
            // 창고동 1층
            Map.entry("WH-DOCK-1", new double[]{4, 3}),
            Map.entry("WH-DOCK-2", new double[]{4, 5}),
            Map.entry("WH-DOCK-3", new double[]{4, 21}),
            Map.entry("WH-DOCK-4", new double[]{4, 23}),
            Map.entry("WH-RECV", new double[]{9, 6}),
            Map.entry("WH-PICK", new double[]{9, 13}),
            Map.entry("WH-SHIP", new double[]{14, 21}),
            Map.entry("WH-ELEV-1F", new double[]{14, 13}),
            // 생산동
            Map.entry("PROD-A1", new double[]{27, 6}),
            Map.entry("PROD-A2", new double[]{34, 6}),
            Map.entry("PROD-A3", new double[]{41, 6}),
            Map.entry("PROD-A4", new double[]{48, 6}),
            Map.entry("PROD-B1", new double[]{27, 21}),
            Map.entry("PROD-B2", new double[]{34, 21}),
            Map.entry("PROD-B3", new double[]{41, 21}),
            Map.entry("PROD-B4", new double[]{48, 21}),
            // 품질동 — 가공이 끝난 물건은 무조건 여기를 거친다
            Map.entry("QC-IN", new double[]{62, 21}),
            Map.entry("QC-OUT", new double[]{62, 6}),
            // 통로·연결로 교차점 (P20) — 로봇이 정차하는 자리가 아니라 경로 그래프의 분기점.
            // V13__layout_edges.sql과 같은 값.
            Map.entry("JCT-4-U", new double[]{4, 9}),
            Map.entry("JCT-4-L", new double[]{4, 18}),
            Map.entry("JCT-9-U", new double[]{9, 9}),
            Map.entry("JCT-9-L", new double[]{9, 18}),
            Map.entry("JCT-14-U", new double[]{14, 9}),
            Map.entry("JCT-14-L", new double[]{14, 18}),
            Map.entry("JCT-27-U", new double[]{27, 9}),
            Map.entry("JCT-27-L", new double[]{27, 18}),
            Map.entry("JCT-34-U", new double[]{34, 9}),
            Map.entry("JCT-34-L", new double[]{34, 18}),
            Map.entry("JCT-41-U", new double[]{41, 9}),
            Map.entry("JCT-41-L", new double[]{41, 18}),
            Map.entry("JCT-48-U", new double[]{48, 9}),
            Map.entry("JCT-48-L", new double[]{48, 18}),
            Map.entry("JCT-62-U", new double[]{62, 9}),
            Map.entry("JCT-62-L", new double[]{62, 18})
    );

    /**
     * 폴백 엣지 — V13 마이그레이션의 엣지 중 위 폴백 노드(1층 + 교차점)만으로 이뤄진 것들.
     * {@code {from, to, cost}} 3항. 양방향 취급은 로딩 시 자동으로 반대 방향도 추가한다.
     */
    private static final List<Object[]> FALLBACK_EDGES = List.of(
            // 교차점 내부 수직(상단↔하단, 통로 사이)
            new Object[]{"JCT-4-U", "JCT-4-L", 9.0}, new Object[]{"JCT-9-U", "JCT-9-L", 9.0},
            new Object[]{"JCT-14-U", "JCT-14-L", 9.0}, new Object[]{"JCT-27-U", "JCT-27-L", 9.0},
            new Object[]{"JCT-34-U", "JCT-34-L", 9.0}, new Object[]{"JCT-41-U", "JCT-41-L", 9.0},
            new Object[]{"JCT-48-U", "JCT-48-L", 9.0}, new Object[]{"JCT-62-U", "JCT-62-L", 9.0},
            // 통로(가로) — 인접 연결로 교차점끼리
            new Object[]{"JCT-4-U", "JCT-9-U", 5.0}, new Object[]{"JCT-9-U", "JCT-14-U", 5.0},
            new Object[]{"JCT-14-U", "JCT-27-U", 13.0}, new Object[]{"JCT-27-U", "JCT-34-U", 7.0},
            new Object[]{"JCT-34-U", "JCT-41-U", 7.0}, new Object[]{"JCT-41-U", "JCT-48-U", 7.0},
            new Object[]{"JCT-48-U", "JCT-62-U", 14.0},
            new Object[]{"JCT-4-L", "JCT-9-L", 5.0}, new Object[]{"JCT-9-L", "JCT-14-L", 5.0},
            new Object[]{"JCT-14-L", "JCT-27-L", 13.0}, new Object[]{"JCT-27-L", "JCT-34-L", 7.0},
            new Object[]{"JCT-34-L", "JCT-41-L", 7.0}, new Object[]{"JCT-41-L", "JCT-48-L", 7.0},
            new Object[]{"JCT-48-L", "JCT-62-L", 14.0},
            // 명명된 노드 → 교차점
            new Object[]{"WH-DOCK-1", "JCT-4-U", 6.0}, new Object[]{"WH-DOCK-2", "JCT-4-U", 4.0},
            new Object[]{"WH-DOCK-3", "JCT-4-L", 3.0}, new Object[]{"WH-DOCK-4", "JCT-4-L", 5.0},
            new Object[]{"WH-RECV", "JCT-9-U", 3.0},
            new Object[]{"WH-PICK", "JCT-9-U", 4.0}, new Object[]{"WH-PICK", "JCT-9-L", 5.0},
            new Object[]{"WH-SHIP", "JCT-14-L", 3.0},
            new Object[]{"WH-ELEV-1F", "JCT-14-U", 4.0}, new Object[]{"WH-ELEV-1F", "JCT-14-L", 5.0},
            new Object[]{"PROD-A1", "JCT-27-U", 3.0}, new Object[]{"PROD-A2", "JCT-34-U", 3.0},
            new Object[]{"PROD-A3", "JCT-41-U", 3.0}, new Object[]{"PROD-A4", "JCT-48-U", 3.0},
            new Object[]{"PROD-B1", "JCT-27-L", 3.0}, new Object[]{"PROD-B2", "JCT-34-L", 3.0},
            new Object[]{"PROD-B3", "JCT-41-L", 3.0}, new Object[]{"PROD-B4", "JCT-48-L", 3.0},
            new Object[]{"QC-OUT", "JCT-62-U", 3.0}, new Object[]{"QC-IN", "JCT-62-L", 3.0}
    );

    private final Map<String, double[]> nodes = new ConcurrentHashMap<>(FALLBACK_NODES);

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
     * 게이트웨이는 인증을 요구하고 서비스 간 인증(M2M)이 아직 없기 때문이다.
     * factory가 이 엔드포인트만 인증 없이 열어 두었다(평면도는 민감정보가 아니다).
     */
    private final String layoutUrl;

    public LocationRegistry(@Value("${layout.url:http://localhost:9001/api/layout}") String layoutUrl) {
        this.layoutUrl = layoutUrl;
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
                if (from == null || to == null || Double.isNaN(cost)) {
                    continue;
                }
                loadedAdjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(to, cost));
                if (bidirectional) {
                    loadedAdjacency.computeIfAbsent(to, k -> new ArrayList<>()).add(new Edge(from, cost));
                }
            }

            // 마스터에서 사라진 노드는 캐시에서도 지운다(폴백 값이 유령으로 남지 않게).
            nodes.keySet().retainAll(loadedNodes.keySet());
            nodes.putAll(loadedNodes);
            floors.keySet().retainAll(loadedFloors.keySet());
            floors.putAll(loadedFloors);
            adjacency.clear();
            adjacency.putAll(loadedAdjacency);

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
