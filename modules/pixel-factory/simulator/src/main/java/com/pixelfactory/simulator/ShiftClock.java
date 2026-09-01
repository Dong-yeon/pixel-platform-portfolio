package com.pixelfactory.simulator;

import java.time.Duration;
import java.time.LocalTime;

/**
 * 지금 벽시계 시각이 교대의 어느 단계인지 판정한다(P15-2).
 *
 * <p><b>왜 필요한가.</b> 시뮬레이터는 원래 교대 개념 없이 무한히 "사이클→불량 판정→
 * 가끔 고장"만 반복했다 — {@code RUNNING}/{@code DOWN}/{@code IDLE}(종료 시)만 있었고
 * {@code SETUP}/{@code PLANNED_STOP}은 한 번도 발행된 적이 없었다. 그 결과 OEE의
 * 가동률(A)이 항상 노이즈 수준으로만 흔들려 그래프가 86% 근처에 평평하게 붙었다
 * (설계 근거: docs/pixel-platform-roadmap.md P10/P15).
 *
 * <p><b>oee-service 쪽은 이미 다 준비돼 있다.</b> {@code EquipmentStatus}가 SETUP은
 * 계획가동시간(분모)엔 포함하고 실가동(분자)엔 안 잡아 A를 깎고, PLANNED_STOP은
 * 분모·분자 양쪽에서 뺀다 — 이 클래스는 그 상태를 <b>언제</b> 발행해야 하는지만
 * 판정한다. 실제로 상태를 반영하는 로직은 그대로(코드 변경 없음)다.
 *
 * <p><b>기준 시각은 하드코딩이다.</b> factory `V5__shift_calendars.sql`이 시드하는
 * 실제 교대(DAY 08:00~17:00, NIGHT 20:00~05:00, 각 휴식 1시간)와 반드시 같아야 한다 —
 * 그 마이그레이션이 바뀌면 여기도 손으로 맞춰야 한다(로봇 좌표 정합성 테스트 같은
 * 자동 대조 장치는 숫자 4쌍뿐이라 이번엔 과하다고 보고 안 만들었다).
 *
 * <p><b>점심·야식 휴식은 여기서 다루지 않는다.</b> {@code ShiftOccurrence.productionWindows()}가
 * 휴식 시간대를 계획가동시간에서 이미 구조적으로 빼기 때문에(그 시간대 실제 설비
 * 상태가 무엇이든 상관없다), PLANNED_STOP을 휴식과 겹치게 낼 필요가 없다 — 오히려
 * 겹치면 같은 시간을 두 번 빼는 셈이라 안 쓴다. PLANNED_STOP은 휴식과 별개인,
 * 교대 중 정기 점검 창을 표현한다.
 */
final class ShiftClock {

    enum ShiftPhase { SETUP, PLANNED_STOP, RUNNING }

    // ---- V5__shift_calendars.sql 기준 — DAY 08:00~17:00, NIGHT 20:00~05:00 ----
    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime NIGHT_START = LocalTime.of(20, 0);

    /** 교대 시작 직후 준비시간. */
    private static final Duration SETUP_DURATION = Duration.ofMinutes(5);

    /**
     * 정기 점검 창 — 교대 시작 2시간 뒤(점심 12~13시·야식 0~1시 휴식과 안 겹치는
     * 자리)부터 20분.
     */
    private static final Duration PLANNED_STOP_OFFSET = Duration.ofHours(2);
    private static final Duration PLANNED_STOP_DURATION = Duration.ofMinutes(20);

    private ShiftClock() {
    }

    static ShiftPhase currentPhase(LocalTime now) {
        if (inWindow(now, DAY_START, SETUP_DURATION) || inWindow(now, NIGHT_START, SETUP_DURATION)) {
            return ShiftPhase.SETUP;
        }
        LocalTime dayStopStart = DAY_START.plus(PLANNED_STOP_OFFSET);
        LocalTime nightStopStart = NIGHT_START.plus(PLANNED_STOP_OFFSET);
        if (inWindow(now, dayStopStart, PLANNED_STOP_DURATION) || inWindow(now, nightStopStart, PLANNED_STOP_DURATION)) {
            return ShiftPhase.PLANNED_STOP;
        }
        return ShiftPhase.RUNNING;
    }

    /** [start, start+duration) 안에 now가 있는가 — 자정을 넘는 창도 방어적으로 처리한다. */
    private static boolean inWindow(LocalTime now, LocalTime start, Duration duration) {
        LocalTime end = start.plus(duration);
        if (!end.isBefore(start)) { // 자정 안 넘음(보통 경우)
            return !now.isBefore(start) && now.isBefore(end);
        }
        // start+duration이 자정을 넘어 end가 start보다 이른 시각으로 보이는 경우.
        return !now.isBefore(start) || now.isBefore(end);
    }
}
