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
        // WH-DOCK-1이 있던 자리(4,3) -> PROD-A1(62,6). P30으로 창고동에 4번째 베이가
        // 생기며(연결로 41) 생산동은 그만큼(+13) 밀렸다 — 코드는 그대로(JCT-9-*, JCT-14-*,
        // JCT-27-* 등)라 실제 x만 다르다.
        // addVertical(4,3,9)=6 + addAisle(4→17→30→41→56→62)=13+13+11+15+6=58
        //   + addVertical(62,9,6)=3 = 67.
        // (30↔62 구간은 4번째 베이 연결로(41)와 게이트(56)를 거친다 — 11+15+6=32,
        // 물리적으로 베이 하나가 더 생겼으니 P22의 "총비용 그대로" 패턴과 달리 실제로 늘어난다.)
        RoutePlan plan = laneGraph.plan(new double[]{4, 3}, new double[]{62, 6});

        assertThat(plan.cost()).isEqualTo(67.0); // P20-5 — 배차 정책이 쓰는 그래프 비용
        assertThat(totalCost(plan.waypoints(), new double[]{4, 3})).isEqualTo(67.0);
        assertThat(plan.waypoints()).containsExactly(
                new double[]{4, 9}, new double[]{62, 9}, new double[]{62, 6});
        assertThat(plan.segments()).containsExactlyInAnyOrder(
                "V:4:top", "AU:4-17", "AU:17-30", "AU:30-41", "AU:41-56", "AU:56-62", "V:62:top");
    }

    @Test
    void 이동중인_로봇의_실좌표에서_출발해도_통로꺾인점의_x는_로봇의_실제_x다() {
        // 로봇이 (20,5)에 있다 — 어느 노드도 아닌 임의의 실시간 좌표. 가장 가까운 연결로는
        // 17이다(|20-17|=3 < |20-30|=10). 목적지는 QC-OUT(P30에서 97,6로 이동).
        //
        // 연결로 17에 WH-RECV(17,6)가 얹혀 있다 — 진입점 탐색이 "이 연결로 위에서
        // 가상 좌표(y=5)보다 위에 있는 가장 가까운 노드"를 찾는데, 그게 교차점(JCT-9-U,
        // y=9)이 아니라 WH-RECV(y=6)다. 그래서 경로가 가상 좌표 → WH-RECV → JCT-9-U를
        // 실제로 거친다(비용은 1+3=4로, 교차점에 바로 이어졌을 때의 |5-9|=4와 우연히 같다).
        RoutePlan plan = laneGraph.plan(new double[]{20, 5}, new double[]{97, 6});

        assertThat(plan.waypoints()).containsExactly(
                new double[]{20, 6}, new double[]{17, 9}, new double[]{97, 9}, new double[]{97, 6});
        assertThat(plan.segments()).contains("V:17:top", "V:97:top");
        assertThat(totalCost(plan.waypoints(), new double[]{20, 5})).isEqualTo(90.0);
    }

    @Test
    void 같은_연결로의_다른_명명노드로는_교차점을_거치지_않고_직행한다() {
        // WH-DOCK-1(4,19)과 WH-DOCK-2(4,20.5) 사이(P29 — 도크 4개가 좌하단 코너로
        // 모였다) — 둘 다 같은 연결로 위, 교차점(y=18)보다 훨씬 가깝다. 명명 노드가
        // 교차점에만 연결돼 있어도(P20-1 데이터), 진입점 탐색이 같은 연결로의 가장
        // 가까운 이웃(교차점이 아니라 다른 명명 노드일 수도 있음)을 우선 찾아야 한다.
        RoutePlan plan = laneGraph.plan(new double[]{4, 20}, locations.resolve("WH-DOCK-2"));

        assertThat(plan.waypoints()).containsExactly(new double[]{4, 20.5});
        assertThat(totalCost(plan.waypoints(), new double[]{4, 20})).isEqualTo(0.5);
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
        // x=20은 이제 연결로 17과 30 사이다(V16 — V15 때는 13과 22 사이였다).
        assertThat(laneGraph.segmentAt(20, 9)).isEqualTo("AU:17-30");
        assertThat(laneGraph.segmentAt(20, 18)).isEqualTo("AL:17-30");
    }

    @Test
    void segmentAt_연결로에서_너무_멀면_null() {
        // x=48 — 연결로 41(JCT-19)·56(게이트) 양쪽에서 다 2.0 넘게 떨어진 자리
        // (|48-41|=7, |48-56|=8).
        assertThat(laneGraph.segmentAt(48, 3)).isNull();
    }

    @Test
    void 엣지가_막히면_그_엣지를_안_쓰고_다른_길로_우회한다() {
        // WH-DOCK-1이 있던 자리 -> PROD-A1의 정상 경로(비용 67)는 상단 통로의 AU:56-62
        // 구간(게이트→JCT-27)을 지난다. 게이트 안쪽(WH-GATE-U↔JCT-27-U)을 막으면 하단
        // 통로를 거쳐서라도(더 길어도) 도착해야 한다 — 아예 못 가면 안 된다.
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("WH-GATE-U", "JCT-27-U"))).thenReturn(true);

        RoutePlan plan = laneGraph.plan(new double[]{4, 3}, new double[]{62, 6});

        assertThat(plan.segments()).doesNotContain("AU:56-62");
        assertThat(plan.waypoints()).contains(new double[]{62, 6}); // 그래도 목적지엔 도달한다
        assertThat(plan.cost()).isGreaterThan(67.0); // P20-5 배차 비교가 이 값을 쓴다
        assertThat(totalCost(plan.waypoints(), new double[]{4, 3})).isGreaterThan(67.0);
    }

    @Test
    void 막히지_않은_엣지는_평소처럼_영향받지_않는다() {
        // 관계없는 엣지 하나를 막아도 다른 경로 계산엔 영향이 없어야 한다(장애물의 범위가
        // 그 엣지 하나로 국한되는지 확인 — 옆 라인까지 통째로 못 쓰게 되면 그건 버그다).
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("JCT-34-U", "JCT-41-U"))).thenReturn(true);

        RoutePlan plan = laneGraph.plan(new double[]{4, 3}, new double[]{62, 6});

        assertThat(totalCost(plan.waypoints(), new double[]{4, 3})).isEqualTo(67.0);
    }

    @Test
    void canonicalEdgeId는_방향과_무관하게_같다() {
        assertThat(LaneGraph.canonicalEdgeId("JCT-14-U", "JCT-27-U"))
                .isEqualTo(LaneGraph.canonicalEdgeId("JCT-27-U", "JCT-14-U"));
    }

    // ---- P25: 통로폭 강제 ----

    @Test
    void passesWidth_적재_시_1400mm_미만은_통과할_수_없다() {
        // 사양서 §4.3 — 적재 시 단일 통로 ≥1400mm. 1200mm는 그 밑이다.
        assertThat(LaneGraph.passesWidth(1200, true)).isFalse();
        assertThat(LaneGraph.passesWidth(1200, false)).isTrue(); // 공차 기준(950mm)은 통과
    }

    @Test
    void passesWidth_경계값은_통과다() {
        // >= 비교이므로 정확히 임계값이면 통과(미만일 때만 막는다).
        assertThat(LaneGraph.passesWidth(1400, true)).isTrue();
        assertThat(LaneGraph.passesWidth(950, false)).isTrue();
        assertThat(LaneGraph.passesWidth(1399.999, true)).isFalse();
    }

    @Test
    void 폴백_엣지는_폭_무제한이라_적재_여부와_무관하게_경로가_그대로다() {
        // LocationRegistry의 폴백 엣지(이 테스트가 쓰는 것 — 클래스 문서 참고)는 전부
        // 2-인자 Edge(to, cost) 생성자를 쓰므로 폭이 무제한이다(UNCONSTRAINED_WIDTH_MM,
        // D3) — 실제 factory 응답(refresh() 경유)에서만 2000mm 실값이 실린다. 여기서는
        // "새 loaded 매개변수가 무제한 엣지에서는 실질적으로 아무것도 안 바꾼다"만 증명한다
        // (설계 근거: docs/p25-robot-spec-routing-design.md 6절) — 실제 2000mm 시드에서도
        // 950/1400보다 넉넉히 크므로 같은 결론이 성립한다(D1 근거, 별도 통합 테스트는 안 둔다).
        RoutePlan unloaded = laneGraph.plan(new double[]{4, 3}, new double[]{62, 6}, false);
        RoutePlan loaded = laneGraph.plan(new double[]{4, 3}, new double[]{62, 6}, true);

        assertThat(loaded.cost()).isEqualTo(unloaded.cost()).isEqualTo(67.0);
        assertThat(loaded.segments()).isEqualTo(unloaded.segments());
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
