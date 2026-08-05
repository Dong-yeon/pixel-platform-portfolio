-- 운송 작업 → 다단 스텝 주문 (P19: Fleet API를 M4 모양으로).
--
-- 지금까지 운송은 "출발 → 도착" 단일 이송이었고, 픽업/하역(leg1/leg2)과 엘리베이터
-- 인수인계가 전부 서비스 코드의 특수 처리로 붙어 있었다. 실물 관제 서버(M4)의 주문은
-- **스텝 리스트**다 — 지점마다 싣거나(forLoad) 내리는(forUnload) 동작이 달리고,
-- 주문을 봉인(step_fixed)하기 전까지는 스텝을 이어 붙일 수 있다. 그 모양을 기본형으로
-- 삼는다. leg1/leg2는 "스텝 2개짜리 주문"의 특수한 경우로 녹는다.
--
-- 상태는 M4를 따른다: TO_BE_ALLOCATED → ALLOCATED → EXECUTING → DONE / CANCELLED,
-- 그리고 PENDING(미봉인 주문이 스텝을 소진하고 다음 스텝을 기다리는 상태).
-- **FAILED 상태가 없다** — M4처럼 fault 플래그로 얼리고(자동 재시도 소진 시),
-- retry-failed 동사로 되살린다. 실패는 상태가 아니라 상태에 얹힌 표식이다.

create table fleet_orders (
    id                  bigserial primary key,
    order_code          varchar(50) not null unique,
    -- 상류(WMS 등)가 자기 전표 번호를 실어 보내는 자리. 완료/실패 통지의 열쇠이며,
    -- 층간 체인으로 주문이 쪼개져도 체인 전체가 같은 값을 물려받는다(그래서 unique 아님).
    external_id         varchar(50),
    -- M4처럼 정수 — 클수록 높다. 0=LOW 1=NORMAL 2=HIGH 3=URGENT 관례.
    priority            integer not null default 1,
    status              varchar(20) not null,
    assigned_robot_id   bigint references robots (id),
    floor_no            smallint not null default 1,
    -- -1 = 아직 시작 전. 그 외엔 지금 향하고 있거나 실행 중인 스텝.
    current_step_index  integer not null default -1,
    loaded              boolean not null default false,
    -- 봉인. false면 마지막 스텝을 마쳐도 닫히지 않고 PENDING으로 다음 스텝을 기다린다.
    step_fixed          boolean not null default true,
    suspended           boolean not null default false,
    -- 자동 재시도 예산 소진 — 상태를 얼린 채 사람의 retry-failed를 기다린다.
    fault               boolean not null default false,
    failure_num         integer not null default 0,
    failure_reason      varchar(500),
    -- 층간 체인(로봇은 층을 못 넘는다 — 화물만 엘리베이터를 탄다)
    handoff_destination varchar(30),
    handoff_of          varchar(50),
    available_at        timestamp,
    assigned_at         timestamp,
    started_at          timestamp,
    -- 워치독 기준. 스텝을 마칠 때마다 갱신되므로 스텝 많은 주문이 주행 타임아웃에
    -- 억울하게 걸리지 않는다(예전엔 시작 시각 하나로 300초를 쟀다).
    last_progress_at    timestamp,
    finished_at         timestamp,
    created_at          timestamp not null,
    updated_at          timestamp not null
);
create index idx_fleet_orders_status on fleet_orders (status);
create index idx_fleet_orders_robot_status on fleet_orders (assigned_robot_id, status);

create table fleet_order_steps (
    id            bigserial primary key,
    order_id      bigint not null references fleet_orders (id) on delete cascade,
    step_index    integer not null,
    location_node varchar(30) not null,
    for_load      boolean not null default false,
    for_unload    boolean not null default false,
    status        varchar(20) not null default 'EXECUTABLE',
    started_at    timestamp,
    finished_at   timestamp,
    created_at    timestamp not null,
    updated_at    timestamp not null,
    constraint uq_order_step unique (order_id, step_index)
);
create index idx_order_steps_order on fleet_order_steps (order_id);

-- ---- 기존 운송 작업을 2스텝 주문으로 변환 (ID 보존) ----
-- fleet_events.task_id 가 FK 없이 작업 id를 가리킨다. ID를 보존하면 그 이력이
-- 그대로 "주문 id"로 읽혀 이벤트 로그를 한 줄도 손대지 않는다.
--
-- 진행 중(ASSIGNED/IN_PROGRESS)이던 작업은 재큐한다 — leg2 계획·픽업 도착 같은
-- 대기 상태가 서비스 메모리에만 있어서 마이그레이션으로 살릴 수 없다.
-- FAILED(최종 실패)는 CANCELLED로 닫되 fault를 남겨 감사 흔적을 보존한다.
insert into fleet_orders (id, order_code, external_id, priority, status, assigned_robot_id,
    floor_no, current_step_index, loaded, step_fixed, suspended, fault, failure_num,
    failure_reason, handoff_destination, handoff_of, available_at, assigned_at, started_at,
    last_progress_at, finished_at, created_at, updated_at)
select id, task_code, task_code,
    case priority when 'LOW' then 0 when 'NORMAL' then 1 when 'HIGH' then 2 else 3 end,
    case status
        when 'PENDING'     then 'TO_BE_ALLOCATED'
        when 'ASSIGNED'    then 'TO_BE_ALLOCATED'
        when 'IN_PROGRESS' then 'TO_BE_ALLOCATED'
        when 'COMPLETED'   then 'DONE'
        else                    'CANCELLED'
    end,
    case when status in ('ASSIGNED', 'IN_PROGRESS') then null else assigned_robot_id end,
    floor_no,
    case when status = 'COMPLETED' then 1 else -1 end,
    false, true, false,
    (status = 'FAILED'),
    retry_count, failure_reason,
    handoff_destination, handoff_of, available_at,
    case when status in ('ASSIGNED', 'IN_PROGRESS') then null else assigned_at end,
    case when status in ('ASSIGNED', 'IN_PROGRESS') then null else started_at end,
    null, finished_at, created_at, updated_at
from transport_tasks;

select setval('fleet_orders_id_seq', (select coalesce(max(id), 1) from fleet_orders));

insert into fleet_order_steps
    (order_id, step_index, location_node, for_load, for_unload, status, created_at, updated_at)
select id, 0, origin_node, true, false,
    case when status = 'COMPLETED' then 'DONE'
         when status in ('FAILED', 'CANCELLED') then 'CANCELLED'
         else 'EXECUTABLE' end,
    created_at, updated_at
from transport_tasks
union all
select id, 1, destination_node, false, true,
    case when status = 'COMPLETED' then 'DONE'
         when status in ('FAILED', 'CANCELLED') then 'CANCELLED'
         else 'EXECUTABLE' end,
    created_at, updated_at
from transport_tasks;

drop table transport_tasks;
