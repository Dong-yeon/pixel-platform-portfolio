package com.pixelfactory.equipment.domain;

/**
 * 설비 상태. <b>OEE의 Availability를 좌우하므로, 각 값이 계획가동시간에 들어가는지가 정의의 핵심이다.</b>
 *
 * <table>
 *   <caption>계획가동시간(planned production time) 취급</caption>
 *   <tr><th>상태</th><th>계획가동</th><th>실가동</th><th>뜻</th></tr>
 *   <tr><td>{@link #RUNNING}</td><td>포함</td><td><b>포함</b></td><td>가공 중</td></tr>
 *   <tr><td>{@link #IDLE}</td><td>포함</td><td>제외</td><td><b>비계획</b> 유휴 — 일감이 없거나 대기</td></tr>
 *   <tr><td>{@link #SETUP}</td><td>포함</td><td>제외</td><td>준비·교체</td></tr>
 *   <tr><td>{@link #DOWN}</td><td>포함</td><td>제외</td><td>고장</td></tr>
 *   <tr><td>{@link #QUALITY_HOLD}</td><td>포함</td><td>제외</td><td>품질 홀드(P14에서 QMS가 건다)</td></tr>
 *   <tr><td>{@link #PLANNED_STOP}</td><td><b>제외</b></td><td>제외</td><td>계획정지 — 애초에 돌릴 계획이 없던 시간</td></tr>
 * </table>
 *
 * <p><b>왜 SETUP을 계획가동시간에 넣는가.</b> 빼면 준비시간이 길어질수록 분모도 같이 줄어
 * A가 오히려 좋아 보인다. 개선해야 할 항목을 지표가 숨기는 셈이라, 포함시켜 A를 깎는다
 * (준비시간 단축이 개선 과제가 되는 이유다).
 *
 * <p><b>PLANNED_STOP만 분모에서 뺀다.</b> 비가동이 계획된 시간(비생산 교대, 정기 점검)은
 * 생산 계획 자체가 없으므로 A로 평가할 대상이 아니다. 휴식시간도 같은 취급이며
 * {@code shift_calendars.break_minutes}로 관리한다.
 */
public enum EquipmentStatus {
    IDLE,
    RUNNING,
    SETUP,
    DOWN,
    QUALITY_HOLD,
    PLANNED_STOP;

    /** 실가동시간(operating time)에 들어가는 상태인가. 현재는 RUNNING만이다. */
    public boolean countsAsOperating() {
        return this == RUNNING;
    }

    /** 계획가동시간(planned production time)에 들어가는 상태인가. PLANNED_STOP만 빠진다. */
    public boolean countsAsPlanned() {
        return this != PLANNED_STOP;
    }
}
