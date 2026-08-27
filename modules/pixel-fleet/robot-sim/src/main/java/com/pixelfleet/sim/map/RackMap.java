package com.pixelfleet.sim.map;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 창고동 렉 코드 사본 — factory {@code layout_racks}와 같은 908기(1층 860 + 2·3층 각 24,
 * P32로 1층만 전면 재발번, P32 D9로 충전존과 겹치던 4기 제외).
 *
 * <p><b>왜 좌표가 아니라 코드만 갖는가.</b> AGV(옛 이름: 랙 피더)가 렉으로 가는 경로(웨이포인트)는
 * {@code NodeMap}처럼 여기서 계산하지 않는다 — 관제 서버가 이미 접근점을 계산해 GOTO에
 * 실어 보낸다({@code fleet LocationRegistry.rackApproachPoint}, 설계 근거:
 * {@code docs/p21-warehouse-rack-feeder-design.md} D2·D3). 여기는 <b>"이 목적지가 렉인가"만
 * 알면 된다</b> — 렉이면 도착 즉시 완료 보고 대신 취출 타이머를 돈다(Simulator 참고).
 *
 * <p><b>왜 1층은 리스트가 아니라 생성 루프인가(P32).</b> 860개를 문자열로 나열하면 파일
 * 하나가 화면 밖으로 넘어가고, 손으로 옮겨 적다 하나라도 틀리면 조용히 어긋난다. factory
 * {@code V22__warehouse_band_relayout.sql}(+ D9 제외분은 {@code V23})이 쓰는 것과
 * <b>정확히 같은 공식</b>(밴드 12개 × 열 72 — 열 1~36이 앞줄, 37~72가 뒷줄, 밴드12의
 * 37~40만 제외)으로 코드만 재생성한다(좌표는 이 클래스가 안 가지므로 코드 문자열만
 * 맞으면 된다). 공식이 바뀌면 두 곳(factory 마이그레이션·여기)을 같이 고쳐야 하고,
 * {@code RackMapLayoutConsistencyTest}가 어긋남을 잡는다.
 *
 * <p>2·3층은 D5(무변경)에 따라 예전처럼 리터럴로 남긴다 — P28(27기 증설)·P30(4번째 베이
 * 18기 증설) 이후 그대로다.
 *
 * <p>그래도 코드 집합 자체는 서버 마스터와 어긋나면 안 된다(새 렉이 생겼는데 여기 없으면
 * 그 렉으로 가는 AGV 주문이 조용히 "일반 노드처럼" 취급돼 취출 대기 없이 즉시
 * 완료된다) — {@code RackMapLayoutConsistencyTest}가 factory V12+V19+V21+V22+V23
 * 마이그레이션과 대조한다.
 */
@Component
public class RackMap {

    /** P32 밴드 구조 상수 — factory V22 마이그레이션과 반드시 같아야 한다. */
    private static final int BANDS = 12;
    /** 밴드당 렉 수(앞줄 36 + 뒷줄 36). */
    private static final int RACKS_PER_BAND = 72;
    /**
     * P32 D9(V23) — 밴드12 뒷줄 앞쪽 4칸이 충전존(CZ-1F, x∈[1,8])과 좌표상 겹쳐서 제외한다.
     * factory V23이 이 4개 코드의 {@code layout_racks} 행을 DELETE한다.
     */
    private static final int EXCLUDED_BAND = 12;
    private static final Set<Integer> EXCLUDED_COLS = Set.of(37, 38, 39, 40);

    private static final Set<String> RACK_CODES = buildRackCodes();

    private static Set<String> buildRackCodes() {
        Set<String> codes = new HashSet<>();
        // ---- 창고동 1층 — 밴드 12개 × 열 72(P32), 충전존과 겹치는 4칸 제외(D9) ----
        for (int band = 1; band <= BANDS; band++) {
            for (int col = 1; col <= RACKS_PER_BAND; col++) {
                if (band == EXCLUDED_BAND && EXCLUDED_COLS.contains(col)) {
                    continue;
                }
                codes.add(String.format("WH-1F-B%02d-R%02d", band, col));
            }
        }
        // ---- 창고동 2·3층 — D5(무변경), P28/P30 그대로 ----
        for (int i = 1; i <= 24; i++) {
            codes.add(String.format("WH-2F-R%02d", i));
            codes.add(String.format("WH-3F-R%02d", i));
        }
        return Set.copyOf(codes);
    }

    public boolean isRackCode(String node) {
        return node != null && RACK_CODES.contains(node);
    }

    /** 서버 마스터와 대조하는 테스트가 쓴다. */
    public Set<String> knownRackCodes() {
        return RACK_CODES;
    }
}
