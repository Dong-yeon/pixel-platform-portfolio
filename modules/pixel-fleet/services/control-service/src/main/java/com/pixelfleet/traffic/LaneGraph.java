package com.pixelfleet.traffic;

import com.pixelfleet.location.LocationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 공장의 주행 레인망. <b>경로는 서버가 소유한다</b> — 로봇이 스스로 길을 정하지 않고
 * 서버가 계산한 웨이포인트를 따라간다. 그래야 서버가 구간 점유를 통제할 수 있다.
 *
 * <p>레인 구조는 단순하다: 가로 주통로(y=12) 하나와, 통로에서 각 스테이션 열로 올라가고
 * 내려가는 세로 연결로들.
 *
 * <pre>
 *   x=3     11      18      25      32      41
 *    │      │       │       │       │       │     ← 세로 연결로
 *    ┼──────┼───────┼───────┼───────┼───────┼     ← 가로 주통로 (y=12)
 *    │      │       │       │       │       │
 * </pre>
 *
 * <p>구간(segment)은 점유 단위다. 가로는 인접한 연결로 사이 구간, 세로는 통로 위/아래
 * 구간으로 나눈다. {@link TrafficController}가 이 구간 단위로 점유권을 관리한다.
 */
@Component
public class LaneGraph {

    /** 가로 주통로의 y좌표. robot-sim NodeMap.AISLE_Y, 대시보드 AISLE_Y와 같아야 한다. */
    public static final double AISLE_Y = 12.0;

    /** 세로 연결로가 있는 x좌표들(정렬 상태를 유지할 것). */
    private static final double[] CONNECTOR_X = {3, 11, 18, 25, 32, 41};

    private final LocationRegistry locations;

    public LaneGraph(LocationRegistry locations) {
        this.locations = locations;
    }

    /** 서버가 계산한 주행 계획: 로봇에게 줄 웨이포인트와, 점유해야 할 구간들. */
    public record RoutePlan(List<double[]> waypoints, List<String> segments) {}

    public RoutePlan planByNode(double[] from, String toNode) {
        return plan(from, locations.resolve(toNode));
    }

    /**
     * {@code from}에서 {@code to}까지 통로를 경유하는 경로를 만든다.
     * 세로로 같은 줄이면 통로를 거치지 않고 바로 오르내린다.
     */
    public RoutePlan plan(double[] from, double[] to) {
        List<double[]> waypoints = new ArrayList<>();
        List<String> segments = new ArrayList<>();

        double fx = from[0];
        double fy = from[1];
        double tx = to[0];
        double ty = to[1];

        if (Math.abs(fx - tx) < 0.6) {
            // 같은 세로선 — 통로를 거칠 필요가 없다.
            waypoints.add(new double[]{tx, ty});
            addVerticalSegments(segments, nearestConnector(fx), fy, ty);
            return new RoutePlan(waypoints, dedupe(segments));
        }

        double fromLane = nearestConnector(fx);
        double toLane = nearestConnector(tx);

        // 통로로 내려/올라간 뒤 → 통로를 따라 이동 → 목표 줄로 이동
        waypoints.add(new double[]{fx, AISLE_Y});
        waypoints.add(new double[]{tx, AISLE_Y});
        waypoints.add(new double[]{tx, ty});

        addVerticalSegments(segments, fromLane, fy, AISLE_Y);
        addAisleSegments(segments, fromLane, toLane);
        addVerticalSegments(segments, toLane, AISLE_Y, ty);

        return new RoutePlan(waypoints, dedupe(segments));
    }

    /** 통로를 기준으로 위/아래 중 어느 세로 구간을 지나는지 구분해 담는다. */
    private void addVerticalSegments(List<String> out, double laneX, double y1, double y2) {
        double lo = Math.min(y1, y2);
        double hi = Math.max(y1, y2);
        if (hi - lo < 0.01) {
            return;
        }
        if (lo < AISLE_Y) {
            out.add(String.format("V:%.0f:up", laneX));
        }
        if (hi > AISLE_Y) {
            out.add(String.format("V:%.0f:down", laneX));
        }
    }

    /** 두 연결로 사이의 통로 구간들을 담는다(방향 무관 — 같은 구간은 같은 이름). */
    private void addAisleSegments(List<String> out, double x1, double x2) {
        double lo = Math.min(x1, x2);
        double hi = Math.max(x1, x2);
        for (int i = 0; i < CONNECTOR_X.length - 1; i++) {
            double a = CONNECTOR_X[i];
            double b = CONNECTOR_X[i + 1];
            if (b > lo && a < hi) {
                out.add(String.format("A:%.0f-%.0f", a, b));
            }
        }
    }

    private double nearestConnector(double x) {
        double best = CONNECTOR_X[0];
        for (double c : CONNECTOR_X) {
            if (Math.abs(c - x) < Math.abs(best - x)) {
                best = c;
            }
        }
        return best;
    }

    private List<String> dedupe(List<String> segments) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(segments));
    }

    /**
     * 지금 이 좌표가 속한 구간. 로봇 위치 텔레메트리로 "어디까지 지나갔는지" 알아내
     * 지나간 구간을 반납하는 데 쓴다.
     *
     * @return 구간 ID. 레인 밖(정차 자리 등)이면 null.
     */
    public String segmentAt(double x, double y) {
        if (Math.abs(y - AISLE_Y) < 1.5) {
            for (int i = 0; i < CONNECTOR_X.length - 1; i++) {
                if (x >= CONNECTOR_X[i] - 1.5 && x <= CONNECTOR_X[i + 1] + 1.5) {
                    return String.format("A:%.0f-%.0f", CONNECTOR_X[i], CONNECTOR_X[i + 1]);
                }
            }
            return null;
        }
        double lane = nearestConnector(x);
        if (Math.abs(lane - x) > 2.0) {
            return null; // 레인에서 많이 벗어난 위치(정차 스팟 등)
        }
        return String.format("V:%.0f:%s", lane, y < AISLE_Y ? "up" : "down");
    }

    /** 디버깅·로그용. */
    public String describe(List<String> segments) {
        return Arrays.toString(segments.toArray());
    }
}
