package com.pixelfleet.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.traffic.LaneGraph;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 서버가 아는 공장 평면도 — 노드 이름 → 2D 좌표.
 * 배차 정책이 "작업 출발지에서 가장 가까운 로봇"을 고를 때 쓴다.
 *
 * <p><b>좌표의 주인은 pixel-factory다.</b> 평면도는 공장의 것이지 물류만의 것이 아니라서
 * factory가 마스터를 갖고, 여기서는 {@code GET /api/layout}으로 받아 캐시한다. 예전에는
 * 이 값이 대시보드·robot-sim과 3중으로 하드코딩돼 있어 한 곳만 고치면 배차 거리 비교가
 * <b>조용히</b> 틀어졌다.
 *
 * <p><b>폴백을 남겨 둔다.</b> factory가 안 떠 있어도 fleet은 배차를 계속해야 한다.
 * 못 받으면 아래 하드코딩 값을 쓰고 WARN을 남기며, 주기 갱신이 성공하면 그때 교체된다.
 * 폴백은 "최선의 추측"이지 진실이 아니므로 쓰이고 있다는 사실이 로그에 남아야 한다.
 *
 * <p>미지 노드 해시 폴백은 robot-sim {@code NodeMap}과 <b>동일해야</b> 한다 — 로봇 위치는
 * 시뮬레이터 좌표계로 들어오므로 양쪽이 같은 자리를 가리켜야 거리 비교가 의미를 갖는다.
 */
@Component
public class LocationRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocationRegistry.class);

    private static final double MAX_X = 68.0;
    private static final double MAX_Y = 26.0;

    /**
     * factory에서 못 받았을 때 쓰는 폴백. V12 마이그레이션 시드와 같은 값이다.
     * 마스터를 바꾸면 여기도 함께 고친다(또는 factory가 항상 떠 있게 해서 안 쓰이게 한다).
     *
     * <p>위층(2·3층) 노드는 넣지 않는다 — 지상 AMR은 층을 오가지 못하므로 배차 대상이 아니고,
     * 좌표가 1층과 겹쳐 폴백에 섞으면 "가장 가까운 노드" 계산만 흐려진다.
     */
    private static final Map<String, double[]> FALLBACK_NODES = Map.ofEntries(
            // 창고동 1층 — 충전 베이 4개는 충전존 안에 나란히
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
            Map.entry("QC-OUT", new double[]{62, 6})
    );

    private final Map<String, double[]> nodes = new ConcurrentHashMap<>(FALLBACK_NODES);
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
     * 평면도가 바뀌었을 때의 반영. 노드는 자주 안 바뀌므로 간격은 넉넉하게 둔다.
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

            Map<String, double[]> loaded = new HashMap<>();
            for (JsonNode node : data.path("nodes")) {
                String code = node.path("nodeCode").asText(null);
                if (code != null) {
                    loaded.put(code, new double[]{node.path("posX").asDouble(), node.path("posY").asDouble()});
                }
            }

            if (loaded.isEmpty()) {
                warnFallback("노드가 비어 있음");
                return;
            }

            // 마스터에서 사라진 노드는 캐시에서도 지운다(폴백 값이 유령으로 남지 않게).
            nodes.keySet().retainAll(loaded.keySet());
            nodes.putAll(loaded);

            if (!loadedFromMaster) {
                log.info("Loaded {} layout nodes from {}", loaded.size(), layoutUrl);
            }
            loadedFromMaster = true;

            checkAisleAgreement(data);
        } catch (Exception e) {
            warnFallback(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 서버가 알려준 통로 y와 {@link LaneGraph}의 상수가 같은지 확인한다.
     *
     * <p>LaneGraph는 통로 y를 구간 이름과 경로 계산에 쓰는 상수로 갖고 있어 지금은 동적으로
     * 바꾸지 못한다. 그래서 최소한 <b>어긋났을 때 시끄럽게</b> 만든다 — 조용히 다르면 그린 선과
     * 실제 주행이 갈리고 구간 점유가 엉킨다. (LaneGraph까지 서버 값을 쓰게 하는 건 백로그.)
     */
    private void checkAisleAgreement(JsonNode data) {
        double upper = data.path("upperAisleY").asDouble(Double.NaN);
        double lower = data.path("lowerAisleY").asDouble(Double.NaN);

        if (Double.isNaN(upper) || Double.isNaN(lower)) {
            return;
        }

        if (Math.abs(upper - LaneGraph.AISLE_UPPER_Y) > 0.001
                || Math.abs(lower - LaneGraph.AISLE_LOWER_Y) > 0.001) {
            log.error("통로 y가 서버 평면도와 다르다! server=({}, {}) LaneGraph=({}, {}). "
                            + "경로 계산과 구간 점유가 실제 주행과 갈린다 — LaneGraph 상수를 맞출 것.",
                    upper, lower, LaneGraph.AISLE_UPPER_Y, LaneGraph.AISLE_LOWER_Y);
        }
    }

    private void warnFallback(String reason) {
        // 이미 마스터에서 받아 둔 값이 있으면 그걸 계속 쓴다(일시적 장애로 좌표를 되돌리지 않는다).
        if (loadedFromMaster) {
            log.warn("평면도 갱신 실패({}) — 마지막으로 받은 좌표를 계속 쓴다.", reason);
        } else {
            log.warn("평면도를 가져오지 못했다({}) — 하드코딩 폴백 좌표로 동작한다. "
                            + "factory({})가 떠 있는지 확인할 것. 마스터와 어긋나면 배차 거리 비교가 틀어진다.",
                    reason, layoutUrl);
        }
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
}
