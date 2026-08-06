package com.pixelfleet.traffic;

import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.location.LocationRegistry.Edge;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 공장의 주행 레인망. <b>경로는 서버가 소유한다</b> — 로봇이 스스로 길을 정하지 않고
 * 서버가 계산한 웨이포인트를 따라간다. 그래야 서버가 구간 점유를 통제할 수 있다.
 *
 * <p><b>P20-2: 컴파일타임 고정 규칙 → 데이터 기반 그래프 탐색.</b> 예전에는 통로 2개·연결로
 * 8개가 이 클래스의 상수({@code CONNECTOR_X}, {@code AISLE_UPPER_Y/LOWER_Y})였다. 이제는
 * {@link LocationRegistry}가 factory({@code GET /api/layout})에서 캐시한 노드-엣지 그래프를
 * 다익스트라로 탐색한다 — Building을 늘리는 일이 이 클래스를 고치지 않고 데이터(노드·엣지)만
 * 추가하는 일이 되게 하기 위해서다(설계 근거: {@code docs/p20-layout-routing-design.md} D2).
 *
 * <p><b>진입점(anchor) 문제.</b> 그래프의 노드는 전부 "정차 자리"거나 "교차점"이지만, 로봇의
 * 실시간 좌표는 그 사이 임의의 점일 수 있다(이동 중). 그 점을 그래프에 넣기 위해 <b>임시
 * 가상 노드</b>를 만들어 같은 연결로 위의 가장 가까운 이웃(아래·위 각 하나)에 잇고, 탐색이
 * 끝나면 버린다 — DB를 건드리지 않는다.
 *
 * <p><b>구간(segment) ID는 기하학적으로 정의한다(하위호환).</b> 세로 이동은 연결로 x와
 * 통로 기준 위/중간/아래 대역(top/mid/bot), 가로 이동은 어느 통로(AU/AL)의 어느 연결로
 * 구간인지로 정한다. 예전 문자열 형식({@code V:34:top}, {@code AU:4-9})과 그대로 호환된다 —
 * P20-1의 엣지가 애초에 이 대역 하나씩과 정확히 대응하도록 만들어졌기 때문이다. 이 ID를
 * 파싱해 쓰는 소비자는 없다(대시보드 확인 완료) — {@link com.pixelfleet.traffic.TrafficController}가
 * 불투명한 문자열로만 다룬다.
 *
 * <p>진입점의 세로 이동은 로봇의 <b>실제 x</b>에서 일어난다(연결로로 순간이동하지 않는다) —
 * 웨이포인트는 로봇의 실좌표를 쓰고, 구간 ID만 가장 가까운 연결로 기준으로 스냅한다.
 * 예전 {@code LaneGraph.plan()}도 같은 방식이었다(웨이포인트는 {@code fx}, 구간은
 * {@code nearestConnector(fx)}) — 그 근사를 그대로 계승한다.
 *
 * <p><b>P20-4: 장애물이 있는 엣지는 아예 통과할 수 없는 것으로 취급한다.</b> {@link
 * ObstacleStore}에 막혀 있다고 기록된 엣지는 다익스트라 완화(relaxation) 단계에서 건너뛴다 —
 * 비용을 무한대로 두는 것과 같은 효과이면서 코드가 더 단순하다. 로봇의 <b>진입점 접근
 * 구간</b>(가상 노드 → 가장 가까운 연결로)은 장애물 대상이 아니다 — 실제 DB 엣지가 아니라
 * 매 호출 임시로 만드는 국소 연결이라, 장애물이 실재 엣지 목록으로만 들어오는 한(P20-4
 * 계약) 자연히 걸리지 않는다. 설계 근거: {@code docs/p20-layout-routing-design.md} D4·D5.
 */
@Component
public class LaneGraph {

    private static final double EPS = 0.05;

    private final LocationRegistry locations;
    private final ObstacleStore obstacles;

    public LaneGraph(LocationRegistry locations, ObstacleStore obstacles) {
        this.locations = locations;
        this.obstacles = obstacles;
    }

    /** 두 노드 사이 엣지의 정본 id — 방향과 무관하게 항상 같은 문자열(사전순). 장애물 조회 키다. */
    public static String canonicalEdgeId(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "~" + b : b + "~" + a;
    }

    /**
     * 서버가 계산한 주행 계획: 로봇에게 줄 웨이포인트와, 점유해야 할 구간들.
     *
     * @param cost 그래프 최단경로 비용(P20-5 — 배차 정책이 "직선 최근접" 대신 이걸로 후보를
     *             고른다). 목적지에 이미 있으면 0, 그래프가 끊겨 있으면
     *             {@link Double#POSITIVE_INFINITY}(직선 폴백은 여전히 웨이포인트로 준다 —
     *             로봇을 세우는 것보다는 낫다는 판단, 다만 배차 비교에서는 항상 진다).
     */
    public record RoutePlan(List<double[]> waypoints, List<String> segments, double cost) {}

    public RoutePlan planByNode(double[] from, String toNode) {
        return plan(from, locations.resolve(toNode));
    }

    /** 노드의 좌표. "그 자리에 누가 서 있는가"를 실제 위치로 판단할 때 쓴다. */
    public double[] nodePosition(String node) {
        return locations.resolve(node);
    }

    /**
     * {@code from}에서 {@code to}까지의 최단 경로를 그래프에서 계산한다.
     *
     * <p>노드 코드가 아니라 좌표를 받는 이유는 {@code from}이 로봇의 실시간 위치(그래프의
     * 노드가 아닐 수 있음)이기 때문이다. {@code to}는 실무상 항상 어떤 노드의 정확한 좌표다.
     */
    public RoutePlan plan(double[] from, double[] to) {
        Anchor source = anchor(from);
        Anchor target = anchor(to);

        if (source.code().equals(target.code())) {
            // 이미 그 자리 — 옛 구현도 이 경우 구간 없이 목적지 좌표 하나만 돌려줬다.
            return new RoutePlan(List.of(to.clone()), List.of(), 0.0);
        }

        DijkstraResult result = dijkstra(source, target);
        if (result.path().isEmpty()) {
            // 그래프가 끊겨 있으면(설정 오류) 직선 목적지라도 준다 — 로봇을 완전히 세우는 것보다 낫다.
            // 비용은 무한대로 둔다 — 배차 비교에서 이 후보/경로가 절대 이기지 않게.
            return new RoutePlan(List.of(to.clone()), List.of(), Double.POSITIVE_INFINITY);
        }

        List<PathStep> path = result.path();
        return new RoutePlan(buildWaypoints(from, to, source, path), buildSegments(path), result.cost());
    }

    /**
     * 지금 이 좌표가 속한 구간. 로봇 위치 텔레메트리로 "어디까지 지나갔는지" 알아내
     * 지나간 구간을 반납하는 데 쓴다.
     *
     * @return 구간 ID. 레인에서 벗어난 위치(정차 자리 등)면 null.
     */
    public String segmentAt(double x, double y) {
        double upper = locations.upperAisleY();
        double lower = locations.lowerAisleY();
        if (Math.abs(y - upper) < 1.2) {
            return aisleSegmentAt("AU", x);
        }
        if (Math.abs(y - lower) < 1.2) {
            return aisleSegmentAt("AL", x);
        }
        double[] columns = locations.columns();
        if (columns.length == 0) {
            return null;
        }
        double lane = nearestColumn(columns, x);
        if (Math.abs(lane - x) > 2.0) {
            return null;
        }
        String band = y < upper ? "top" : (y > lower ? "bot" : "mid");
        return String.format("V:%.0f:%s", lane, band);
    }

    // ---- 진입점(anchor) ----

    /**
     * 좌표를 그래프 진입점으로 — 정확히 일치하는 노드가 있으면 그 노드, 없으면 가상 노드.
     *
     * @param segmentX 구간 ID 계산에 쓸 x. 가상 노드는 <b>스냅된 연결로</b>(로봇의 실제 x가
     *                 아니다) — 구간은 항상 정해진 연결로 단위로 셈해야 한다(옛 동작 계승).
     */
    private record Anchor(String code, double[] pos, boolean virtual, double segmentX, List<Edge> extraEdges) {}

    private Anchor anchor(double[] pos) {
        String exact = locations.exactNodeAt(pos[0], pos[1]);
        if (exact != null) {
            return new Anchor(exact, pos, false, pos[0], List.of());
        }

        double column = nearestColumn(locations.columns(), pos[0]);
        List<Map.Entry<String, double[]>> onColumn = locations.nodesOnColumn(column);

        Map.Entry<String, double[]> below = null;
        Map.Entry<String, double[]> above = null;
        for (Map.Entry<String, double[]> entry : onColumn) {
            double y = entry.getValue()[1];
            if (y <= pos[1] && (below == null || y > below.getValue()[1])) {
                below = entry;
            }
            if (y >= pos[1] && (above == null || y < above.getValue()[1])) {
                above = entry;
            }
        }

        List<Edge> extra = new ArrayList<>();
        if (below != null) {
            extra.add(new Edge(below.getKey(), Math.abs(pos[1] - below.getValue()[1])));
        }
        if (above != null && (below == null || !above.getKey().equals(below.getKey()))) {
            extra.add(new Edge(above.getKey(), Math.abs(pos[1] - above.getValue()[1])));
        }

        String virtualCode = "~" + pos[0] + "," + pos[1];
        return new Anchor(virtualCode, pos, true, column, extra);
    }

    // ---- 최단경로 (다익스트라) ----

    /**
     * @param pos        웨이포인트(시각적 이동)에 쓰는 좌표.
     * @param segmentPos 구간 ID 계산에 쓰는 좌표 — 가상 노드는 스냅된 연결로 x를 쓴다(pos와 다를 수 있음).
     */
    private record PathStep(String code, double[] pos, double[] segmentPos) {}

    /** @param path 비어 있으면 도달 불가 — 그때 {@code cost}는 의미 없다({@code plan()}이 무한대로 대체). */
    private record DijkstraResult(List<PathStep> path, double cost) {}

    private DijkstraResult dijkstra(Anchor source, Anchor target) {
        Map<String, double[]> extraPositions = new HashMap<>();
        Map<String, double[]> extraSegmentPositions = new HashMap<>();
        Map<String, List<Edge>> extraAdjacency = new HashMap<>();
        registerAnchor(source, extraPositions, extraSegmentPositions, extraAdjacency);
        registerAnchor(target, extraPositions, extraSegmentPositions, extraAdjacency);

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Set<String> visited = new HashSet<>();
        PriorityQueue<String> queue = new PriorityQueue<>(
                Comparator.comparingDouble(code -> dist.getOrDefault(code, Double.MAX_VALUE)));

        dist.put(source.code(), 0.0);
        queue.add(source.code());

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(target.code())) {
                break;
            }
            double currentDist = dist.get(current);
            for (Edge edge : neighborsOf(current, extraAdjacency)) {
                if (visited.contains(edge.to())) {
                    continue;
                }
                if (obstacles.isBlocked(canonicalEdgeId(current, edge.to()))) {
                    continue; // 장애물 — 이 엣지는 존재하지 않는 것처럼 취급한다
                }
                double candidate = currentDist + edge.cost();
                if (candidate < dist.getOrDefault(edge.to(), Double.MAX_VALUE)) {
                    dist.put(edge.to(), candidate);
                    prev.put(edge.to(), current);
                    queue.add(edge.to());
                }
            }
        }

        if (!dist.containsKey(target.code())) {
            return new DijkstraResult(List.of(), Double.POSITIVE_INFINITY);
        }

        LinkedList<String> codes = new LinkedList<>();
        String cursor = target.code();
        while (cursor != null) {
            codes.addFirst(cursor);
            cursor = prev.get(cursor);
        }

        List<PathStep> steps = new ArrayList<>();
        for (String code : codes) {
            double[] pos = extraPositions.containsKey(code) ? extraPositions.get(code) : locations.resolve(code);
            double[] segmentPos = extraSegmentPositions.containsKey(code) ? extraSegmentPositions.get(code) : pos;
            steps.add(new PathStep(code, pos, segmentPos));
        }
        return new DijkstraResult(steps, dist.get(target.code()));
    }

    /** 가상 노드를 이번 탐색에서만 그래프에 편입시킨다 — 이웃 쪽에도 돌아오는 엣지를 심는다. */
    private void registerAnchor(Anchor anchor, Map<String, double[]> positions,
                                 Map<String, double[]> segmentPositions, Map<String, List<Edge>> extra) {
        if (!anchor.virtual()) {
            return;
        }
        positions.put(anchor.code(), anchor.pos());
        // 구간 계산은 스냅된 연결로 x를 쓴다 — 웨이포인트(실좌표)와 다를 수 있다(클래스 문서 참고).
        segmentPositions.put(anchor.code(), new double[]{anchor.segmentX(), anchor.pos()[1]});
        extra.put(anchor.code(), anchor.extraEdges());
        for (Edge e : anchor.extraEdges()) {
            extra.computeIfAbsent(e.to(), k -> new ArrayList<>(locations.edgesFrom(e.to())))
                    .add(new Edge(anchor.code(), e.cost()));
        }
    }

    private List<Edge> neighborsOf(String code, Map<String, List<Edge>> extraAdjacency) {
        List<Edge> extra = extraAdjacency.get(code);
        return extra != null ? extra : locations.edgesFrom(code);
    }

    // ---- 웨이포인트 ----

    /**
     * 경로 노드열을 대략적인 꺾인점(turn point)만 남긴 웨이포인트로 압축한다 — 일직선인
     * 중간 노드는 GOTO에 필요 없다(옛 구현도 통로를 몇 구간 지나든 꺾이는 지점만 줬다).
     *
     * <p>시작이 가상 노드(로봇의 실좌표)면 첫 꺾인점의 x는 <b>로봇의 실제 x</b>로 되돌린다 —
     * 그래프 탐색은 가장 가까운 연결로로 스냅해 계산했지만, 로봇이 그 연결로까지 옆으로
     * 미끄러지듯 이동하는 게 아니라 <b>자기 x에서 수직으로</b> 통로까지 올라간다(옛 동작 계승).
     */
    private List<double[]> buildWaypoints(double[] from, double[] to, Anchor source, List<PathStep> path) {
        // path.get(0)이 이미 출발점(source anchor의 위치, from과 같다) — 따로 더 넣지 않는다.
        List<double[]> raw = new ArrayList<>();
        for (PathStep step : path) {
            raw.add(step.pos());
        }
        raw.set(raw.size() - 1, to.clone());

        if (source.virtual() && raw.size() > 1) {
            double[] firstTurn = raw.get(1);
            raw.set(1, new double[]{from[0], firstTurn[1]});
        }

        List<double[]> compressed = new ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            double[] prevPoint = raw.get(i - 1);
            double[] point = raw.get(i);
            boolean last = i == raw.size() - 1;
            if (!last) {
                double[] nextPoint = raw.get(i + 1);
                if (sameDirection(prevPoint, point, nextPoint)) {
                    continue; // 일직선 위의 중간 점 — 꺾이지 않으니 생략
                }
            }
            compressed.add(point);
        }
        return compressed;
    }

    private boolean sameDirection(double[] a, double[] b, double[] c) {
        double dx1 = b[0] - a[0];
        double dy1 = b[1] - a[1];
        double dx2 = c[0] - b[0];
        double dy2 = c[1] - b[1];
        boolean firstVertical = Math.abs(dx1) < EPS;
        boolean secondVertical = Math.abs(dx2) < EPS;
        boolean firstHorizontal = Math.abs(dy1) < EPS;
        boolean secondHorizontal = Math.abs(dy2) < EPS;
        if (firstVertical && secondVertical) {
            return Math.signum(dy1) == Math.signum(dy2) || dy1 == 0 || dy2 == 0;
        }
        if (firstHorizontal && secondHorizontal) {
            return Math.signum(dx1) == Math.signum(dx2) || dx1 == 0 || dx2 == 0;
        }
        return false;
    }

    // ---- 구간(segment) ----

    private List<String> buildSegments(List<PathStep> path) {
        List<String> segments = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            String segment = segmentBetween(path.get(i - 1).segmentPos(), path.get(i).segmentPos());
            if (segment != null) {
                segments.add(segment);
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(segments));
    }

    /** 두 인접 노드 사이 구간 ID. 그래프의 모든 엣지는 축에 정렬돼 있다(세로 또는 가로). */
    private String segmentBetween(double[] a, double[] b) {
        if (Math.abs(a[0] - b[0]) < EPS) {
            return verticalBand(a[0], a[1], b[1]);
        }
        if (Math.abs(a[1] - b[1]) < EPS) {
            return horizontalAisle(a[1], a[0], b[0]);
        }
        return null; // 대각선 엣지는 이 그래프에 없어야 한다 — 방어적으로 무시.
    }

    private String verticalBand(double x, double y1, double y2) {
        double lo = Math.min(y1, y2);
        double hi = Math.max(y1, y2);
        if (hi - lo < 0.01) {
            return null;
        }
        double upper = locations.upperAisleY();
        double lower = locations.lowerAisleY();
        if (hi <= upper + EPS) {
            return String.format("V:%.0f:top", x);
        }
        if (lo >= lower - EPS) {
            return String.format("V:%.0f:bot", x);
        }
        return String.format("V:%.0f:mid", x);
    }

    private String horizontalAisle(double y, double x1, double x2) {
        double upper = locations.upperAisleY();
        double lower = locations.lowerAisleY();
        String prefix = Math.abs(y - upper) <= Math.abs(y - lower) ? "AU" : "AL";
        double lo = Math.min(x1, x2);
        double hi = Math.max(x1, x2);
        return String.format("%s:%.0f-%.0f", prefix, lo, hi);
    }

    private double nearestColumn(double[] columns, double x) {
        double best = columns.length > 0 ? columns[0] : x;
        for (double c : columns) {
            if (Math.abs(c - x) < Math.abs(best - x)) {
                best = c;
            }
        }
        return best;
    }

    private String aisleSegmentAt(String prefix, double x) {
        double[] columns = locations.columns();
        for (int i = 0; i < columns.length - 1; i++) {
            if (x >= columns[i] - 1.5 && x <= columns[i + 1] + 1.5) {
                return String.format("%s:%.0f-%.0f", prefix, columns[i], columns[i + 1]);
            }
        }
        return null;
    }

    /** 디버깅·로그용. */
    public String describe(List<String> segments) {
        return Arrays.toString(segments.toArray());
    }
}
