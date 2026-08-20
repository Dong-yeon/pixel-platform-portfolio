-- P22: AMR이 창고동에 아예 들어오지 않게 — AMR ↔ AGV 게이트 신설.
-- 설계 근거: docs/p22-amr-agv-boundary-design.md, docs/BACKLOG.md P22.
--
-- **이 마이그레이션이 하는 일.** P21(랙 피더)은 "렉 → 피킹존" 구간만 AMR이 아닌 로봇(랙
-- 피더)에게 맡겼다 — 입고장·출하장·도크는 여전히 AMR이 드나들었다. 이번엔 그 나머지도
-- 넘긴다 — **창고동 1층 안쪽 전체가 AGV(옛 이름: 랙 피더) 전용**이 되고, AMR은 창고동에
-- 물리적으로 발을 들이지 않는다.
--
-- 그러려면 지금까지 창고동↔생산동을 직결하던 JCT-14↔JCT-27 엣지(연결로 30↔49, 비용 19)를
-- **WH-GATE-U/WH-GATE-L** 두 노드를 경유하는 두 구간으로 바꿔야 한다 — AMR의 레인망은 이제
-- 창고동 벽 밖(x=43, 두 건물 사이 중립 지대)에서 끝난다. 총 비용은 그대로다
-- (JCT-14→게이트 13 + 게이트→JCT-27 6 = 19).
--
-- 창고동 도크(WH-DOCK-*)는 AGV 전용이 되므로, AMR이 충전할 **생산동 쪽 도크
-- (PROD-DOCK-1~4)**를 새로 둔다 — 생산동 첫 연결로(JCT-27, x=49)에 WH-DOCK과 같은
-- y 배치(3/5/21/23)로 얹는다.
--
-- **창고동 2·3층은 이번에도 범위 밖이다**(P21 그대로) — 렉 전용 좁은 존 배치이고 AMR이
-- 계속 담당한다. fleet의 AGV 판정은 "창고동 1층"만 넓게 본다.
--
-- **좌표 코드는 그대로 둔다**(V15·V16과 같은 이유 — FK, "불투명 식별자" 관례). 노드·엣지를
-- 지웠다 다시 넣는 이유도 같다(NodeMapLayoutConsistencyTest가 이 파일을 새 정본으로 파싱).
-- 렉은 이번에 안 움직이므로 건드리지 않는다.

delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 160, 26, 9, 18, 5, now(), now(), now());

-- 건물 폭·위치는 V16과 동일 — 게이트는 벽 밖 중립 지대에 놓이므로 건물 자체는 안 바뀐다.

delete from layout_edges;
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- 창고동 1층 — 도크는 이제 AGV 전용
    ('WH-DOCK-1', '1번 충전 베이(AGV)', 'DOCK',       4,  3, 'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이(AGV)', 'DOCK',       4,  5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이(AGV)', 'DOCK',       4, 21, 'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이(AGV)', 'DOCK',       4, 23, 'WH', 1, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE', 17,  6, 'WH', 1, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE', 17, 13, 'WH', 1, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  30, 21, 'WH', 1, now(), now()),
    ('WH-ELEV-1F','엘리베이터 1층','ELEVATOR',  30, 13, 'WH', 1, now(), now()),
    -- 창고동 2층 (범위 밖 — 좌표·역할 그대로, 계속 AMR)
    ('WH-DOCK-2F','2층 충전 베이', 'DOCK',       4,  3, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  30, 13, 'WH', 2, now(), now()),
    -- 창고동 3층 (범위 밖)
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4,  3, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  30, 13, 'WH', 3, now(), now()),
    -- 교차점(JUNCTION) — 창고동 두 개(17·30), 생산동·품질동·신관은 V16과 동일
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
    -- P22: AMR ↔ AGV 게이트 — 창고동 벽(41) 밖, 생산동 벽(45) 앞의 중립 지대(x=43).
    ('WH-GATE-U', '창고동 게이트 · 상단', 'GATE', 43,  9, 'PROD', 1, now(), now()),
    ('WH-GATE-L', '창고동 게이트 · 하단', 'GATE', 43, 18, 'PROD', 1, now(), now()),
    -- P22: 생산동 쪽 AMR 충전 베이 — 창고동 도크가 AGV 전용이 되며 AMR은 갈 곳이 필요하다.
    ('PROD-DOCK-1', '1번 충전 베이(AMR)', 'DOCK', 49,  3, 'PROD', 1, now(), now()),
    ('PROD-DOCK-2', '2번 충전 베이(AMR)', 'DOCK', 49,  5, 'PROD', 1, now(), now()),
    ('PROD-DOCK-3', '3번 충전 베이(AMR)', 'DOCK', 49, 21, 'PROD', 1, now(), now()),
    ('PROD-DOCK-4', '4번 충전 베이(AMR)', 'DOCK', 49, 23, 'PROD', 1, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장) — V16과 동일
    ('PROD-A1',   'A1 하역',       'STATION',   49,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   56,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   63,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   70,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   49, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   56, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   63, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   70, 21, 'PROD', 1, now(), now()),
    -- 품질동 — V16과 동일
    ('QC-IN',     '검사 입고',     'INSPECTION', 84, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 84,  6, 'QC', 1, now(), now()),
    -- 신관(V14) — V16과 동일
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',      95, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',  105, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  115, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',    122, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  132, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 145, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 ----
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
    ('JCT-4-U',  'JCT-9-U',  13, true, now(), now()),
    ('JCT-9-U',  'JCT-14-U', 13, true, now(), now()),
    -- P22: JCT-14↔JCT-27 직결(19) 대신 게이트를 경유한다 — 13 + 6 = 19, 총비용 그대로.
    ('JCT-14-U', 'WH-GATE-U', 13, true, now(), now()),
    ('WH-GATE-U', 'JCT-27-U',  6, true, now(), now()),
    ('JCT-27-U', 'JCT-34-U', 7,  true, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, now(), now()),
    ('JCT-4-L',  'JCT-9-L',  13, true, now(), now()),
    ('JCT-9-L',  'JCT-14-L', 13, true, now(), now()),
    ('JCT-14-L', 'WH-GATE-L', 13, true, now(), now()),
    ('WH-GATE-L', 'JCT-27-L',  6, true, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, now(), now());

-- 명명된 노드 → 교차점. 창고동 쪽은 V16과 동일(도크·입출고 좌표 안 바뀜).
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
    -- P22: 생산동 AMR 충전 베이
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

-- 신관 엣지 — V16과 동일
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 11, true, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, now(), now());

-- 렉·엘리베이터·설비·POP 단말은 이번 마이그레이션에서 안 움직인다 — V16 값 그대로 유지.
