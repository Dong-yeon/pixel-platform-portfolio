package com.pixelfleet.sim.map;

import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 창고동 렉 코드 사본(P21) — factory {@code layout_racks}(V12)와 같은 27기.
 *
 * <p><b>왜 좌표가 아니라 코드만 갖는가.</b> 랙 피더가 렉으로 가는 경로(웨이포인트)는
 * {@code NodeMap}처럼 여기서 계산하지 않는다 — 관제 서버가 이미 접근점을 계산해 GOTO에
 * 실어 보낸다({@code fleet LocationRegistry.rackApproachPoint}, 설계 근거:
 * {@code docs/p21-warehouse-rack-feeder-design.md} D2·D3). 여기는 <b>"이 목적지가 렉인가"만
 * 알면 된다</b> — 렉이면 도착 즉시 완료 보고 대신 취출 타이머를 돈다(Simulator 참고).
 *
 * <p>그래도 코드 집합 자체는 서버 마스터와 어긋나면 안 된다(새 렉이 생겼는데 여기 없으면
 * 그 렉으로 가는 랙 피더 주문이 조용히 "일반 노드처럼" 취급돼 취출 대기 없이 즉시
 * 완료된다) — {@code RackMapLayoutConsistencyTest}가 factory V12 마이그레이션과 대조한다.
 */
@Component
public class RackMap {

    private static final Set<String> RACK_CODES = Set.of(
            "WH-1F-R01", "WH-1F-R02", "WH-1F-R03", "WH-1F-R04", "WH-1F-R05",
            "WH-1F-R06", "WH-1F-R07", "WH-1F-R08", "WH-1F-R09",
            "WH-2F-R01", "WH-2F-R02", "WH-2F-R03", "WH-2F-R04", "WH-2F-R05",
            "WH-2F-R06", "WH-2F-R07", "WH-2F-R08", "WH-2F-R09",
            "WH-3F-R01", "WH-3F-R02", "WH-3F-R03", "WH-3F-R04", "WH-3F-R05",
            "WH-3F-R06", "WH-3F-R07", "WH-3F-R08", "WH-3F-R09"
    );

    public boolean isRackCode(String node) {
        return node != null && RACK_CODES.contains(node);
    }

    /** 서버 마스터와 대조하는 테스트가 쓴다. */
    public Set<String> knownRackCodes() {
        return RACK_CODES;
    }
}
