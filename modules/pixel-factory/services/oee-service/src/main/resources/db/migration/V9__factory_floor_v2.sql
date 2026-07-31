-- 공장 평면도 2.0 — 건물 3채(생산동 / 창고동 3층 / 품질동) + 렉.
--
-- **왜 다시 그리는가.** V7의 44×24는 설비가 통로 옆에 일렬로 선 배치도에 가까웠다. 창고는
-- 노드 하나(WAREHOUSE)로 축약돼 있어 "무엇이 어디에 얼마나 쌓여 있는가"를 말할 수 없었고,
-- 품질관리실은 평면도 **바깥에 떠 있는 박스**였다(P14). 실제 공장처럼 구역을 나눈다.
--
-- **경로 계산과의 계약(중요).** fleet의 LaneGraph/robot-sim NodeMap은 "통로 2개 + 커넥터 x"를
-- 컴파일 상수로 갖는 고정 규칙(수직→통로→수평→수직)이다. 그래서 건물을 나누되
-- **통로 2개 구조는 유지**하고, 통로가 벽을 지나는 자리를 출입구로 삼는다. 규칙은 그대로 두고
-- 배치만 바꾸므로 경로탐색을 새로 쓰지 않아도 로봇이 벽을 뚫지 않는다.
--   - 모든 노드는 커넥터 x 위에 둔다: 4, 9, 14 (창고동) / 27, 34, 41, 48 (생산동) / 62 (품질동)
--   - 모든 노드는 통로(y=9, y=18)에서 벗어나 있다
--
-- **물류 흐름이 배치를 정한다.** 가공이 끝난 물건은 **무조건 품질동을 거친다**:
--     창고동(자재) → 생산동(가공·조립) → 품질동(전수 검사) → 합격이면 창고동(입고)
--                                                        → 불합격이면 생산동(재작업)
-- 그래서 품질동은 정보 흐름의 도착지이기만 한 게 아니라 **AMR 운송의 기착지**다. 검사 대기가
-- 들어오는 자리(QC-IN)는 생산동 B열과 같은 하단에, 판정 후 나가는 자리(QC-OUT)는 창고동
-- 입고장과 같은 상단에 둔다 — 각 구간이 통로 하나로 이어지도록.
--
-- **좌표 사본 4곳을 함께 고쳐야 한다**: LaneGraph / LocationRegistry.FALLBACK_NODES /
-- DemoTaskGenerator.FLOWS / robot-sim NodeMap. NodeMapLayoutConsistencyTest가 이 파일을
-- 파싱해 대조하므로, 어긋나면 빌드가 깨진다(그게 이 변경의 안전망이다).

-- ---- 건물 ----
create table layout_buildings (
    id            bigserial primary key,
    building_code varchar(30) not null unique,   -- PROD / WH / QC
    name          varchar(50) not null,
    pos_x         double precision not null,     -- 좌상단
    pos_y         double precision not null,
    width         double precision not null,
    height        double precision not null,
    floor_count   smallint not null default 1,
    display_order smallint not null default 0,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

insert into layout_buildings
    (building_code, name, pos_x, pos_y, width, height, floor_count, display_order, created_at, updated_at) values
    ('WH',   '창고동',   1,  1, 18, 24, 3, 1, now(), now()),
    ('PROD', '생산동',  23,  1, 32, 24, 1, 2, now(), now()),
    -- 품질동도 통로 2개가 모두 지나야 한다(검사 입고는 하단, 판정 출고는 상단).
    ('QC',   '품질동',  59,  1,  8, 24, 1, 3, now(), now());

-- ---- 층 (창고동만 여러 층) ----
create table layout_floors (
    id          bigserial primary key,
    building_id bigint not null references layout_buildings (id),
    floor_no    smallint not null,
    name        varchar(50) not null,
    created_at  timestamp not null,
    updated_at  timestamp not null,
    constraint uq_layout_floor unique (building_id, floor_no)
);

insert into layout_floors (building_id, floor_no, name, created_at, updated_at) values
    ((select id from layout_buildings where building_code = 'WH'),   1, '1층 파렛트 창고',   now(), now()),
    ((select id from layout_buildings where building_code = 'WH'),   2, '2층 잔량·피킹 창고', now(), now()),
    ((select id from layout_buildings where building_code = 'WH'),   3, '3층 풀파렛트 창고', now(), now()),
    ((select id from layout_buildings where building_code = 'PROD'), 1, '가공·조립 라인',    now(), now()),
    ((select id from layout_buildings where building_code = 'QC'),   1, '품질관리실',        now(), now());

-- ---- 렉(선반) ----
-- 용량은 **물리 속성**이라 여기(factory)에 둔다. 실제 적재 수량은 재고라 WMS가 갖고,
-- 대시보드가 rack_code == wms.locations.location_code 로 조인해 적재율을 색으로 그린다.
-- (모듈 간 FK 없이 코드로만 잇는다 — DB per module)
create table layout_racks (
    id            bigserial primary key,
    rack_code     varchar(30) not null unique,   -- WH-1F-R01
    building_code varchar(30) not null,
    floor_no      smallint not null,
    pos_x         double precision not null,     -- 중심
    pos_y         double precision not null,
    orientation   varchar(10) not null,          -- V(세로) / H(가로)
    columns_count smallint not null,             -- 열
    levels_count  smallint not null,             -- 단
    capacity_qty  integer not null,              -- 만재 수량(EA)
    created_at    timestamp not null,
    updated_at    timestamp not null
);

-- 1층 12기 — 노드(x=4,9,14)와 통로(y=9,18)를 피해 그 사이에 세운다.
insert into layout_racks
    (rack_code, building_code, floor_no, pos_x, pos_y, orientation, columns_count, levels_count, capacity_qty, created_at, updated_at) values
    ('WH-1F-R01', 'WH', 1,  2.5,  3.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R02', 'WH', 1,  6.5,  3.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R03', 'WH', 1, 11.5,  3.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R04', 'WH', 1, 16.5,  3.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R05', 'WH', 1,  2.5, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R06', 'WH', 1,  6.5, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R07', 'WH', 1, 11.5, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R08', 'WH', 1, 16.5, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R09', 'WH', 1,  2.5, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R10', 'WH', 1,  6.5, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R11', 'WH', 1, 11.5, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R12', 'WH', 1, 16.5, 22.0, 'V', 4, 5, 200, now(), now());

-- 2층 10기 — 지상 노드가 없으므로 자유 배치(잔량·피킹)
insert into layout_racks
    (rack_code, building_code, floor_no, pos_x, pos_y, orientation, columns_count, levels_count, capacity_qty, created_at, updated_at) values
    ('WH-2F-R01', 'WH', 2,  2.5,  7.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R02', 'WH', 2,  6.0,  7.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R03', 'WH', 2,  9.5,  7.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R04', 'WH', 2, 13.0,  7.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R05', 'WH', 2, 16.5,  7.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R06', 'WH', 2,  2.5, 18.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R07', 'WH', 2,  6.0, 18.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R08', 'WH', 2,  9.5, 18.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R09', 'WH', 2, 13.0, 18.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R10', 'WH', 2, 16.5, 18.0, 'V', 3, 4, 120, now(), now());

-- 3층 8기 — 풀파렛트(단이 높고 열이 적다)
insert into layout_racks
    (rack_code, building_code, floor_no, pos_x, pos_y, orientation, columns_count, levels_count, capacity_qty, created_at, updated_at) values
    ('WH-3F-R01', 'WH', 3,  3.5,  7.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R02', 'WH', 3,  8.0,  7.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R03', 'WH', 3, 12.5,  7.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R04', 'WH', 3, 17.0,  7.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R05', 'WH', 3,  3.5, 18.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R06', 'WH', 3,  8.0, 18.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R07', 'WH', 3, 12.5, 18.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R08', 'WH', 3, 17.0, 18.0, 'V', 2, 6, 240, now(), now());

-- ---- 평면도 치수 ----
-- 싱글톤이라 갱신이 아니라 지우고 다시 넣는다 — robot-sim 정합 테스트가
-- `values (1, w, h, upper, lower` 형태를 파싱하기 때문이다(UPDATE면 못 읽는다).
delete from layout_settings;
insert into layout_settings (id, width, height, upper_aisle_y, lower_aisle_y, created_at, updated_at)
values (1, 68, 26, 9, 18, now(), now());

-- ---- 노드 재배치 + 건물 체계로 재명명 ----
-- 예전 코드(WAREHOUSE/SHIPPING/STATION-*)는 어느 건물인지 말해 주지 않았다.
delete from layout_nodes;

insert into layout_nodes (node_code, name, node_type, pos_x, pos_y, created_at, updated_at) values
    -- 창고동: 도크는 통로에 접한 자리에 둔다(충전 복귀는 서버를 거치지 않으므로 벽을 뚫으면 안 된다)
    ('WH-DOCK-1', '1번 충전 도크', 'DOCK',       4,  6, now(), now()),
    ('WH-DOCK-2', '2번 충전 도크', 'DOCK',       4, 21, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE',  9,  6, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE',  9, 13, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  14, 21, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장)
    ('PROD-A1',   'A1 하역',       'STATION',   27,  6, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   34,  6, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   41,  6, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   48,  6, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   27, 21, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   34, 21, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   41, 21, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   48, 21, now(), now()),
    -- 품질동: 생산동에서 온 물건이 내려지는 자리(하단)와 판정 후 실려 나가는 자리(상단)
    ('QC-IN',     '검사 입고',     'INSPECTION', 62, 21, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 62,  6, now(), now());

-- ---- 설비 재배치 ----
-- 렉 뒤가 아니라 생산동 자기 구역에. 각 설비는 자기 하역 지점 바로 위/아래에 선다.
update equipments set pos_x = v.x, pos_y = v.y
from (values
    ('CNC-01', 27.0,  3.0),
    ('CNC-02', 34.0,  3.0),
    ('CNC-03', 41.0,  3.0),
    ('MCT-01', 48.0,  3.0),
    ('ASM-01', 27.0, 24.0),
    ('ASM-02', 34.0, 24.0),
    ('INS-01', 41.0, 24.0),
    ('PKG-01', 48.0, 24.0)
) as v(code, x, y)
where equipments.equipment_code = v.code;

-- ---- POP 단말 재배치 ----
-- 생산동 입구 쪽(작업자가 라인에 들어서며 조작하는 자리).
update pop_terminals set pos_x = v.x, pos_y = v.y
from (values
    ('POP-A1', 24.0,  4.5),
    ('POP-B1', 24.0, 22.5)
) as v(code, x, y)
where pop_terminals.terminal_code = v.code;
