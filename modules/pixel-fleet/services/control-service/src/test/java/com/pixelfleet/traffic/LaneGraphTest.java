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
    void 다른_건물_노드간_경로는_실제_계산값과_비용이_일치한다() {
        // P32 — 창고동 내부가 좌우 스파인 + 밴드 12개로 바뀌었다. WH-RECV(2,3)는 좌측
        // 스파인의 밴드1 진입 노드(WH-B01-L)에 붙어 있고, 좌→우 스파인을 건너려면 밴드
        // 아이슬(D3에서 배타 잠금을 거는 그 세그먼트, 비용 50) 하나를 반드시 타야 한다.
        // WH-RECV→WH-B01-L(1) → 밴드1 아이슬(50) → WH-B01-R→게이트접속(5) →
        // 접속→WH-GATE-U(4) → WH-GATE-U→JCT-27-U(6) → JCT-27-U→PROD-A1(3) = 69.
        RoutePlan plan = laneGraph.plan(locations.resolve("WH-RECV"), new double[]{62, 6});

        assertThat(plan.cost()).isEqualTo(69.0); // P20-5 — 배차 정책이 쓰는 그래프 비용
        assertThat(totalCost(plan.waypoints(), locations.resolve("WH-RECV"))).isEqualTo(69.0);
        assertThat(plan.segments()).contains("A4:2-52"); // 밴드1 아이슬 — D3 배타 잠금 대상
    }

    @Test
    void 이동중인_로봇의_실좌표에서_출발해도_밴드_아이슬_세그먼트가_밴드마다_구분된다() {
        // 로봇이 (2,6)에 있다 — 어느 노드도 아닌 임의의 실시간 좌표. 좌측 스파인(x=2) 위지만
        // 밴드1 진입(y=4.0)보다 밴드2 진입(y=9.7)에 진입점 탐색이 더 가깝다고 본다(원래
        // 로직이 "가상 좌표보다 위에 있는 가장 가까운 노드"를 우선한다).
        //
        // 핵심 검증: 밴드2 아이슬 세그먼트("A10:2-52")가 밴드1 아이슬("A4:2-52")과 다른
        // 문자열이어야 한다 — 옛 코드는 AU/AL 두 버킷뿐이라 서로 다른 밴드가 같은 세그먼트로
        // 뭉쳐 잠기는 버그가 있었다(이번에 고침).
        RoutePlan plan = laneGraph.plan(new double[]{2, 6}, new double[]{97, 6});

        assertThat(plan.segments()).contains("A10:2-52");
        assertThat(plan.segments()).doesNotContain("A4:2-52");
        assertThat(plan.waypoints()).contains(new double[]{97, 6});
    }

    @Test
    void 같은_밴드_진입_노드를_공유하는_도크끼리는_그_노드를_거쳐_이어진다() {
        // 충전 도크 8개(P32, D4)는 전부 밴드12 좌측 진입 노드(WH-B12-L) 하나에만 붙어
        // 있다 — 옛 4베이 시절처럼 도크끼리 같은 연결로 위에서 교차점 없이 바로 이어지던
        // 배치와 달리, 이제는 별 모양(star) 토폴로지라 항상 WH-B12-L을 거친다.
        RoutePlan plan = laneGraph.plan(locations.resolve("WH-DOCK-1"), locations.resolve("WH-DOCK-5"));

        assertThat(plan.waypoints()).contains(locations.resolve("WH-DOCK-5"));
        // WH-DOCK-1→WH-B12-L(1.8) + WH-B12-L→WH-DOCK-5(3.3) = 5.1(부동소수 오차 허용).
        assertThat(totalCost(plan.waypoints(), locations.resolve("WH-DOCK-1")))
                .isCloseTo(5.1, org.assertj.core.data.Offset.offset(1e-9));
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
        // x=72는 생산동 연결로 69(JCT-34)와 76(JCT-41) 사이 — P32로 창고동 내부가
        // 바뀌어도 생산동 쪽은 무변경이라 그대로다.
        assertThat(laneGraph.segmentAt(72, 9)).isEqualTo("AU:69-76");
        assertThat(laneGraph.segmentAt(72, 18)).isEqualTo("AL:69-76");
    }

    @Test
    void segmentAt_연결로에서_너무_멀면_null() {
        // x=48 — 연결로 41(JCT-19)·56(게이트) 양쪽에서 다 2.0 넘게 떨어진 자리
        // (|48-41|=7, |48-56|=8).
        assertThat(laneGraph.segmentAt(48, 3)).isNull();
    }

    @Test
    void 엣지가_막히면_그_엣지를_안_쓰고_다른_길로_우회한다() {
        // WH-RECV -> PROD-A1의 정상 경로(비용 69)는 상단 게이트 접속(WH-SPINE-R-GATE-U↔
        // WH-GATE-U)을 지난다. 그 구간을 막으면 하단 게이트로라도(더 길어도) 도착해야
        // 한다 — 아예 못 가면 안 된다.
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("WH-SPINE-R-GATE-U", "WH-GATE-U"))).thenReturn(true);

        RoutePlan plan = laneGraph.plan(locations.resolve("WH-RECV"), new double[]{62, 6});

        assertThat(plan.segments()).doesNotContain("AU:52-56");
        assertThat(plan.waypoints()).contains(new double[]{62, 6}); // 그래도 목적지엔 도달한다
        assertThat(plan.cost()).isGreaterThan(69.0); // P20-5 배차 비교가 이 값을 쓴다
        assertThat(totalCost(plan.waypoints(), locations.resolve("WH-RECV"))).isGreaterThan(69.0);
    }

    @Test
    void 막히지_않은_엣지는_평소처럼_영향받지_않는다() {
        // 관계없는 엣지 하나를 막아도 다른 경로 계산엔 영향이 없어야 한다(장애물의 범위가
        // 그 엣지 하나로 국한되는지 확인 — 옆 라인까지 통째로 못 쓰게 되면 그건 버그다).
        when(obstacles.isBlocked(LaneGraph.canonicalEdgeId("JCT-34-U", "JCT-41-U"))).thenReturn(true);

        RoutePlan plan = laneGraph.plan(locations.resolve("WH-RECV"), new double[]{62, 6});

        assertThat(totalCost(plan.waypoints(), locations.resolve("WH-RECV"))).isEqualTo(69.0);
    }

    @Test
    void canonicalEdgeId는_방향과_무관하게_같다() {
        // 순수 문자열 정규화 로직이라 두 인자가 실제 그래프 노드일 필요는 없다.
        assertThat(LaneGraph.canonicalEdgeId("WH-B01-L", "WH-B01-R"))
                .isEqualTo(LaneGraph.canonicalEdgeId("WH-B01-R", "WH-B01-L"));
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
        RoutePlan unloaded = laneGraph.plan(locations.resolve("WH-RECV"), new double[]{62, 6}, false);
        RoutePlan loaded = laneGraph.plan(locations.resolve("WH-RECV"), new double[]{62, 6}, true);

        assertThat(loaded.cost()).isEqualTo(unloaded.cost()).isEqualTo(69.0);
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
