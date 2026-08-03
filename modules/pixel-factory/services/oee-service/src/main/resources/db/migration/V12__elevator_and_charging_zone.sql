-- 창고동 엘리베이터 + 충전존 + 층별 노드.
--
-- **왜 고치는가 (세 가지 실제 문제)**
--  1. 위층 재고가 어떻게 내려오는지 설명이 없었다 — V3(WMS) 주석에 "리프트로 내려온다"고
--     써 놓고 리프트가 없었다. 엘리베이터를 실제 노드로 만든다.
--  2. **AMR이 렉과 겹쳤다.** 도크가 렉 기둥(x=2.5, 6.5) 사이 x=4에 끼어 있는데, 로봇은 도크
--     주위 반지름 1.1 원에 주차하고 로봇 반지름이 0.95라 좌우로 2.05가 필요했다(간격은 1.5).
--     실측: AMR-01(5.1,6.0) ↔ WH-1F-R02(6.5,3.5). 왼쪽 렉 기둥을 없애 **충전존**을 만들고,
--     로봇마다 자기 베이를 준다(같은 도크에 여러 대가 서면 원주 위에서 1.9까지 붙는다).
--  3. 층마다 AMR을 두려면 층에 노드가 있어야 한다 — 그래서 노드에 building_code/floor_no를 준다.
--
-- **배치 규칙(경로 규칙이 컴파일 상수라 배치로 지킨다)**
--  - 노드는 커넥터 x(4, 9, 14 / 27, 34, 41, 48 / 62) 위, 통로(y=9, 18) 밖.
--  - 렉은 커넥터에서 1.75 이상 떨어뜨린다(로봇 반지름 0.95 + 렉 반폭 0.8) — 주행 중 겹치지 않게.
--    그래서 렉 기둥은 7.0 / 11.5 / 16.5 이고, x=4 왼쪽은 통째로 충전존으로 비운다.

-- ---- 평면도 치수 ----
-- 치수는 V9에서 정한 그대로다(68 × 26, 통로 9·18). 그런데도 여기서 다시 못박는 이유는,
-- robot-sim 의 정합 테스트가 **평면도 정본 파일 하나**를 읽어 좌표·치수를 대조하기 때문이다.
-- 이 파일이 새 정본이 되므로 치수도 함께 들고 있어야 그 검사가 성립한다.
delete from layout_settings;
insert into layout_settings (id, width, height, upper_aisle_y, lower_aisle_y, created_at, updated_at)
values (1, 68, 26, 9, 18, now(), now());

-- ---- 노드에 건물·층 ----
-- 지금까지 소속은 좌표로만 알았다(건물은 사각형, 노드는 점). 층이 생기면서 소속이 **기능**이
-- 됐다 — 위층 노드는 아래층과 좌표가 겹치므로 좌표만으로는 구분할 수 없다.
alter table layout_nodes add column building_code varchar(30);
alter table layout_nodes add column floor_no smallint;

-- ---- 엘리베이터 ----
-- 층마다 하나씩, **같은 자리**에 선다(샤프트가 수직으로 관통하므로).
-- 물건만 오르내린다 — AMR은 자기 층에 머문다.
create table layout_elevators (
    id            bigserial primary key,
    elevator_code varchar(30) not null unique,
    building_code varchar(30) not null,
    name          varchar(50) not null,
    pos_x         double precision not null,
    pos_y         double precision not null,
    /** 이 샤프트가 닿는 층(쉼표 구분) — 화면이 "1·2·3층" 으로 보여준다. */
    serves_floors varchar(30) not null,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

insert into layout_elevators
    (elevator_code, building_code, name, pos_x, pos_y, serves_floors, created_at, updated_at) values
    ('WH-ELEV', 'WH', '창고동 화물 엘리베이터', 14, 13, '1,2,3', now(), now());

-- ---- 충전존 ----
-- 도크가 흩어져 있으면 "충전하러 가는 곳"이 읽히지 않는다. 구역으로 묶어 그린다.
create table layout_charging_zones (
    id            bigserial primary key,
    zone_code     varchar(30) not null unique,
    building_code varchar(30) not null,
    floor_no      smallint not null,
    name          varchar(50) not null,
    pos_x         double precision not null,   -- 좌상단
    pos_y         double precision not null,
    width         double precision not null,
    height        double precision not null,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

insert into layout_charging_zones
    (zone_code, building_code, floor_no, name, pos_x, pos_y, width, height, created_at, updated_at) values
    ('CZ-1F', 'WH', 1, '1층 충전존', 1.6, 1.6, 4.8, 22.8, now(), now()),
    ('CZ-2F', 'WH', 2, '2층 충전존', 1.6, 1.6, 4.8,  6.0, now(), now()),
    ('CZ-3F', 'WH', 3, '3층 충전존', 1.6, 1.6, 4.8,  6.0, now(), now());

-- ---- 노드 재배치 + 층별 노드 ----
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- 창고동 1층: 충전 베이 4개(로봇마다 하나 — 같은 도크에 몰리면 원주에서 1.9까지 붙는다)
    ('WH-DOCK-1', '1번 충전 베이', 'DOCK',       4,  3, 'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이', 'DOCK',       4,  5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이', 'DOCK',       4, 21, 'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이', 'DOCK',       4, 23, 'WH', 1, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE',  9,  6, 'WH', 1, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE',  9, 13, 'WH', 1, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  14, 21, 'WH', 1, now(), now()),
    ('WH-ELEV-1F','엘리베이터 1층','ELEVATOR',  14, 13, 'WH', 1, now(), now()),
    -- 창고동 2층: 자기 층 AMR과 충전 베이, 엘리베이터 승강장
    ('WH-DOCK-2F','2층 충전 베이', 'DOCK',       4,  3, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE',  9,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE',  9, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  14, 13, 'WH', 2, now(), now()),
    -- 창고동 3층
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4,  3, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE',  9,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE',  9, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  14, 13, 'WH', 3, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장)
    ('PROD-A1',   'A1 하역',       'STATION',   27,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   34,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   41,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   48,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   27, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   34, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   41, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   48, 21, 'PROD', 1, now(), now()),
    -- 품질동: 가공품은 예외 없이 여기를 거친다
    ('QC-IN',     '검사 입고',     'INSPECTION', 62, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 62,  6, 'QC', 1, now(), now());

alter table layout_nodes alter column building_code set not null;
alter table layout_nodes alter column floor_no set not null;

-- ---- 렉 재배치 ----
-- 커넥터(4/9/14)에서 1.75 이상 떨어진 기둥 3개 × 3줄. 왼쪽 기둥은 충전존에 내줬다.
delete from layout_racks;

insert into layout_racks
    (rack_code, building_code, floor_no, pos_x, pos_y, orientation, columns_count, levels_count, capacity_qty, created_at, updated_at) values
    ('WH-1F-R01', 'WH', 1,  7.0,  4.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R02', 'WH', 1, 11.5,  4.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R03', 'WH', 1, 16.5,  4.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R04', 'WH', 1,  7.0, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R05', 'WH', 1, 11.5, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R06', 'WH', 1, 16.5, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R07', 'WH', 1,  7.0, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R08', 'WH', 1, 11.5, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R09', 'WH', 1, 16.5, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-2F-R01', 'WH', 2,  7.0,  4.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R02', 'WH', 2, 11.5,  4.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R03', 'WH', 2, 16.5,  4.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R04', 'WH', 2,  7.0, 13.5, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R05', 'WH', 2, 11.5, 13.5, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R06', 'WH', 2, 16.5, 13.5, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R07', 'WH', 2,  7.0, 22.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R08', 'WH', 2, 11.5, 22.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R09', 'WH', 2, 16.5, 22.0, 'V', 3, 4, 120, now(), now()),
    ('WH-3F-R01', 'WH', 3,  7.0,  4.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R02', 'WH', 3, 11.5,  4.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R03', 'WH', 3, 16.5,  4.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R04', 'WH', 3,  7.0, 13.5, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R05', 'WH', 3, 11.5, 13.5, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R06', 'WH', 3, 16.5, 13.5, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R07', 'WH', 3,  7.0, 22.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R08', 'WH', 3, 11.5, 22.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R09', 'WH', 3, 16.5, 22.0, 'V', 2, 6, 240, now(), now());
