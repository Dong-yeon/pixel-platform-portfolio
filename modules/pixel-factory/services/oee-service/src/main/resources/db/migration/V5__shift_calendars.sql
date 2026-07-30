-- 시프트(교대) 캘린더 — OEE Availability의 **분모**를 만들기 위한 마스터.
--
-- 계획가동시간이 정의되지 않으면 A를 계산할 수 없다. "언제 돌릴 계획이었나"를 알려주는
-- 것이 이 테이블이다. 시프트 밖 시간은 애초에 생산 계획이 없으므로 A로 평가하지 않는다.
--
-- **휴식은 총 분(minutes)이 아니라 실제 시각으로 갖는다.**
-- 처음엔 break_minutes(총합)로 뒀는데, 그러면 분모에서는 뺄 수 있어도 분자(RUNNING 구간)에서는
-- 뺄 수가 없다 — 언제가 휴식인지 모르니까. 그 상태로 계산하면 휴식 중에도 돌아간 설비가
-- 실가동 > 계획가동이 되어 **A가 100%를 넘는다**(실제로 109%가 나왔다).
-- 시각으로 가지면 구간 연산이 양쪽에 똑같이 적용돼 그런 모순이 생길 수 없다.

create table shift_calendars (
    id bigserial primary key,
    line_id     bigint not null references production_lines (id),
    shift_code  varchar(20) not null,
    start_time  time not null,
    end_time    time not null,
    -- 휴식 구간. 없는 교대는 둘 다 null. 자정을 넘는 교대는 이 시각도 넘어갈 수 있다.
    break_start time,
    break_end   time,
    created_at  timestamp not null,
    updated_at  timestamp not null,
    constraint uk_shift_calendars_line_shift unique (line_id, shift_code),
    constraint ck_shift_calendars_break_pair
        check ((break_start is null) = (break_end is null))
);

create index idx_shift_calendars_line_id on shift_calendars (line_id);

-- 2교대 시드. 야간은 자정을 넘어간다(end_time < start_time) — 계산기가 이 경우를 다룬다.
-- 야간의 휴식(00:00~01:00)도 자정 넘은 쪽에 있다.
insert into shift_calendars (line_id, shift_code, start_time, end_time, break_start, break_end, created_at, updated_at)
select l.id, s.shift_code, s.start_time, s.end_time, s.break_start, s.break_end, now(), now()
from production_lines l
cross join (values
    ('DAY',   time '08:00', time '17:00', time '12:00', time '13:00'),
    ('NIGHT', time '20:00', time '05:00', time '00:00', time '01:00')
) as s(shift_code, start_time, end_time, break_start, break_end);
