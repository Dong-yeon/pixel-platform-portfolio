package com.pixelfactory.oee.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixelfactory.oee.domain.ShiftCalendar;
import com.pixelfactory.oee.domain.ShiftOccurrence;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShiftWindowResolverTest {

    private final ShiftWindowResolver resolver = new ShiftWindowResolver();

    /** 주간 08:00~17:00, 휴식 12:00~13:00 → 생산 창 2개(08~12, 13~17), 계획 480분. */
    private static final ShiftCalendar DAY = new ShiftCalendar(
            1L, "DAY", LocalTime.of(8, 0), LocalTime.of(17, 0), LocalTime.of(12, 0), LocalTime.of(13, 0));

    /** 야간 20:00~05:00, 휴식은 자정 넘어 00:00~01:00. */
    private static final ShiftCalendar NIGHT = new ShiftCalendar(
            1L, "NIGHT", LocalTime.of(20, 0), LocalTime.of(5, 0), LocalTime.of(0, 0), LocalTime.of(1, 0));

    @Test
    @DisplayName("주간 교대 하루: 9시간 − 휴식 1시간 = 계획가동 480분")
    void dayShiftPlannedTime() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 30, 8, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 30, 17, 0);

        List<ShiftOccurrence> shifts = resolver.resolve(List.of(DAY), from, to);

        assertThat(shifts).hasSize(1);
        assertThat(resolver.plannedTime(shifts, from, to)).isEqualTo(Duration.ofMinutes(480));
    }

    @Test
    @DisplayName("생산 창이 휴식을 기준으로 두 토막이 된다 — 계획과 실가동을 같은 창에서 재기 위한 것")
    void productionWindowsSplitAroundBreak() {
        ShiftOccurrence day = DAY.occurrenceStartingOn(LocalDateTime.of(2026, 7, 30, 0, 0).toLocalDate());

        List<ShiftOccurrence.Window> windows = day.productionWindows();

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0).from()).isEqualTo(LocalDateTime.of(2026, 7, 30, 8, 0));
        assertThat(windows.get(0).to()).isEqualTo(LocalDateTime.of(2026, 7, 30, 12, 0));
        assertThat(windows.get(1).from()).isEqualTo(LocalDateTime.of(2026, 7, 30, 13, 0));
        assertThat(windows.get(1).to()).isEqualTo(LocalDateTime.of(2026, 7, 30, 17, 0));
    }

    @Test
    @DisplayName("휴식 시간대만 조회하면 계획가동시간이 0이다")
    void breakWindowHasNoPlannedTime() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 30, 12, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 30, 13, 0);

        List<ShiftOccurrence> shifts = resolver.resolve(List.of(DAY), from, to);

        assertThat(resolver.plannedTime(shifts, from, to)).isZero();
    }

    @Test
    @DisplayName("휴식을 걸친 조회는 휴식만 빠진다: 11:00~14:00 → 2시간")
    void plannedTimeExcludesBreakOnly() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 30, 11, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 30, 14, 0);

        List<ShiftOccurrence> shifts = resolver.resolve(List.of(DAY), from, to);

        // 11~12 (1h) + 13~14 (1h) = 2h, 12~13 은 휴식이라 빠진다
        assertThat(resolver.plannedTime(shifts, from, to)).isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("자정을 넘는 야간 교대가 새벽 구간에도 잡힌다 — 전날부터 훑지 않으면 계획가동이 0이 된다")
    void nightShiftCrossingMidnightIsFound() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 31, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 31, 5, 0);

        List<ShiftOccurrence> shifts = resolver.resolve(List.of(NIGHT), from, to);

        assertThat(shifts).hasSize(1);
        assertThat(shifts.get(0).start()).isEqualTo(LocalDateTime.of(2026, 7, 30, 20, 0));
        assertThat(shifts.get(0).end()).isEqualTo(LocalDateTime.of(2026, 7, 31, 5, 0));
        // 00:00~05:00 중 00:00~01:00 이 휴식 → 4시간
        assertThat(resolver.plannedTime(shifts, from, to)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    @DisplayName("야간 교대의 휴식 시각이 자정 넘은 쪽으로 붙는다 — 교대 시작보다 이른 시각은 다음 날")
    void nightShiftBreakResolvesToNextDay() {
        ShiftOccurrence night = NIGHT.occurrenceStartingOn(LocalDateTime.of(2026, 7, 30, 0, 0).toLocalDate());

        assertThat(night.breakStart()).isEqualTo(LocalDateTime.of(2026, 7, 31, 0, 0));
        assertThat(night.breakEnd()).isEqualTo(LocalDateTime.of(2026, 7, 31, 1, 0));
    }

    @Test
    @DisplayName("교대 밖 구간은 계획가동시간이 0이다 — 생산 계획이 없던 시간은 A로 평가하지 않는다")
    void outsideShiftHasNoPlannedTime() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 30, 18, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 30, 19, 0);

        List<ShiftOccurrence> shifts = resolver.resolve(List.of(DAY, NIGHT), from, to);

        assertThat(resolver.plannedTime(shifts, from, to)).isZero();
    }

    @Test
    @DisplayName("현재 교대 찾기: 새벽 02:00 은 전날 시작한 야간 교대에 속한다")
    void currentShiftHandlesMidnightCrossing() {
        ShiftOccurrence shift = resolver.currentShift(
                List.of(DAY, NIGHT), LocalDateTime.of(2026, 7, 31, 2, 0));

        assertThat(shift).isNotNull();
        assertThat(shift.shiftCode()).isEqualTo("NIGHT");
        assertThat(shift.start()).isEqualTo(LocalDateTime.of(2026, 7, 30, 20, 0));
    }

    @Test
    @DisplayName("교대 사이(18:00)에는 현재 교대가 없다")
    void currentShiftIsNullBetweenShifts() {
        assertThat(resolver.currentShift(List.of(DAY, NIGHT), LocalDateTime.of(2026, 7, 30, 18, 0)))
                .isNull();
    }

    @Test
    @DisplayName("주간+야간 하루 전체: 각 교대가 합산되고 총 계획가동은 하루(1440분)보다 작다")
    void bothShiftsAccumulate() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 30, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 31, 0, 0);

        List<ShiftOccurrence> shifts = resolver.resolve(List.of(DAY, NIGHT), from, to);
        Duration planned = resolver.plannedTime(shifts, from, to);

        // 전날 야간의 꼬리(00:00~05:00) + 주간(08:00~17:00) + 당일 야간의 머리(20:00~24:00)
        assertThat(shifts).hasSize(3);
        assertThat(planned).isPositive();
        assertThat(planned).isLessThan(Duration.ofDays(1));
    }
}
