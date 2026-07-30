-- 이벤트 "발생시각"을 도입한다.
--
-- created_at은 **서버가 기록한 시각**이다. 브로커 지연·서비스 다운 후 밀린 메시지 처리·
-- 재처리가 있으면 실제 설비에서 일어난 시각과 크게 벌어진다. OEE는 상태 구간의 길이로
-- 계산하므로 그 차이가 곧 지표 오차가 된다. 그래서 설비가 보낸 시각(payload의 ts)을
-- 별도 컬럼으로 보존한다.
--
-- created_at은 그대로 남긴다(적재 시각 감사용). 둘의 차이가 곧 파이프라인 지연이다.

alter table factory_events add column occurred_at timestamp;

-- 기존 행은 발생시각을 알 수 없다. 적재 시각으로 백필한다 —
-- 그 시점엔 지연이 작았다는 가정이며, 이 시점 이전 데이터의 한계다.
update factory_events set occurred_at = created_at where occurred_at is null;

alter table factory_events alter column occurred_at set not null;

-- 설비 단위 OEE 집계용. 지금은 created_at 인덱스뿐이라 설비별 기간 조회가 풀스캔이다.
-- 컬럼 순서는 조회 형태(설비 지정 → 이벤트 종류 필터 → 기간 범위)를 따른다.
create index idx_factory_events_target_type_time
    on factory_events (target_type, target_id, event_type, occurred_at desc);

-- 타임라인 조회도 발생시각 기준으로 바뀌므로 정렬용 인덱스를 함께 만든다.
create index idx_factory_events_occurred_at on factory_events (occurred_at desc);
