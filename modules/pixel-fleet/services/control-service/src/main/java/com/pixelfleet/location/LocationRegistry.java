package com.pixelfleet.location;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 서버가 아는 공장 평면도(44 × 24) — 노드 이름 → 2D 좌표.
 * 배차 정책이 "작업 출발지에서 가장 가까운 로봇"을 고를 때 쓴다.
 *
 * <p><b>중요:</b> 이 좌표는 robot-sim의 NodeMap과 반드시 같아야 한다. 로봇 위치는
 * 시뮬레이터 좌표계로 들어오므로, 양쪽 지도와 미지 노드 폴백이 일치해야만 거리 비교가
 * 의미를 갖는다. 대시보드 types.ts까지 세 곳에 중복돼 있다 — 단일화는 플랫폼 BACKLOG 항목.
 */
@Component
public class LocationRegistry {

    private static final double MAX_X = 44.0;
    private static final double MAX_Y = 24.0;

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

    public double[] resolve(String node) {
        double[] known = NODES.get(node);
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
