package com.pixelfactory.simulator;

import static com.pixelfactory.simulator.ShiftClock.ShiftPhase.PLANNED_STOP;
import static com.pixelfactory.simulator.ShiftClock.ShiftPhase.RUNNING;
import static com.pixelfactory.simulator.ShiftClock.ShiftPhase.SETUP;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * 경계값 위주 — factory {@code V5__shift_calendars.sql}의 실제 교대 시간(DAY 08:00~17:00,
 * NIGHT 20:00~05:00)과 어긋나면 이 테스트가 먼저 잡는다.
 */
class ShiftClockTest {

    @Test
    void 주간_교대_시작_정각에_SETUP() {
        assertThat(ShiftClock.currentPhase(LocalTime.of(8, 0))).isEqualTo(SETUP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(8, 4, 59))).isEqualTo(SETUP);
    }

    @Test
    void 주간_SETUP_5분_지나면_RUNNING() {
        assertThat(ShiftClock.currentPhase(LocalTime.of(8, 5))).isEqualTo(RUNNING);
    }

    @Test
    void SETUP_직전은_RUNNING() {
        assertThat(ShiftClock.currentPhase(LocalTime.of(7, 59, 59))).isEqualTo(RUNNING);
    }

    @Test
    void 주간_정기점검_창_10시부터_20분() {
        assertThat(ShiftClock.currentPhase(LocalTime.of(9, 59, 59))).isEqualTo(RUNNING);
        assertThat(ShiftClock.currentPhase(LocalTime.of(10, 0))).isEqualTo(PLANNED_STOP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(10, 19, 59))).isEqualTo(PLANNED_STOP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(10, 20))).isEqualTo(RUNNING);
    }

    @Test
    void 점심_휴식_시간대는_시뮬레이터가_별도로_안_다룬다_RUNNING으로_본다() {
        // productionWindows()가 이미 구조적으로 빼므로 여기서는 그냥 RUNNING이면 된다.
        assertThat(ShiftClock.currentPhase(LocalTime.of(12, 30))).isEqualTo(RUNNING);
    }

    @Test
    void 야간_교대_시작_정각에_SETUP() {
        assertThat(ShiftClock.currentPhase(LocalTime.of(20, 0))).isEqualTo(SETUP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(20, 4, 59))).isEqualTo(SETUP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(20, 5))).isEqualTo(RUNNING);
    }

    @Test
    void 야간_정기점검_창_22시부터_20분() {
        assertThat(ShiftClock.currentPhase(LocalTime.of(21, 59, 59))).isEqualTo(RUNNING);
        assertThat(ShiftClock.currentPhase(LocalTime.of(22, 0))).isEqualTo(PLANNED_STOP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(22, 19, 59))).isEqualTo(PLANNED_STOP);
        assertThat(ShiftClock.currentPhase(LocalTime.of(22, 20))).isEqualTo(RUNNING);
    }

    @Test
    void 자정_근처_야간_교대_구간은_RUNNING() {
        // 야식 휴식(0~1시)은 productionWindows()가 구조적으로 처리 — 여기선 RUNNING.
        assertThat(ShiftClock.currentPhase(LocalTime.of(23, 59))).isEqualTo(RUNNING);
        assertThat(ShiftClock.currentPhase(LocalTime.of(0, 30))).isEqualTo(RUNNING);
        assertThat(ShiftClock.currentPhase(LocalTime.of(4, 59))).isEqualTo(RUNNING);
    }

    @Test
    void 교대_사이_공백_시간대도_RUNNING으로_본다() {
        // 05:00~08:00, 17:00~20:00 — shift_calendars에 어느 교대도 없는 시간대(기존부터
        // 있던 특성, 이번 변경과 무관). 시뮬레이터는 계속 돌지만 OeeService가 교대 밖이라
        // 계획가동시간 0으로 걸러낸다.
        assertThat(ShiftClock.currentPhase(LocalTime.of(6, 30))).isEqualTo(RUNNING);
        assertThat(ShiftClock.currentPhase(LocalTime.of(18, 0))).isEqualTo(RUNNING);
    }
}
