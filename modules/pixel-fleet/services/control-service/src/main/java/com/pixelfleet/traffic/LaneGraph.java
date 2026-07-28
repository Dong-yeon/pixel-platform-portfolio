package com.pixelfleet.traffic;

import com.pixelfleet.location.LocationRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 공장의 주행 레인망. <b>경로는 서버가 소유한다</b> — 로봇이 스스로 길을 정하지 않고
 * 서버가 계산한 웨이포인트를 따라간다. 그래야 서버가 구간 점유를 통제할 수 있다.
 *
 * <p><b>가로 통로가 둘이다.</b> 처음엔 하나였는데, 그러면 거의 모든 경로가 서로 겹쳐
 * 동시 주행이 1~2대로 묶였다(실측). 상단 라인과 하단 라인이 각자 통로를 쓰면 서로
 * 막지 않아 동시 주행이 늘고, 실제 공장 배치에도 가깝다.
 *
 * <pre>
 *      x=3     11    18    25    32    41
 *       │      │     │     │     │     │      ← 세로 연결로
 *  y=5.5       ○A1   ○A2   ○A3   ○A4          ← 상단 하역
 *  y=8.5 ══╪═══╪═════╪═════╪═════╪═════╪══    ← 상단 통로
 *  y=12  [창고]                        [출하]
 *  y=15.5 ═╪═══╪═════╪═════╪═════╪═════╪══    ← 하단 통로
 *  y=18.5      ○B1   ○B2   ○B3   ○B4          ← 하단 하역
 * </pre>
 *
 * <p>구간(segment)은 점유 단위다. 가로는 통로별·연결로 사이 구간(AU/AL), 세로는 통로를
 * 기준으로 위·중간·아래 대역(top/mid/bot)으로 나눈다.
 */
@Component
public class LaneGraph {

    /** 상단 가로 통로 y. */
    public static final double AISLE_UPPER_Y = 8.5;
    /** 하단 가로 통로 y. */
    public static final double AISLE_LOWER_Y = 15.5;
    /** 두 통로의 경계 — 목적지가 어느 쪽 통로를 쓸지 가른다. */
    private static final double MID_Y = (AISLE_UPPER_Y + AISLE_LOWER_Y) / 2;

    /** 세로 연결로가 있는 x좌표들(정렬 상태 유지). */
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
     *
     * <p><b>목적지가 속한 쪽 통로를 탄다</b> — 위쪽이면 상단 통로, 아래쪽이면 하단 통로.
     * 이 규칙은 robot-sim의 NodeMap, 대시보드의 routePoints와 <b>반드시 같아야 한다.</b>
     * 다르면 지도에 그려지는 선과 로봇이 실제로 가는 길이 어긋난다.
     */
    public RoutePlan plan(double[] from, double[] to) {
        double fx = from[0];
        double fy = from[1];
        double tx = to[0];
        double ty = to[1];

        List<double[]> waypoints = new ArrayList<>();
        List<String> segments = new ArrayList<>();

        // 세로로 거의 같은 줄이면 통로를 경유할 필요가 없다.
        if (Math.abs(fx - tx) < 0.6) {
            waypoints.add(new double[]{tx, ty});
            addVertical(segments, nearestConnector(fx), fy, ty);
            return new RoutePlan(waypoints, dedupe(segments));
        }

        double aisleY = ty < MID_Y ? AISLE_UPPER_Y : AISLE_LOWER_Y;
        double fromLane = nearestConnector(fx);
        double toLane = nearestConnector(tx);

        waypoints.add(new double[]{fx, aisleY});
        waypoints.add(new double[]{tx, aisleY});
        waypoints.add(new double[]{tx, ty});

        addVertical(segments, fromLane, fy, aisleY);
        addAisle(segments, aisleY, fromLane, toLane);
        addVertical(segments, toLane, aisleY, ty);

        return new RoutePlan(waypoints, dedupe(segments));
    }

    /** 세로 구간은 통로를 기준으로 위(top)·중간(mid)·아래(bot) 대역으로 나눈다. */
    private void addVertical(List<String> out, double laneX, double y1, double y2) {
        double lo = Math.min(y1, y2);
        double hi = Math.max(y1, y2);
        if (hi - lo < 0.01) {
            return;
        }
        if (lo < AISLE_UPPER_Y) {
            out.add(String.format("V:%.0f:top", laneX));
        }
        if (hi > AISLE_UPPER_Y && lo < AISLE_LOWER_Y) {
            out.add(String.format("V:%.0f:mid", laneX));
        }
        if (hi > AISLE_LOWER_Y) {
            out.add(String.format("V:%.0f:bot", laneX));
        }
    }

    /** 가로 구간은 통로별로 따로 센다(상단과 하단은 서로 다른 구간). */
    private void addAisle(List<String> out, double aisleY, double x1, double x2) {
        String prefix = aisleY == AISLE_UPPER_Y ? "AU" : "AL";
        double lo = Math.min(x1, x2);
        double hi = Math.max(x1, x2);
        for (int i = 0; i < CONNECTOR_X.length - 1; i++) {
            double a = CONNECTOR_X[i];
            double b = CONNECTOR_X[i + 1];
            if (b > lo && a < hi) {
                out.add(String.format("%s:%.0f-%.0f", prefix, a, b));
            }
        }
    }

    /**
     * 지금 이 좌표가 속한 구간. 로봇 위치 텔레메트리로 "어디까지 지나갔는지" 알아내
     * 지나간 구간을 반납하는 데 쓴다.
     *
     * @return 구간 ID. 레인에서 벗어난 위치(정차 자리 등)면 null.
     */
    public String segmentAt(double x, double y) {
        if (Math.abs(y - AISLE_UPPER_Y) < 1.2) {
            return aisleSegmentAt("AU", x);
        }
        if (Math.abs(y - AISLE_LOWER_Y) < 1.2) {
            return aisleSegmentAt("AL", x);
        }
        double lane = nearestConnector(x);
        if (Math.abs(lane - x) > 2.0) {
            return null;
        }
        String band = y < AISLE_UPPER_Y ? "top" : (y > AISLE_LOWER_Y ? "bot" : "mid");
        return String.format("V:%.0f:%s", lane, band);
    }

    private String aisleSegmentAt(String prefix, double x) {
        for (int i = 0; i < CONNECTOR_X.length - 1; i++) {
            if (x >= CONNECTOR_X[i] - 1.5 && x <= CONNECTOR_X[i + 1] + 1.5) {
                return String.format("%s:%.0f-%.0f", prefix, CONNECTOR_X[i], CONNECTOR_X[i + 1]);
            }
        }
        return null;
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
        return new ArrayList<>(new LinkedHashSet<>(segments));
    }

    /** 디버깅·로그용. */
    public String describe(List<String> segments) {
        return Arrays.toString(segments.toArray());
    }
}
