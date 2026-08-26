-- P30: 창고동에 4번째 베이를 신설한다 — 렉 18기 증설 + 실제로 도는 순환 경로.
-- 설계 근거: docs/p30-warehouse-fourth-bay-design.md.
--
-- **왜 두 가지가 한 변경으로 묶이는가.** 렉을 더 늘리는 것과 "실제로 라우팅되는 순환
-- 경로"는 원래 다른 문제다(AGV의 렉 접근은 LaneGraph를 안 탄다, P21 D2) — 렉만 늘리면
-- 순환 경로는 여전히 죽는다. 그래서 4번째 베이에 렉과 별개로 진짜 목적지 노드
-- (WH-SHIP-2)를 두고, DemoTaskGenerator가 그 노드로 실제 주문을 보낸다 — 그래야 새
-- 연결로 구간을 실제 주문이 지나간다.
--
-- **왜 생산동·품질동·신관까지 옮기는가.** 창고동 폭이 40→53으로 늘면 그 오른쪽에 있는
-- 모든 건물이 그만큼(+13) 밀려야 자리가 안 겹친다. V16과 같은 이유로 **균일하게 밀면
-- 그 건물들 내부 엣지 비용은 하나도 안 바뀐다**(양 끝이 같이 움직이므로) — 바뀌는 건
-- 창고동 내부(신규 연결로 두 구간)와 창고동↔게이트 구간뿐이다.
--
-- **왜 노드·엣지를 지웠다 다시 넣는가.** V15/V16/V17/V20과 같은 이유 —
-- NodeMapLayoutConsistencyTest가 최신 마이그레이션 파일 하나를 정본으로 파싱한다.

delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 173, 26, 9, 18, 7, now(), now(), now());

update layout_buildings set width = 53 where building_code = 'WH';
update layout_buildings set pos_x = 58  where building_code = 'PROD';
update layout_buildings set pos_x = 94  where building_code = 'QC';
update layout_buildings set pos_x = 108 where building_code = 'BLDG-A';
update layout_buildings set pos_x = 141 where building_code = 'BLDG-B';

delete from layout_edges;
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- 창고동 1층 — 도크는 P29(V20)와 동일한 좌하단 코너, 안 움직인다.
    ('WH-DOCK-1', '1번 충전 베이(AGV)', 'DOCK',       4, 19,   'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이(AGV)', 'DOCK',       4, 20.5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이(AGV)', 'DOCK',       4, 21,   'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이(AGV)', 'DOCK',       4, 23,   'WH', 1, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE', 17,  6, 'WH', 1, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE', 17, 13, 'WH', 1, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  30, 21, 'WH', 1, now(), now()),
    ('WH-ELEV-1F','엘리베이터 1층','ELEVATOR',  30, 13, 'WH', 1, now(), now()),
    -- P30: 4번째 베이 — 보조 출하장(순환 경로에 실제 주문을 태우는 진짜 목적지, D3)
    ('WH-SHIP-2', '보조 출하장',   'SHIPPING',  41, 21, 'WH', 1, now(), now()),
    -- 창고동 2층 (범위 밖 — 도크는 P29와 동일)
    ('WH-DOCK-2F','2층 충전 베이', 'DOCK',       4, 21, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  30, 13, 'WH', 2, now(), now()),
    -- 창고동 3층
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4, 21, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  30, 13, 'WH', 3, now(), now()),
    -- 교차점(JUNCTION) — 창고동 4개(신규 JCT-19 포함), 생산동·품질동·신관은 균일 +13
    ('JCT-4-U',  '연결로 4 · 상단 통로 교차점',   'JUNCTION', 4,  9, 'WH',   1, now(), now()),
    ('JCT-4-L',  '연결로 4 · 하단 통로 교차점',   'JUNCTION', 4, 18, 'WH',   1, now(), now()),
    ('JCT-9-U',  '연결로 17 · 상단 통로 교차점',  'JUNCTION', 17,  9, 'WH',   1, now(), now()),
    ('JCT-9-L',  '연결로 17 · 하단 통로 교차점',  'JUNCTION', 17, 18, 'WH',   1, now(), now()),
    ('JCT-14-U', '연결로 30 · 상단 통로 교차점',  'JUNCTION', 30,  9, 'WH',   1, now(), now()),
    ('JCT-14-L', '연결로 30 · 하단 통로 교차점',  'JUNCTION', 30, 18, 'WH',   1, now(), now()),
    -- P30: 4번째 베이 연결로(옛 벽 자리, x=41) — "불투명 식별자" 관례상 4·9·14 다음(19)을 쓴다.
    ('JCT-19-U', '연결로 41 · 상단 통로 교차점',  'JUNCTION', 41,  9, 'WH',   1, now(), now()),
    ('JCT-19-L', '연결로 41 · 하단 통로 교차점',  'JUNCTION', 41, 18, 'WH',   1, now(), now()),
    ('JCT-27-U', '연결로 62 · 상단 통로 교차점',  'JUNCTION', 62,  9, 'PROD', 1, now(), now()),
    ('JCT-27-L', '연결로 62 · 하단 통로 교차점',  'JUNCTION', 62, 18, 'PROD', 1, now(), now()),
    ('JCT-34-U', '연결로 69 · 상단 통로 교차점',  'JUNCTION', 69,  9, 'PROD', 1, now(), now()),
    ('JCT-34-L', '연결로 69 · 하단 통로 교차점',  'JUNCTION', 69, 18, 'PROD', 1, now(), now()),
    ('JCT-41-U', '연결로 76 · 상단 통로 교차점',  'JUNCTION', 76,  9, 'PROD', 1, now(), now()),
    ('JCT-41-L', '연결로 76 · 하단 통로 교차점',  'JUNCTION', 76, 18, 'PROD', 1, now(), now()),
    ('JCT-48-U', '연결로 83 · 상단 통로 교차점',  'JUNCTION', 83,  9, 'PROD', 1, now(), now()),
    ('JCT-48-L', '연결로 83 · 하단 통로 교차점',  'JUNCTION', 83, 18, 'PROD', 1, now(), now()),
    ('JCT-62-U', '연결로 97 · 상단 통로 교차점',  'JUNCTION', 97,  9, 'QC',   1, now(), now()),
    ('JCT-62-L', '연결로 97 · 하단 통로 교차점',  'JUNCTION', 97, 18, 'QC',   1, now(), now()),
    -- P22: AMR ↔ AGV 게이트 — 창고동 새 벽(54) 밖, 생산동 새 벽(58) 앞 중립 지대(+13 이동)
    ('WH-GATE-U', '창고동 게이트 · 상단', 'GATE', 56,  9, 'PROD', 1, now(), now()),
    ('WH-GATE-L', '창고동 게이트 · 하단', 'GATE', 56, 18, 'PROD', 1, now(), now()),
    -- P22: 생산동 쪽 AMR 충전 베이 — 균일 +13
    ('PROD-DOCK-1', '1번 충전 베이(AMR)', 'DOCK', 62,  3, 'PROD', 1, now(), now()),
    ('PROD-DOCK-2', '2번 충전 베이(AMR)', 'DOCK', 62,  5, 'PROD', 1, now(), now()),
    ('PROD-DOCK-3', '3번 충전 베이(AMR)', 'DOCK', 62, 21, 'PROD', 1, now(), now()),
    ('PROD-DOCK-4', '4번 충전 베이(AMR)', 'DOCK', 62, 23, 'PROD', 1, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장) — 균일 +13
    ('PROD-A1',   'A1 하역',       'STATION',   62,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   69,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   76,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   83,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   62, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   69, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   76, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   83, 21, 'PROD', 1, now(), now()),
    -- 품질동 — 균일 +13
    ('QC-IN',     '검사 입고',     'INSPECTION', 97, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 97,  6, 'QC', 1, now(), now()),
    -- 신관(V14) — 균일 +13
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',     108, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',  118, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  128, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',   135, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  145, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 158, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 ----
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('JCT-4-U',  'JCT-4-L',  9, true, now(), now()),
    ('JCT-9-U',  'JCT-9-L',  9, true, now(), now()),
    ('JCT-14-U', 'JCT-14-L', 9, true, now(), now()),
    ('JCT-19-U', 'JCT-19-L', 9, true, now(), now()),
    ('JCT-27-U', 'JCT-27-L', 9, true, now(), now()),
    ('JCT-34-U', 'JCT-34-L', 9, true, now(), now()),
    ('JCT-41-U', 'JCT-41-L', 9, true, now(), now()),
    ('JCT-48-U', 'JCT-48-L', 9, true, now(), now()),
    ('JCT-62-U', 'JCT-62-L', 9, true, now(), now());

-- 통로(가로) — 창고동 내부 두 구간(4~17, 17~30)은 그대로(13). 새로 생긴 두 구간
-- (30~41, 41~56)이 이번 마이그레이션의 핵심 변화 — 옛 JCT-14↔게이트 직결(13)이
-- JCT-14↔JCT-19(11) + JCT-19↔게이트(15)로 늘어난다(물리적으로 베이 하나가
-- 끼어들었으므로 총 거리가 실제로 늘어나는 게 맞다 — V15/V16의 "균일 이동" 패턴과 다르다).
-- 나머지(생산동·품질동·신관 내부)는 양 끝이 같이 +13 움직여 거리가 그대로다.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('JCT-4-U',  'JCT-9-U',  13, true, now(), now()),
    ('JCT-9-U',  'JCT-14-U', 13, true, now(), now()),
    ('JCT-14-U', 'JCT-19-U', 11, true, now(), now()),
    ('JCT-19-U', 'WH-GATE-U', 15, true, now(), now()),
    ('WH-GATE-U', 'JCT-27-U',  6, true, now(), now()),
    ('JCT-27-U', 'JCT-34-U', 7,  true, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, now(), now()),
    ('JCT-4-L',  'JCT-9-L',  13, true, now(), now()),
    ('JCT-9-L',  'JCT-14-L', 13, true, now(), now()),
    ('JCT-14-L', 'JCT-19-L', 11, true, now(), now()),
    ('JCT-19-L', 'WH-GATE-L', 15, true, now(), now()),
    ('WH-GATE-L', 'JCT-27-L',  6, true, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, now(), now());

-- 명명된 노드 → 교차점. 도크는 P29(V20)와 동일. WH-SHIP-2가 새로 생겼다(D3 — 순환 경로에
-- 실제 주문을 태우는 목적지, WH-SHIP의 JCT-14-L 연결 비용(3)과 같은 패턴).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('WH-DOCK-1',  'JCT-4-L',  1,   true, now(), now()),
    ('WH-DOCK-2',  'JCT-4-L',  2.5, true, now(), now()),
    ('WH-DOCK-3',  'JCT-4-L',  3,   true, now(), now()),
    ('WH-DOCK-4',  'JCT-4-L',  5,   true, now(), now()),
    ('WH-RECV',    'JCT-9-U',  3, true, now(), now()),
    ('WH-PICK',    'JCT-9-U',  4, true, now(), now()),
    ('WH-PICK',    'JCT-9-L',  5, true, now(), now()),
    ('WH-SHIP',    'JCT-14-L', 3, true, now(), now()),
    ('WH-ELEV-1F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-1F', 'JCT-14-L', 5, true, now(), now()),
    ('WH-SHIP-2',  'JCT-19-L', 3, true, now(), now()),
    ('WH-DOCK-2F', 'JCT-4-L',  3, true, now(), now()),
    ('WH-2F-P1',   'JCT-9-U',  3, true, now(), now()),
    ('WH-2F-P2',   'JCT-9-U',  4, true, now(), now()),
    ('WH-2F-P2',   'JCT-9-L',  5, true, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-L', 5, true, now(), now()),
    ('WH-DOCK-3F', 'JCT-4-L',  3, true, now(), now()),
    ('WH-3F-P1',   'JCT-9-U',  3, true, now(), now()),
    ('WH-3F-P2',   'JCT-9-U',  4, true, now(), now()),
    ('WH-3F-P2',   'JCT-9-L',  5, true, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-L', 5, true, now(), now()),
    ('PROD-DOCK-1', 'JCT-27-U', 6, true, now(), now()),
    ('PROD-DOCK-2', 'JCT-27-U', 4, true, now(), now()),
    ('PROD-DOCK-3', 'JCT-27-L', 3, true, now(), now()),
    ('PROD-DOCK-4', 'JCT-27-L', 5, true, now(), now()),
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

insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 11, true, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, now(), now());

-- ---- 렉 18기 증설 (D4) — 4번째 베이(x=45/50), 행 우선 번호(R01~R18과 같은 관례) ----
insert into layout_racks
    (rack_code, building_code, floor_no, pos_x, pos_y, orientation, columns_count, levels_count, capacity_qty, created_at, updated_at) values
    -- 1층 (4열×5단, 용량 200)
    ('WH-1F-R19', 'WH', 1, 45, 4.0,  'V', 4, 5, 200, now(), now()),
    ('WH-1F-R20', 'WH', 1, 50, 4.0,  'V', 4, 5, 200, now(), now()),
    ('WH-1F-R21', 'WH', 1, 45, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R22', 'WH', 1, 50, 13.5, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R23', 'WH', 1, 45, 22.0, 'V', 4, 5, 200, now(), now()),
    ('WH-1F-R24', 'WH', 1, 50, 22.0, 'V', 4, 5, 200, now(), now()),
    -- 2층 (3열×4단, 용량 120)
    ('WH-2F-R19', 'WH', 2, 45, 4.0,  'V', 3, 4, 120, now(), now()),
    ('WH-2F-R20', 'WH', 2, 50, 4.0,  'V', 3, 4, 120, now(), now()),
    ('WH-2F-R21', 'WH', 2, 45, 13.5, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R22', 'WH', 2, 50, 13.5, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R23', 'WH', 2, 45, 22.0, 'V', 3, 4, 120, now(), now()),
    ('WH-2F-R24', 'WH', 2, 50, 22.0, 'V', 3, 4, 120, now(), now()),
    -- 3층 (2열×6단, 용량 240)
    ('WH-3F-R19', 'WH', 3, 45, 4.0,  'V', 2, 6, 240, now(), now()),
    ('WH-3F-R20', 'WH', 3, 50, 4.0,  'V', 2, 6, 240, now(), now()),
    ('WH-3F-R21', 'WH', 3, 45, 13.5, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R22', 'WH', 3, 50, 13.5, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R23', 'WH', 3, 45, 22.0, 'V', 2, 6, 240, now(), now()),
    ('WH-3F-R24', 'WH', 3, 50, 22.0, 'V', 2, 6, 240, now(), now());

-- ---- 설비 8대 ---- 생산동과 같이 균일 +13(V16이 실제로 빠뜨릴 뻔했다고 경고한 항목).
update equipments e set pos_x = v.new_x, updated_at = now()
from (values
    ('CNC-01', 62.0), ('CNC-02', 69.0), ('CNC-03', 76.0), ('MCT-01', 83.0),
    ('ASM-01', 62.0), ('ASM-02', 69.0), ('INS-01', 76.0), ('PKG-01', 83.0)
) as v(code, new_x)
where e.equipment_code = v.code;

-- ---- POP 단말 2개 ---- 생산동과 같이 균일 +13.
update pop_terminals t set pos_x = v.new_x, updated_at = now()
from (values ('POP-A1', 59.0), ('POP-B1', 59.0)) as v(code, new_x)
where t.terminal_code = v.code;

-- 충전존 사각형(layout_charging_zones)은 이번에 안 바뀐다 — 도크가 창고동 안(x=1.6 부근)
-- 왼쪽 벽에 붙어 있고 이번 변경은 오른쪽(x=41 이후)만 건드린다.
