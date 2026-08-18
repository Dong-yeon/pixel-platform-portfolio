-- 창고동 폭 확대 — 렉 27기·3개 층을 가진 건물이 도크 3개뿐인 생산동보다도 좁았다.
--
-- **무엇을 바꾸는가.** 창고동 폭을 18 → 30으로 넓히고, 내부 연결로 3개(4·9·14)를 더 넓게
-- 벌린다(4·13·22). 렉·입출고 노드·엘리베이터는 그 연결로 배치를 그대로 따라간다. 생산동·
-- 품질동은 건물 폭이 안 바뀌므로 내부 배치는 그대로 두고, 창고동이 늘어난 만큼(+12)
-- 오른쪽으로 통째로 민다. 신관 두 동(BLDG-A/B, V14)은 여백이 충분해 그대로 둔다.
--
-- **좌표 코드는 그대로 둔다.** JCT-9-U는 이제 x=13이고 JCT-14-U는 x=22다 — 코드의 숫자가
-- 실제 좌표와 더는 안 맞는다. 그래도 이름을 안 바꾸는 이유는, LaneGraph의 구간 ID처럼
-- 이 코드들도 처음부터 "불투명 식별자"로 다뤄져 왔고(LaneGraph.java 참고), 이름을 바꾸면
-- layout_edges의 FK(node_code 참조)가 걸려 있는 행을 전부 지웠다 다시 넣어야 해서 위험만
-- 커진다. 대신 이 주석이 그 사실을 기록해 둔다.
--
-- **왜 노드·엣지를 지웠다 다시 넣는가.** robot-sim의 NodeMapLayoutConsistencyTest는 이
-- 파일을 정본으로 파싱해 좌표를 대조한다(V9→V12처럼 "평면도를 다시 그리는" 마이그레이션이
-- 되면 그 경로도 옮겨야 한다 — V12 헤더 주석이 이미 그렇게 경고해 뒀다). 대조 대상이 되려면
-- INSERT 문 형태로 전체를 다시 선언해야 한다. layout_edges가 layout_nodes.node_code를
-- FK로 참조하므로 삭제 순서는 엣지 → 노드다.
--
-- **렉·엘리베이터는 UPDATE만 한다.** 코드 집합이 안 바뀌므로(RackMapLayoutConsistencyTest는
-- 코드만 본다) 좌표만 옮기면 된다 — 지웠다 다시 넣을 이유가 없다.

-- delete+insert로 쓴다(UPDATE가 아니라) — NodeMapLayoutConsistencyTest의 SETTINGS_ROW
-- 정규식이 "values (1, 폭, 높이, 상단통로y, 하단통로y" 형태의 INSERT문만 파싱한다(V9·V12와
-- 같은 모양). V14는 이 값을 UPDATE로 바꿨지만 그때는 테스트가 V12를 보고 있어 상관없었다 —
-- 이제 이 파일이 정본이 되므로 파싱 가능한 형태를 지켜야 한다.
delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 150, 26, 9, 18, 3, now(), now(), now());
-- height·통로 y는 안 바뀐다(9, 18) — 이 마이그레이션은 세로 좌표를 하나도 건드리지 않는다.

update layout_buildings set width = 30 where building_code = 'WH';
update layout_buildings set pos_x = 35 where building_code = 'PROD';
update layout_buildings set pos_x = 71 where building_code = 'QC';
-- BLDG-A(85)·BLDG-B(118)는 그대로 — 품질동이 71~79로 옮겨도 85까지 여유(6)가 남는다.

-- ---- 엣지 먼저 지운다(FK) ----
delete from layout_edges;

-- ---- 노드 재배치 ----
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- 창고동 1층 — 충전 베이는 그대로(연결로 4 곁에 둔 자리라 옮길 이유가 없다)
    ('WH-DOCK-1', '1번 충전 베이', 'DOCK',       4,  3, 'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이', 'DOCK',       4,  5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이', 'DOCK',       4, 21, 'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이', 'DOCK',       4, 23, 'WH', 1, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE', 13,  6, 'WH', 1, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE', 13, 13, 'WH', 1, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  22, 21, 'WH', 1, now(), now()),
    ('WH-ELEV-1F','엘리베이터 1층','ELEVATOR',  22, 13, 'WH', 1, now(), now()),
    -- 창고동 2층
    ('WH-DOCK-2F','2층 충전 베이', 'DOCK',       4,  3, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 13,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 13, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  22, 13, 'WH', 2, now(), now()),
    -- 창고동 3층
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4,  3, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 13,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 13, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  22, 13, 'WH', 3, now(), now()),
    -- 교차점(JUNCTION) — 창고동 2개(연결로 13·22로 재배치), 생산동·품질동은 +12 통째 이동
    ('JCT-4-U',  '연결로 4 · 상단 통로 교차점',  'JUNCTION', 4,  9, 'WH',   1, now(), now()),
    ('JCT-4-L',  '연결로 4 · 하단 통로 교차점',  'JUNCTION', 4, 18, 'WH',   1, now(), now()),
    ('JCT-9-U',  '연결로 13 · 상단 통로 교차점', 'JUNCTION', 13,  9, 'WH',   1, now(), now()),
    ('JCT-9-L',  '연결로 13 · 하단 통로 교차점', 'JUNCTION', 13, 18, 'WH',   1, now(), now()),
    ('JCT-14-U', '연결로 22 · 상단 통로 교차점', 'JUNCTION', 22,  9, 'WH',   1, now(), now()),
    ('JCT-14-L', '연결로 22 · 하단 통로 교차점', 'JUNCTION', 22, 18, 'WH',   1, now(), now()),
    ('JCT-27-U', '연결로 39 · 상단 통로 교차점', 'JUNCTION', 39,  9, 'PROD', 1, now(), now()),
    ('JCT-27-L', '연결로 39 · 하단 통로 교차점', 'JUNCTION', 39, 18, 'PROD', 1, now(), now()),
    ('JCT-34-U', '연결로 46 · 상단 통로 교차점', 'JUNCTION', 46,  9, 'PROD', 1, now(), now()),
    ('JCT-34-L', '연결로 46 · 하단 통로 교차점', 'JUNCTION', 46, 18, 'PROD', 1, now(), now()),
    ('JCT-41-U', '연결로 53 · 상단 통로 교차점', 'JUNCTION', 53,  9, 'PROD', 1, now(), now()),
    ('JCT-41-L', '연결로 53 · 하단 통로 교차점', 'JUNCTION', 53, 18, 'PROD', 1, now(), now()),
    ('JCT-48-U', '연결로 60 · 상단 통로 교차점', 'JUNCTION', 60,  9, 'PROD', 1, now(), now()),
    ('JCT-48-L', '연결로 60 · 하단 통로 교차점', 'JUNCTION', 60, 18, 'PROD', 1, now(), now()),
    ('JCT-62-U', '연결로 74 · 상단 통로 교차점', 'JUNCTION', 74,  9, 'QC',   1, now(), now()),
    ('JCT-62-L', '연결로 74 · 하단 통로 교차점', 'JUNCTION', 74, 18, 'QC',   1, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장) — 전부 +12
    ('PROD-A1',   'A1 하역',       'STATION',   39,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   46,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   53,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   60,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   39, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   46, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   53, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   60, 21, 'PROD', 1, now(), now()),
    -- 품질동 — +12
    ('QC-IN',     '검사 입고',     'INSPECTION', 74, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 74,  6, 'QC', 1, now(), now()),
    -- 신관(V14) — 이번 마이그레이션에서 안 움직인다. 그래도 위 delete가 테이블 전체를
    -- 비우므로 여기서 다시 넣지 않으면 유실된다(아래 신관 엣지가 참조하는 노드이기도 하다).
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',      85, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',   95, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  105, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',    112, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  122, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 135, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 다시 넣는다 ----
-- 값은 V13과 대부분 같다. 바뀐 것은 창고동 내부 세 엣지(연결로 간격이 5→9로 벌어짐)와
-- 창고동↔생산동 경계 하나(13→17)뿐이다 — 나머지는 양 끝이 같은 +12로 움직여 거리가 그대로다.
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
    ('JCT-4-U',  'JCT-9-U',  9,  true, now(), now()),  -- 5 → 9  (4~13)
    ('JCT-9-U',  'JCT-14-U', 9,  true, now(), now()),  -- 5 → 9  (13~22)
    ('JCT-14-U', 'JCT-27-U', 17, true, now(), now()),  -- 13 → 17 (22~39)
    ('JCT-27-U', 'JCT-34-U', 7,  true, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, now(), now()),
    ('JCT-4-L',  'JCT-9-L',  9,  true, now(), now()),
    ('JCT-9-L',  'JCT-14-L', 9,  true, now(), now()),
    ('JCT-14-L', 'JCT-27-L', 17, true, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, now(), now());

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

-- V14가 신관(가공동·조립동)으로 잇는 엣지 6개 — 위에서 layout_edges를 통째로 지웠으므로
-- 여기서 다시 넣지 않으면 신관 전체가 그래프에서 끊긴다(실제로 처음에 빠뜨릴 뻔했다).
-- GATE-WH-A(85)는 이번 마이그레이션에서 안 움직이는데 QC-OUT은 62→74로 옮겼으므로,
-- 그 사이 거리(비용)만 23 → 11로 줄여서 넣는다. 나머지 다섯은 V14와 값이 같다.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 11, true, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, now(), now());

-- ---- 렉 재배치 ---- 코드·열·단·용량은 그대로, x만 넓어진 폭에 맞춰 다시 편다(y는 안 바뀐다).
-- 세 기둥(A/B/C)이 연결로(4/13/22)에서 각각 4.5 이상 떨어지도록 8.5 / 17.5 / 26.5로 편다.
update layout_racks r set pos_x = v.new_x, updated_at = now()
from (values
    ('R01', 8.5), ('R02', 17.5), ('R03', 26.5),
    ('R04', 8.5), ('R05', 17.5), ('R06', 26.5),
    ('R07', 8.5), ('R08', 17.5), ('R09', 26.5)
) as v(suffix, new_x)
where r.rack_code like 'WH-_F-' || v.suffix;

-- ---- 엘리베이터 ---- 노드(WH-ELEV-*F)와 같은 자리를 가리켜야 한다(같은 샤프트).
update layout_elevators set pos_x = 22, updated_at = now() where elevator_code = 'WH-ELEV';

-- ---- 설비 8대 ---- V9에서 생산동 연결로(27/34/41/48)와 같은 x에 맞춰 뒀다(하역 지점 바로
-- 위/아래). 그 좌표를 그대로 두면 생산동이 +12 밀린 뒤 설비만 옛 자리(창고동↔생산동 사이
-- 빈 틈)에 남아 건물 밖으로 떨어져 나간다 — layout_nodes를 고치면서 이걸 빠뜨릴 뻔했다.
update equipments e set pos_x = v.new_x, updated_at = now()
from (values
    ('CNC-01', 39.0), ('CNC-02', 46.0), ('CNC-03', 53.0), ('MCT-01', 60.0),
    ('ASM-01', 39.0), ('ASM-02', 46.0), ('INS-01', 53.0), ('PKG-01', 60.0)
) as v(code, new_x)
where e.equipment_code = v.code;

-- ---- POP 단말 2개 ---- 생산동 입구 쪽(x=24, V9에서 잡은 자리)도 같은 이유로 옮긴다.
update pop_terminals t set pos_x = v.new_x, updated_at = now()
from (values ('POP-A1', 36.0), ('POP-B1', 36.0)) as v(code, new_x)
where t.terminal_code = v.code;
