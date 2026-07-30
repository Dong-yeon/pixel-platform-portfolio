package com.pixelfactory.oee.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 라인의 교대 시간표. OEE Availability의 <b>분모(계획가동시간)</b>를 만드는 근거다.
 *
 * <p>시프트 밖 시간은 생산 계획이 없으므로 A로 평가하지 않는다. 시프트 안에서도
 * 휴식 구간은 계획가동시간에서 뺀다.
 *
 * <p><b>휴식은 총 분이 아니라 실제 시각으로 갖는다.</b> 총 분만 있으면 분모에서는 뺄 수
 * 있어도 분자(RUNNING 구간)에서는 뺄 수 없어 실가동 &gt; 계획가동이 되고 A가 100%를 넘는다.
 * 시각으로 가지면 같은 구간 연산이 양쪽에 적용돼 그 모순이 생기지 않는다.
 */
@Getter
@Entity
@Table(name = "shift_calendars")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShiftCalendar extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long lineId;

    @Column(nullable = false, length = 20)
    private String shiftCode;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    /** 휴식 시작. 휴식 없는 교대는 {@code breakEnd}와 함께 null. */
    private LocalTime breakStart;

    private LocalTime breakEnd;

    public ShiftCalendar(
            Long lineId,
            String shiftCode,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime breakStart,
            LocalTime breakEnd
    ) {
        this.lineId = lineId;
        this.shiftCode = shiftCode;
        this.startTime = startTime;
        this.endTime = endTime;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
    }

    /** 자정을 넘어가는 교대인가(야간 20:00~05:00 처럼 {@code endTime <= startTime}). */
    public boolean crossesMidnight() {
        return !endTime.isAfter(startTime);
    }

    /**
     * {@code date}에 시작하는 이 교대의 실제 구간.
     *
     * <p>자정을 넘어가면 종료가 다음 날이다 — 이걸 빼먹으면 야간 교대의 계획가동시간이
     * 0이나 음수가 된다. 휴식 시각도 같은 규칙으로 날짜를 고른다.
     */
    public ShiftOccurrence occurrenceStartingOn(LocalDate date) {
        LocalDateTime start = date.atTime(startTime);
        LocalDateTime end = crossesMidnight() ? date.plusDays(1).atTime(endTime) : date.atTime(endTime);

        LocalDateTime breakFrom = null;
        LocalDateTime breakTo = null;
        if (breakStart != null && breakEnd != null) {
            breakFrom = resolveWithin(date, breakStart, start);
            breakTo = resolveWithin(date, breakEnd, start);
            // 휴식이 자정을 넘는 경우(23:30~00:30)
            if (!breakTo.isAfter(breakFrom)) {
                breakTo = breakTo.plusDays(1);
            }
        }

        return new ShiftOccurrence(shiftCode, start, end, breakFrom, breakTo);
    }

    /**
     * 교대 시작 시각을 기준으로 {@code time}이 같은 날인지 다음 날인지 정한다.
     *
     * <p>야간 교대(20:00 시작)의 휴식 00:00은 <b>다음 날</b> 00:00이다.
     * 시작 시각보다 이르면 다음 날로 본다.
     */
    private LocalDateTime resolveWithin(LocalDate date, LocalTime time, LocalDateTime shiftStart) {
        LocalDateTime sameDay = date.atTime(time);
        return sameDay.isBefore(shiftStart) ? sameDay.plusDays(1) : sameDay;
    }
}
