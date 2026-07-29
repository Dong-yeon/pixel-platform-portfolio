package com.pixelfleet.traffic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 구간 점유권 관리 — 두 로봇이 같은 레인 구간에 동시에 들어가지 못하게 한다.
 *
 * <p><b>정책: 한 구간(leg) 단위로 전부 아니면 전무로 잡고, 지나간 구간은 곧바로 놓는다.</b>
 *
 * <ul>
 *   <li><b>leg 단위 일괄 확보</b> — 한 leg의 구간을 전부 잡거나 하나도 안 잡는다.
 *       부분 확보가 없어 한 leg 안에서는 서로 기다릴 일이 없다.</li>
 *   <li><b>점진 반납</b> — 지나간 구간은 즉시 풀어 준다({@link #progress}). 뒤따르는 로봇이
 *       바로 쓸 수 있어 동시 주행 대수가 올라간다.</li>
 *   <li><b>leg 경계에서 선반납</b> — 픽업에 도착하면 leg1을 <b>전부 놓은 뒤</b> leg2를 요청한다
 *       (TaskService#markPickedUp). 쥔 채로 요청하면(hold-and-wait) 여러 로봇이 서로가 쥔
 *       구간을 기다려 <b>교착에 빠진다</b> — 실제로 그렇게 만들었다가 로봇 4대가 픽업에서
 *       모두 멈춰 아무 작업도 완료되지 않았다. 선반납이 이 구조의 교착 안전성을 지탱한다.</li>
 * </ul>
 */
@Component
public class TrafficController {

    private static final Logger log = LoggerFactory.getLogger(TrafficController.class);

    /** 구간 ID → 점유한 로봇 ID */
    private final Map<String, Long> reservations = new ConcurrentHashMap<>();

    /** 로봇 ID → 아직 지나지 않은 구간(경로 순서 유지). 점진 반납의 기준이 된다. */
    private final Map<Long, List<String>> remainingRoute = new ConcurrentHashMap<>();

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
        remainingRoute.put(robotId, new ArrayList<>(segments));
        return true;
    }

    /**
     * 로봇이 지금 {@code currentSegment}에 있다는 보고를 받아, <b>그보다 앞선 구간들을 반납</b>한다.
     * 위치 텔레메트리가 올 때마다 호출된다.
     *
     * <p>현재 구간이 경로에 없거나(레인 밖) 아직 첫 구간이면 아무것도 하지 않는다.
     */
    public synchronized void progress(Long robotId, String currentSegment) {
        if (robotId == null || currentSegment == null) {
            return;
        }
        List<String> remaining = remainingRoute.get(robotId);
        if (remaining == null) {
            return;
        }
        int idx = remaining.indexOf(currentSegment);
        if (idx <= 0) {
            return; // 경로에 없거나 이미 맨 앞 — 놓을 게 없다
        }
        // 현재 구간 이전의 것들은 이미 통과했다.
        List<String> passed = new ArrayList<>(remaining.subList(0, idx));
        passed.forEach(segment -> reservations.remove(segment, robotId));
        remaining.subList(0, idx).clear();
        log.debug("Robot {} passed {} — released.", robotId, passed);
    }

    /** 로봇이 쥐고 있던 구간을 모두 놓는다(작업 완료·실패·취소 시). */
    public synchronized void release(Long robotId) {
        reservations.entrySet().removeIf(e -> e.getValue().equals(robotId));
        remainingRoute.remove(robotId);
    }

    /** 현재 점유 현황(모니터링용 스냅샷). */
    public Map<String, Long> snapshot() {
        return new LinkedHashMap<>(reservations);
    }
}
