package com.pixelfactory.oee.service;

import com.pixelfactory.oee.domain.ShiftCalendar;
import com.pixelfactory.oee.domain.ShiftOccurrence;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 교대 규칙({@link ShiftCalendar})을 조회 구간에 펼쳐 실제 교대 구간들로 만든다.
 *
 * <p>순수 함수다 — 달력 규칙과 구간만 받는다.
 */
@Component
public class ShiftWindowResolver {

    /**
     * 조회 구간과 겹치는 모든 교대 발생을 시간순으로 돌려준다.
     *
     * <p>{@code from}의 <b>하루 전</b>부터 훑는다. 자정을 넘는 야간 교대(20:00~05:00)는
     * 전날 시작해 조회 구간에 걸칠 수 있어서다 — 이걸 빼면 새벽 구간의 계획가동시간이 0이 된다.
     */
    public List<ShiftOccurrence> resolve(List<ShiftCalendar> calendars, LocalDateTime from, LocalDateTime to) {
        List<ShiftOccurrence> occurrences = new ArrayList<>();

        LocalDate firstDate = from.toLocalDate().minusDays(1);
        LocalDate lastDate = to.toLocalDate();

        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            for (ShiftCalendar calendar : calendars) {
                ShiftOccurrence occurrence = calendar.occurrenceStartingOn(date);
                if (!occurrence.overlapWith(from, to).isZero()) {
                    occurrences.add(occurrence);
                }
            }
        }

        occurrences.sort(Comparator.comparing(ShiftOccurrence::start));
        return occurrences;
    }

    /**
     * 조회 구간의 계획가동시간 = Σ(각 교대의 생산 창 ∩ 조회 구간).
     *
     * <p>생산 창은 교대에서 휴식을 뺀 것이다({@link ShiftOccurrence#productionWindows()}).
     * 실가동시간도 <b>같은 창에서</b> 재므로 실가동이 계획을 넘을 수 없다.
     *
     * <p>{@code PLANNED_STOP} 구간은 여기서 빼지 않는다 — 상태 구간을 가진
     * {@code OeeService}가 뺀다. 이 클래스는 달력만 안다.
     */
    public Duration plannedTime(List<ShiftOccurrence> occurrences, LocalDateTime from, LocalDateTime to) {
        Duration total = Duration.ZERO;

        for (ShiftOccurrence occurrence : occurrences) {
            total = total.plus(occurrence.plannedTimeWithin(from, to));
        }

        return total;
    }

    /** {@code moment}를 품는 교대. 없으면 빈 값(시프트 밖 시각). */
    public ShiftOccurrence currentShift(List<ShiftCalendar> calendars, LocalDateTime moment) {
        LocalDate today = moment.toLocalDate();

        for (LocalDate date : List.of(today.minusDays(1), today)) {
            for (ShiftCalendar calendar : calendars) {
                ShiftOccurrence occurrence = calendar.occurrenceStartingOn(date);
                if (occurrence.contains(moment)) {
                    return occurrence;
                }
            }
        }

        return null;
    }
}
