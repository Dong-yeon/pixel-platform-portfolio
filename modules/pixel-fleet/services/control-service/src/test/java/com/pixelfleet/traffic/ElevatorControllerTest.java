package com.pixelfleet.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * P27 — 엘리베이터 카 배타 예약 회귀 검증. {@link TrafficController}의 구간 배타 잠금과
 * 같은 종류의 규칙(한 번에 하나만)을 시간 계산으로 검증한다.
 */
class ElevatorControllerTest {

    private final ElevatorController elevator = new ElevatorController();

    @Test
    void 앞선_예약이_없으면_큐잉_없이_요청시각_뒤_왕복시간에_도착한다() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

        LocalDateTime arrival = elevator.reserve(requestedAt, 12);

        assertThat(arrival).isEqualTo(requestedAt.plusSeconds(12));
    }

    @Test
    void 겹치는_두_요청은_두번째가_첫번째_도착_이후로_밀린다() {
        LocalDateTime firstRequest = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        LocalDateTime firstArrival = elevator.reserve(firstRequest, 12);

        // 첫 번째가 아직 도착하기 전(3초 후)에 두 번째가 같은 엘리베이터를 요청한다.
        LocalDateTime secondRequest = firstRequest.plusSeconds(3);
        LocalDateTime secondArrival = elevator.reserve(secondRequest, 12);

        assertThat(secondArrival).isEqualTo(firstArrival.plusSeconds(12));
        assertThat(secondArrival).isAfter(secondRequest.plusSeconds(12)); // 큐잉이 실제로 있었다
    }

    @Test
    void 앞선_예약이_이미_끝난_뒤_요청이면_큐잉되지_않는다() {
        LocalDateTime firstRequest = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        elevator.reserve(firstRequest, 12);

        // 첫 번째 왕복(12초)이 끝난 지 한참 뒤 — 큐가 없어야 한다.
        LocalDateTime secondRequest = firstRequest.plusSeconds(60);
        LocalDateTime secondArrival = elevator.reserve(secondRequest, 12);

        assertThat(secondArrival).isEqualTo(secondRequest.plusSeconds(12));
    }
}
