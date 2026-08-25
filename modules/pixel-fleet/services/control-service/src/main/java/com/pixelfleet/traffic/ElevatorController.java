package com.pixelfleet.traffic;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 엘리베이터 카를 배타적 자원으로 — 한 번에 한 운송만 태운다 (P27).
 *
 * <p>{@link TrafficController}(레인 구간 배타 잠금)와 같은 종류의 구멍을 메운다 — 지금까지
 * 층을 넘는 두 운송이 동시에 발생하면 서로 독립적인 12초 타이머를 각자 받았다(엘리베이터가
 * 무한정 여러 대를 동시에 태우는 셈이었다). 사양서(AMR 사양요구서 §"엘리베이터 연동")의
 * 12단계 핸드셰이크를 문 열림·PLC 신호까지 흉내 내지는 않는다 — 실물 도어 센서가 없는
 * 데이터를 지어내는 것이기 때문이다(설계 근거: docs/p27-battery-tiers-elevator-queue-design.md
 * 0절). 대신 그 프로세스가 실제로 강제하는 물리적 사실만 가져온다.
 *
 * <p><b>왜 "잡았다 놓는다"가 아니라 시간 계산인가.</b> 레인 구간은 "누가 지금 쥐고 있는가"를
 * 실시간으로 알아야 하지만(로봇 위치 텔레메트리로 진행을 본다), 엘리베이터는 왕복 시간이
 * 이미 고정값으로 알려져 있어 예약 시점에 전체 큐를 계산할 수 있다 — 별도 점유/반납
 * 생명주기(로봇이 언제 내렸는지 보고)가 필요 없다. {@code release}가 없는 이유이기도
 * 하다(시간이 지나면 자동으로 다음 예약이 그 뒤를 잇는다).
 *
 * <p>{@link TrafficController}와 같이 인메모리다 — 서버가 재시작되면 큐가 비지만, 그
 * 시점에 실제로 승강 중이던 화물도 없으므로(재시작 자체가 드문 데모 환경) 무해하다.
 */
@Component
public class ElevatorController {

    /**
     * 지금 이 포트폴리오에 실제 구현된 엘리베이터는 창고동 하나뿐이다({@code
     * OrderService.elevatorNode()}의 {@code "WH-"} 하드코딩과 같은 전제) — 새 건물에
     * 엘리베이터가 추가되면 샤프트별로 나눠야 한다(범위 밖, design doc 8절).
     */
    private static final String SHAFT_ID = "WH-ELEVATOR";

    private final Map<String, LocalDateTime> nextFreeAt = new ConcurrentHashMap<>();

    /**
     * 지금 요청하면 언제 도착하는가 — 이미 예약된 마지막 탑승 뒤로 큐잉된다.
     *
     * @return 이 운송이 실제로 승강장에서 인수 가능해지는 시각. 앞선 예약이 없으면
     *         {@code requestedAt + rideSeconds}(큐잉 없음)와 같다.
     */
    public synchronized LocalDateTime reserve(LocalDateTime requestedAt, int rideSeconds) {
        LocalDateTime start = nextFreeAt.getOrDefault(SHAFT_ID, requestedAt);
        if (start.isBefore(requestedAt)) {
            start = requestedAt;
        }
        LocalDateTime arrival = start.plusSeconds(rideSeconds);
        nextFreeAt.put(SHAFT_ID, arrival);
        return arrival;
    }
}
