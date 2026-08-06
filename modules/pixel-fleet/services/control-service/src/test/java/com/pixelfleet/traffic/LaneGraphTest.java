package com.pixelfleet.traffic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pixelfleet.location.LocationRegistry;
import com.pixelfleet.traffic.LaneGraph.RoutePlan;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * P20-2/P20-4 회귀 검증 — 컴파일타임 고정 규칙(옛 {@code LaneGraph})으로 손으로 계산한
 * 비용·웨이포인트를 그래프 탐색(새 {@code LaneGraph})이 그대로 재현하는지, 그리고 장애물이
 * 있을 때 실제로 우회하는지 확인한다.
 *
 * <p>Spring 컨텍스트 없이 순수 단위 테스트다 — {@link LocationRegistry}는 네트워크 호출 없이
 * 폴백 노드·엣지로 즉시 동작한다(필드 초기화 시점에 채워짐), 이번 리팩터로 처음 생기는
 * 안전망이다(리서치 확인: 이 모듈엔 이전까지 테스트가 하나도 없었다). {@link ObstacleStore}는
 * Redis가 필요해 Mockito로 대체한다 — 스텁하지 않으면 {@code isBlocked}는 기본값 false라
 * 기존 케이스에 영향이 없다.
 */
class LaneGraphTest {

    private final LocationRegistry locations = new LocationRegistry("http://unused:0/api/layout");
    private final ObstacleStore obstacles = Mockito.mock(ObstacleStore.class);
    private final LaneGraph laneGraph = new LaneGraph(locations, obstacles);

    @Test
    void 다른_건물_노드간_경로는_옛_LaneGraph_계산값과_비용이_일치한다() {
        // WH-DOCK-1(4,3) -> PROD-A1(27,6). 옛 LaneGraph.plan(): addVertical(4,3,9)=6
        // + addAisle(9,4->27)=5+5+13=23 + addVertical(27,9,6)=3 = 32.
        RoutePlan plan = laneGraph.plan(new double[]{4, 3}, new double[]{27, 6});

        assertThat(plan.cost()).isEqualTo(32.0); // P20-5 — 배차 정책이 쓰는 그래프 비용
        assertThat(totalCost(plan.waypoints(), new double[]{4, 3})).isEqualTo(32.0);
        assertThat(plan.waypoints()).containsExactly(
                new double[]{4, 9}, new double[]{27, 9}, new double[]{27, 6});
        assertThat(plan.segments()).containsExactlyInAnyOrder(
                "V:4:top", "AU:4-9", "AU:9-14", "AU:14-27", "V:27:top");
    }

    @Test
    void 이동중인_로봇의_실좌표에서_출발해도_통로꺾인점의_x는_로봇의_실제_x다() {
        // 로봇이 (20,5)에 있다 — 어느 노드도 아닌 임의의 실시간 좌표(연결로 14와 27 사이).
        // 옛 LaneGraph: fromLane=nearestConnector(20)=14 였지만 waypoint는 fx=20을 그대로 썼다.
        RoutePlan plan = laneGraph.plan(new double[]{20, 5}, new double[]{62, 6});

        assertThat(plan.waypoints()).containsExactly(
                new double[]{20, 9}, new double[]{62, 9}, new double[]{62, 6});
        // 구간은 스냅된 연결로(14) 기준이라 웨이포인트(실제 x=20)와 값이 다르다 — 옛 동작 그대로다.
        // (그래서 구간 비용 합(=55, |20-14|=6만큼 더 셈)과 실제 웨이포인트 이동거리(=49)가 다르다 —
        // 새 시스템만의 특성이 아니라 옛 LaneGraph도 같은 근사를 썼다.)
        assertThat(plan.segments()).contains("V:14:top", "V:62:top");
        assertThat(totalCost(plan.waypoints(), new double[]{20, 5})).isEqualTo(49.0);
    }

    @Test
    void 같은_연결로의_다른_명명노드로는_교차점을_거치지_않고_직행한다() {
        // WH-DOCK-1(4,3)과 WH-DOCK-2(4,5) 사이 — 둘 다 같은 연결로 위, 교차점(y=9)보다 훨씬 가깝다.
        // 명명 노드가 교차점에만 연결돼 있어도(P20-1 데이터), 진입점 탐색이 같은 연결로의
        // 가장 가까운 이웃(교차점이 아니라 다른 명명 노드일 수도 있음)을 우선 찾아야 한다.
        RoutePlan plan = laneGraph.plan(new double[]{4, 4}, locations.resolve("WH-DOCK-2"));

        assertThat(plan.waypoints()).containsExactly(new double[]{4, 5});
        assertThat(totalCost(plan.waypoints(), new double[]{4, 4})).isEqualTo(1.0);
    }

    @Test
    void 같은_노드면_구간_없이_그_자리_좌표만_돌려준다() {
        double[] pos = locations.resolve("WH-RECV");
        RoutePlan plan = laneGraph.plan(pos.clone(), pos.clone());

        assertThat(plan.waypoints()).containsExactly(pos);
        assertThat(plan.segments()).isEmpty();
    }

    @Test
    void segmentAt_통로위에서는_AU_AL_구간을_돌려준다() {
        assertThat(laneGraph.segmentAt(20, 9)).isEqualTo("AU:14-27");
        assertThat(laneGraph.segmentAt(20, 18)).isEqualTo("AL:14-27");
    }

    @Test
    void segmentAt_연결로에서_너무_멀면_null() {
        assertThat(laneGraph.segmentAt(20, 3)).isNull();
    }

    @Test
    void 엣지가_막히면_그_엣지를_안_쓰고_다른_길로_우회한다() {
        // WH-DOCK-1 -> PROD-A1의 정상 경로(비용 32)는 상단 통로의 AU:14-27 구간을 지난다.
        // 그 엣지를 막으면 하단 통로를 거쳐서라도(더 길어도) 도착해야 한다 — 아예 못 가면 안 된다.
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("JCT-14-U", "JCT-27-U"))).thenReturn(true);

        RoutePlan plan = laneGraph.plan(new double[]{4, 3}, new double[]{27, 6});

        assertThat(plan.segments()).doesNotContain("AU:14-27");
        assertThat(plan.waypoints()).contains(new double[]{27, 6}); // 그래도 목적지엔 도달한다
        assertThat(plan.cost()).isGreaterThan(32.0); // P20-5 배차 비교가 이 값을 쓴다
        assertThat(totalCost(plan.waypoints(), new double[]{4, 3})).isGreaterThan(32.0);
    }

    @Test
    void 막히지_않은_엣지는_평소처럼_영향받지_않는다() {
        // 관계없는 엣지 하나를 막아도 다른 경로 계산엔 영향이 없어야 한다(장애물의 범위가
        // 그 엣지 하나로 국한되는지 확인 — 옆 라인까지 통째로 못 쓰게 되면 그건 버그다).
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("JCT-34-U", "JCT-41-U"))).thenReturn(true);

        RoutePlan plan = laneGraph.plan(new double[]{4, 3}, new double[]{27, 6});

        assertThat(totalCost(plan.waypoints(), new double[]{4, 3})).isEqualTo(32.0);
    }

    @Test
    void canonicalEdgeId는_방향과_무관하게_같다() {
        assertThat(LaneGraph.canonicalEdgeId("JCT-14-U", "JCT-27-U"))
                .isEqualTo(LaneGraph.canonicalEdgeId("JCT-27-U", "JCT-14-U"));
    }

    /** 압축된 웨이포인트를 순서대로 이었을 때의 총 이동 거리(맨해튼 — 모든 다리가 축정렬이므로 유클리드와 같다). */
    private double totalCost(java.util.List<double[]> waypoints, double[] from) {
        double total = 0;
        double[] cursor = from;
        for (double[] point : waypoints) {
            total += Math.abs(point[0] - cursor[0]) + Math.abs(point[1] - cursor[1]);
            cursor = point;
        }
        return total;
    }
}
