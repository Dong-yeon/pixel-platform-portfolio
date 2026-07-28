package com.pixelfleet.traffic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 구간 점유권 관리 — 두 로봇이 같은 레인 구간에 동시에 들어가지 못하게 한다.
 *
 * <p><b>정책: 경로 전체를 한 번에, 전부 아니면 전무로 잡는다.</b> 로봇은 주행을 시작하기
 * 전에 지나갈 모든 구간을 확보하고, 작업이 끝나면 한꺼번에 놓는다.
 *
 * <ul>
 *   <li>장점 — 부분 점유가 없으므로 <b>교착(deadlock)이 원천적으로 생기지 않는다.</b>
 *       (서로 상대가 쥔 구간을 기다리는 상황 자체가 만들어지지 않는다.)</li>
 *   <li>단점 — 보수적이라 동시 주행 대수가 줄어든다. 실제 FMS는 구간을 지나갈 때마다
 *       점진적으로 잡고 놓아 처리량을 높이지만, 그러면 교착 감지·회피가 따로 필요하다.</li>
 * </ul>
 *
 * 지금 규모(AMR 6대)에서는 이 단순한 정책으로 충분하고, 무엇보다 <b>안전이 보장된다</b>.
 */
@Component
public class TrafficController {

    private static final Logger log = LoggerFactory.getLogger(TrafficController.class);

    /** 구간 ID → 점유한 로봇 ID */
    private final Map<String, Long> reservations = new ConcurrentHashMap<>();

    /**
     * 경로 전체를 예약한다. 하나라도 다른 로봇이 쥐고 있으면 <b>아무것도 잡지 않고</b> 실패한다.
     *
     * @return 확보했으면 true
     */
    public synchronized boolean tryReserve(Long robotId, List<String> segments) {
        for (String segment : segments) {
            Long holder = reservations.get(segment);
            if (holder != null && !holder.equals(robotId)) {
                log.debug("Segment {} held by robot {}; robot {} must wait.", segment, holder, robotId);
                return false;
            }
        }
        segments.forEach(segment -> reservations.put(segment, robotId));
        return true;
    }

    /** 로봇이 쥐고 있던 구간을 모두 놓는다(작업 완료·실패·취소 시). */
    public synchronized void release(Long robotId) {
        reservations.entrySet().removeIf(e -> e.getValue().equals(robotId));
    }

    /** 현재 점유 현황(모니터링용 스냅샷). */
    public Map<String, Long> snapshot() {
        return Map.copyOf(reservations);
    }
}
