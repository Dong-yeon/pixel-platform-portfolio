-- 창고동을 명실상부한 최대 건물로 — V15(18→30)로도 생산동(32)보다 여전히 좁았다.
--
-- **무엇을 바꾸는가.** 창고동 폭을 30 → 40으로 다시 넓혀 생산동(32)을 확실히 앞지르게 한다.
-- 이번엔 내부 연결로 2개(13·22)도 더 벌려(17·30) 새로 생긴 폭을 통로로 실제로 쓴다 —
-- 지난번처럼 오른쪽에 빈 공간만 남기지 않는다. 렉 세 기둥도 그 연결로 배치를 따라
-- 10.5 / 23.5 / 36.5로 다시 편다.
--
-- 생산동·품질동·신관(BLDG-A/B)은 이번엔 전부 균일하게(+10) 오른쪽으로 밀린다 — V15와 달리
-- 신관까지 미는 이유는, 신관을 그대로 두면 품질동(81~89)이 신관(85~)과 겹치기 때문이다.
-- **균일하게 밀면 내부 상대 거리가 그대로 보존된다** — PROD·QC·신관 내부 엣지 비용은 단
-- 하나도 안 바뀐다(양 끝이 같이 움직이므로). 바뀌는 건 창고동 내부 연결로 3개와,
-- 창고동↔생산동 경계 엣지 하나뿐이다.
--
-- 좌표 코드는 V15와 같은 이유로 그대로 둔다(FK, LaneGraph 구간 ID와 같은 "불투명 식별자"
-- 관례). 노드·엣지를 지웠다 다시 넣는 이유, 렉·엘리베이터는 UPDATE만 하는 이유도 V15와 같다
-- (NodeMapLayoutConsistencyText·RackMapLayoutConsistencyTest 참고).

delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 160, 26, 9, 18, 4, now(), now(), now());

update layout_buildings set width = 40 where building_code = 'WH';
update layout_buildings set pos_x = 45  where building_code = 'PROD';
update layout_buildings set pos_x = 81  where building_code = 'QC';
update layout_buildings set pos_x = 95  where building_code = 'BLDG-A';
update layout_buildings set pos_x = 128 where building_code = 'BLDG-B';

delete from layout_edges;
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- 창고동 1층 — 도크는 그대로(연결로 4는 안 움직인다)
    ('WH-DOCK-1', '1번 충전 베이', 'DOCK',       4,  3, 'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이', 'DOCK',       4,  5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이', 'DOCK',       4, 21, 'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이', 'DOCK',       4, 23, 'WH', 1, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE', 17,  6, 'WH', 1, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE', 17, 13, 'WH', 1, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  30, 21, 'WH', 1, now(), now()),
    ('WH-ELEV-1F','엘리베이터 1층','ELEVATOR',  30, 13, 'WH', 1, now(), now()),
    -- 창고동 2층
    ('WH-DOCK-2F','2층 충전 베이', 'DOCK',       4,  3, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  30, 13, 'WH', 2, now(), now()),
    -- 창고동 3층
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4,  3, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  30, 13, 'WH', 3, now(), now()),
    -- 교차점(JUNCTION) — 창고동 두 개(17·30로 재배치), 생산동·품질동·신관은 균일 +10
    ('JCT-4-U',  '연결로 4 · 상단 통로 교차점',   'JUNCTION', 4,  9, 'WH',   1, now(), now()),
    ('JCT-4-L',  '연결로 4 · 하단 통로 교차점',   'JUNCTION', 4, 18, 'WH',   1, now(), now()),
    ('JCT-9-U',  '연결로 17 · 상단 통로 교차점',  'JUNCTION', 17,  9, 'WH',   1, now(), now()),
    ('JCT-9-L',  '연결로 17 · 하단 통로 교차점',  'JUNCTION', 17, 18, 'WH',   1, now(), now()),
    ('JCT-14-U', '연결로 30 · 상단 통로 교차점',  'JUNCTION', 30,  9, 'WH',   1, now(), now()),
    ('JCT-14-L', '연결로 30 · 하단 통로 교차점',  'JUNCTION', 30, 18, 'WH',   1, now(), now()),
    ('JCT-27-U', '연결로 49 · 상단 통로 교차점',  'JUNCTION', 49,  9, 'PROD', 1, now(), now()),
    ('JCT-27-L', '연결로 49 · 하단 통로 교차점',  'JUNCTION', 49, 18, 'PROD', 1, now(), now()),
    ('JCT-34-U', '연결로 56 · 상단 통로 교차점',  'JUNCTION', 56,  9, 'PROD', 1, now(), now()),
    ('JCT-34-L', '연결로 56 · 하단 통로 교차점',  'JUNCTION', 56, 18, 'PROD', 1, now(), now()),
    ('JCT-41-U', '연결로 63 · 상단 통로 교차점',  'JUNCTION', 63,  9, 'PROD', 1, now(), now()),
    ('JCT-41-L', '연결로 63 · 하단 통로 교차점',  'JUNCTION', 63, 18, 'PROD', 1, now(), now()),
    ('JCT-48-U', '연결로 70 · 상단 통로 교차점',  'JUNCTION', 70,  9, 'PROD', 1, now(), now()),
    ('JCT-48-L', '연결로 70 · 하단 통로 교차점',  'JUNCTION', 70, 18, 'PROD', 1, now(), now()),
    ('JCT-62-U', '연결로 84 · 상단 통로 교차점',  'JUNCTION', 84,  9, 'QC',   1, now(), now()),
    ('JCT-62-L', '연결로 84 · 하단 통로 교차점',  'JUNCTION', 84, 18, 'QC',   1, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장) — 균일 +10
    ('PROD-A1',   'A1 하역',       'STATION',   49,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   56,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   63,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   70,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   49, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   56, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   63, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   70, 21, 'PROD', 1, now(), now()),
    -- 품질동 — 균일 +10
    ('QC-IN',     '검사 입고',     'INSPECTION', 84, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 84,  6, 'QC', 1, now(), now()),
    -- 신관(V14) — 이번엔 균일 +10로 같이 옮긴다(안 옮기면 품질동과 겹친다).
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',      95, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',  105, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  115, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',    122, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  132, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 145, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 다시 넣는다 ----
-- 균일하게 +10 움직인 구간은 상대거리가 그대로라 값이 안 바뀐다. 바뀌는 건 창고동 내부
-- 연결로 두 구간(9→13, 창고동↔생산동 경계 하나(17→19)뿐이다 — 아래 주석 참고.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('JCT-4-U',  'JCT-4-L',  9, true, now(), now()),
    ('JCT-9-U',  'JCT-9-L',  9, true, now(), now()),
    ('JCT-14-U', 'JCT-14-L', 9, true, now(), now()),
    ('JCT-27-U', 'JCT-27-L', 9, true, now(), now()),
    ('JCT-34-U', 'JCT-34-L', 9, true, now(), now()),
    ('JCT-41-U', 'JCT-41-L', 9, true, now(), now()),
    ('JCT-48-U', 'JCT-48-L', 9, true, now(), now()),
    ('JCT-62-U', 'JCT-62-L', 9, true, now(), now());

insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('JCT-4-U',  'JCT-9-U',  13, true, now(), now()),  -- 9 → 13  (4~17)
    ('JCT-9-U',  'JCT-14-U', 13, true, now(), now()),  -- 9 → 13  (17~30)
    ('JCT-14-U', 'JCT-27-U', 19, true, now(), now()),  -- 17 → 19 (30~49)
    ('JCT-27-U', 'JCT-34-U', 7,  true, now(), now()),  -- 균일 이동, 그대로
    ('JCT-34-U', 'JCT-41-U', 7,  true, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, now(), now()),  -- 균일 이동, 그대로
    ('JCT-4-L',  'JCT-9-L',  13, true, now(), now()),
    ('JCT-9-L',  'JCT-14-L', 13, true, now(), now()),
    ('JCT-14-L', 'JCT-27-L', 19, true, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, now(), now());

-- 명명된 노드 → 교차점. 전부 자기 연결로와 같이(같은 델타로) 움직여서 y차만 남는다 —
-- 비용은 V15와 완전히 동일하다(창고동 것도 포함 — WH-RECV·SHIP·ELEV가 연결로와 같이
-- 움직였으므로 Δx=0이 유지된다).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('WH-DOCK-1',  'JCT-4-U',  6, true, now(), now()),
    ('WH-DOCK-2',  'JCT-4-U',  4, true, now(), now()),
    ('WH-DOCK-3',  'JCT-4-L',  3, true, now(), now()),
    ('WH-DOCK-4',  'JCT-4-L',  5, true, now(), now()),
    ('WH-RECV',    'JCT-9-U',  3, true, now(), now()),
    ('WH-PICK',    'JCT-9-U',  4, true, now(), now()),
    ('WH-PICK',    'JCT-9-L',  5, true, now(), now()),
    ('WH-SHIP',    'JCT-14-L', 3, true, now(), now()),
    ('WH-ELEV-1F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-1F', 'JCT-14-L', 5, true, now(), now()),
    ('WH-DOCK-2F', 'JCT-4-U',  6, true, now(), now()),
    ('WH-2F-P1',   'JCT-9-U',  3, true, now(), now()),
    ('WH-2F-P2',   'JCT-9-U',  4, true, now(), now()),
    ('WH-2F-P2',   'JCT-9-L',  5, true, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-L', 5, true, now(), now()),
    ('WH-DOCK-3F', 'JCT-4-U',  6, true, now(), now()),
    ('WH-3F-P1',   'JCT-9-U',  3, true, now(), now()),
    ('WH-3F-P2',   'JCT-9-U',  4, true, now(), now()),
    ('WH-3F-P2',   'JCT-9-L',  5, true, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-L', 5, true, now(), now()),
    ('PROD-A1', 'JCT-27-U', 3, true, now(), now()),
    ('PROD-A2', 'JCT-34-U', 3, true, now(), now()),
    ('PROD-A3', 'JCT-41-U', 3, true, now(), now()),
    ('PROD-A4', 'JCT-48-U', 3, true, now(), now()),
    ('PROD-B1', 'JCT-27-L', 3, true, now(), now()),
    ('PROD-B2', 'JCT-34-L', 3, true, now(), now()),
    ('PROD-B3', 'JCT-41-L', 3, true, now(), now()),
    ('PROD-B4', 'JCT-48-L', 3, true, now(), now()),
    ('QC-OUT', 'JCT-62-U', 3, true, now(), now()),
    ('QC-IN',  'JCT-62-L', 3, true, now(), now());

-- 신관 엣지 6개 — QC-OUT·GATE-WH-A가 이번엔 둘 다 +10으로 같이 움직여 거리가 그대로다(11).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 11, true, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, now(), now());

-- ---- 렉 재배치 ---- 코드·열·단·용량 그대로, 새 연결로(4/17/30) 기준으로 다시 편다.
update layout_racks r set pos_x = v.new_x, updated_at = now()
from (values
    ('R01', 10.5), ('R02', 23.5), ('R03', 36.5),
    ('R04', 10.5), ('R05', 23.5), ('R06', 36.5),
    ('R07', 10.5), ('R08', 23.5), ('R09', 36.5)
) as v(suffix, new_x)
where r.rack_code like 'WH-_F-' || v.suffix;

-- ---- 엘리베이터 ---- 노드(WH-ELEV-*F)와 같은 자리.
update layout_elevators set pos_x = 30, updated_at = now() where elevator_code = 'WH-ELEV';

-- ---- 설비 8대 ---- 생산동과 같이 균일 +10.
update equipments e set pos_x = v.new_x, updated_at = now()
from (values
    ('CNC-01', 49.0), ('CNC-02', 56.0), ('CNC-03', 63.0), ('MCT-01', 70.0),
    ('ASM-01', 49.0), ('ASM-02', 56.0), ('INS-01', 63.0), ('PKG-01', 70.0)
) as v(code, new_x)
where e.equipment_code = v.code;

-- ---- POP 단말 2개 ---- 생산동과 같이 균일 +10.
update pop_terminals t set pos_x = v.new_x, updated_at = now()
from (values ('POP-A1', 46.0), ('POP-B1', 46.0)) as v(code, new_x)
where t.terminal_code = v.code;
