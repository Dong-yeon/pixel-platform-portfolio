package com.pixelfactory.oee.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 특정 날짜에 실제로 발생한 교대 구간(달력의 한 칸).
 *
 * <p>{@link ShiftCalendar}는 "매일 08:00~17:00" 같은 규칙이고, 이쪽은 그 규칙이 어느 날에
 * 적용된 결과다. 자정을 넘는 교대는 {@code end}가 다음 날이 된다.
 *
 * <p>핵심은 {@link #productionWindows()}다 — <b>계획가동시간과 실가동시간을 같은 창에서
 * 재도록</b> 만드는 장치다. 분모만 휴식을 빼고 분자는 안 빼면 A가 100%를 넘는다.
 *
 * @param breakStart 휴식 시작. 휴식 없는 교대는 {@code breakEnd}와 함께 null
 */
public record ShiftOccurrence(
        String shiftCode,
        LocalDateTime start,
        LocalDateTime end,
        LocalDateTime breakStart,
        LocalDateTime breakEnd
) {

    /** 시간 구간 한 토막. */
    public record Window(LocalDateTime from, LocalDateTime to) {

        public Duration overlapWith(LocalDateTime otherFrom, LocalDateTime otherTo) {
            LocalDateTime s = from.isAfter(otherFrom) ? from : otherFrom;
            LocalDateTime e = to.isBefore(otherTo) ? to : otherTo;
            return s.isBefore(e) ? Duration.between(s, e) : Duration.ZERO;
        }
    }

    /**
     * 실제로 돌릴 계획이었던 창들 = 교대 구간 − 휴식 구간.
     *
     * <p>휴식이 교대 중간이면 앞/뒤 두 토막이 되고, 휴식이 없으면 한 토막이다.
     * 계획가동시간과 실가동시간을 <b>모두 이 창 안에서</b> 재기 때문에 실가동이 계획을
     * 넘을 수 없다(RUNNING이 창 밖이면 애초에 세지 않는다).
     */
    public List<Window> productionWindows() {
        if (breakStart == null || breakEnd == null) {
            return List.of(new Window(start, end));
        }

        // 휴식이 교대 밖이면(설정 오류이거나 부분 조회) 교대 전체가 생산 창이다.
        if (!breakStart.isBefore(end) || !breakEnd.isAfter(start)) {
            return List.of(new Window(start, end));
        }

        LocalDateTime breakFrom = breakStart.isAfter(start) ? breakStart : start;
        LocalDateTime breakTo = breakEnd.isBefore(end) ? breakEnd : end;

        List<Window> windows = new java.util.ArrayList<>(2);
        if (start.isBefore(breakFrom)) {
            windows.add(new Window(start, breakFrom));
        }
        if (breakTo.isBefore(end)) {
            windows.add(new Window(breakTo, end));
        }

        return windows;
    }

    /** 조회 구간이 이 교대의 생산 창과 겹치는 총 길이 = 이 교대가 기여하는 계획가동시간. */
    public Duration plannedTimeWithin(LocalDateTime from, LocalDateTime to) {
        Duration total = Duration.ZERO;

        for (Window window : productionWindows()) {
            total = total.plus(window.overlapWith(from, to));
        }

        return total;
    }

    /** 조회 구간과 교대 전체(휴식 포함)가 겹치는 길이. 이 교대를 쓸지 판단할 때 쓴다. */
    public Duration overlapWith(LocalDateTime from, LocalDateTime to) {
        return new Window(start, end).overlapWith(from, to);
    }

    public boolean contains(LocalDateTime moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }
}
