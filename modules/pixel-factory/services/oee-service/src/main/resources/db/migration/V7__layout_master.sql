-- 공장 평면도를 마스터로 승격한다.
--
-- **왜 factory가 소유하는가.** 평면도는 공장의 것이지 물류만의 것이 아니다. 설비·하역 지점·
-- (나중에) POP 단말이 모두 같은 바닥 위에 있다. 그래서 factory가 마스터를 갖고, fleet은
-- 필요한 노드 좌표를 REST로 받아 캐시한다 — "DB per module"을 지키면서 중복을 없애는 방법이다.
--
-- **왜 지금인가.** 좌표가 대시보드 types.ts · control-service LocationRegistry ·
-- robot-sim NodeMap 세 곳에 하드코딩돼 있었다. 한 곳만 고치면 배차 거리 비교나 화면 표시가
-- **조용히** 틀어진다. POP 단말·사무실을 얹으면 중복이 5종류가 되므로 그 전에 끊는다.

-- ---- 설비 좌표 ----
-- 지금까지 대시보드가 EQUIPMENT_POSITIONS 로 들고 있던 값을 그대로 옮긴다.
-- 각 설비는 자기 하역 지점(STATION-*) 바로 위/아래에 선다.
alter table equipments add column pos_x double precision;
alter table equipments add column pos_y double precision;

update equipments set pos_x = v.x, pos_y = v.y
from (values
    -- LINE-1 가공 — 하역 지점(STATION-A*) 바로 위
    ('CNC-01', 11.0, 2.4),
    ('CNC-02', 18.0, 2.4),
    ('CNC-03', 25.0, 2.4),
    ('MCT-01', 32.0, 2.4),
    -- LINE-2 조립·검사 — 하역 지점(STATION-B*) 바로 아래
    ('ASM-01', 11.0, 21.6),
    ('ASM-02', 18.0, 21.6),
    ('INS-01', 25.0, 21.6),
    ('PKG-01', 32.0, 21.6)
) as v(code, x, y)
where equipments.equipment_code = v.code;

-- 좌표 없는 설비를 허용하면 지도에서 조용히 사라진다. 시드가 빠진 설비는 여기서 실패한다.
alter table equipments alter column pos_x set not null;
alter table equipments alter column pos_y set not null;

-- ---- 하역 지점·도크 등 노드 ----
create table layout_nodes (
    id bigserial primary key,
    node_code  varchar(30) not null unique,
    name       varchar(50) not null,
    node_type  varchar(20) not null,
    pos_x      double precision not null,
    pos_y      double precision not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into layout_nodes (node_code, name, node_type, pos_x, pos_y, created_at, updated_at) values
    ('DOCK-1',     '1번 충전 도크', 'DOCK',      3,  3,    now(), now()),
    ('DOCK-2',     '2번 충전 도크', 'DOCK',      3,  21,   now(), now()),
    ('WAREHOUSE',  '자재 창고',     'WAREHOUSE', 3,  12,   now(), now()),
    ('STATION-A1', 'A1 하역',       'STATION',   11, 5.5,  now(), now()),
    ('STATION-A2', 'A2 하역',       'STATION',   18, 5.5,  now(), now()),
    ('STATION-A3', 'A3 하역',       'STATION',   25, 5.5,  now(), now()),
    ('STATION-A4', 'A4 하역',       'STATION',   32, 5.5,  now(), now()),
    ('STATION-B1', 'B1 하역',       'STATION',   11, 18.5, now(), now()),
    ('STATION-B2', 'B2 하역',       'STATION',   18, 18.5, now(), now()),
    ('STATION-B3', 'B3 하역',       'STATION',   25, 18.5, now(), now()),
    ('STATION-B4', 'B4 하역',       'STATION',   32, 18.5, now(), now()),
    ('SHIPPING',   '출하장',        'SHIPPING',  41, 12,   now(), now());

-- ---- 평면도 자체의 치수 ----
-- 행이 하나뿐인 테이블이다(싱글톤). 평면도는 "여러 개 중 하나"가 아니라 이 공장의 속성이라
-- 컬럼으로 갖는 게 맞고, id를 1로 못박아 두 번째 행이 생기지 않게 한다.
create table layout_settings (
    id             smallint primary key,
    width          double precision not null,
    height         double precision not null,
    upper_aisle_y  double precision not null,
    lower_aisle_y  double precision not null,
    created_at     timestamp not null,
    updated_at     timestamp not null,
    constraint ck_layout_settings_singleton check (id = 1)
);

-- 통로가 둘인 이유: 하나였을 때는 거의 모든 경로가 겹쳐 동시 주행이 1~2대로 묶였다(실측).
-- 상단은 LINE-1(A열), 하단은 LINE-2(B열)가 쓴다.
insert into layout_settings (id, width, height, upper_aisle_y, lower_aisle_y, created_at, updated_at)
values (1, 44, 24, 8.5, 15.5, now(), now());
