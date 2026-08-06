-- P20-3: Building-A(가공동 신관) / Building-B(조립·물류동 신관)를 데이터만으로 추가한다.
-- 설계 근거: docs/p20-layout-routing-design.md D1(공유 캔버스 유지) / D3(노드 단위 그래프).
--
-- **왜 코드 변경이 필요 없는가.** P20-2에서 fleet의 LaneGraph가 컴파일타임 상수(연결로 8개·
-- 통로 2개) 대신 factory가 내려주는 노드-엣지 그래프를 다익스트라로 탐색하게 바뀌었다.
-- 그래서 새 건물을 붙이는 일은 fleet 코드를 전혀 건드리지 않고 여기 이 파일 하나로 끝난다 —
-- 이번 마이그레이션이 그 주장의 실제 증거다.
--
-- **왜 좌표를 옆으로(x=85~) 붙이는가, 위/아래가 아니라.** LaneGraph의 "가상 진입점" 탐색은
-- 로봇의 실시간 좌표가 그래프 노드가 아닐 때 "가장 가까운 연결로(x)"를 전역에서 찾는다(2D
-- 거리가 아니라 x만 본다 — D1 범위 내 근사). 새 건물을 기존 건물 바로 아래(y만 다르게)에
-- 붙이면 x가 겹쳐, 옛 건물 안에 있는 로봇의 진입점 탐색이 멀리 떨어진 새 건물의 연결로를
-- 더 가깝다고 잘못 고를 수 있다(y를 안 보므로). x를 충분히 멀리 떼어 두면(기존 최대 x=62,
-- 새 건물 시작 x=85, 간격 23) 이 문제가 원천적으로 생기지 않는다 — 코드를 건드리지 않고
-- 데이터 배치만으로 피한 것이다. 진짜 다중 층 레이아웃(x·y 둘 다 자유로운 배치)을 하려면
-- 진입점 탐색을 건물 소속 기준으로 바꿔야 한다 — 그건 P20-3 범위 밖으로 남긴다.
--
-- **평면도가 실제로 바뀌므로 버전을 올린다** — V13(그래프 데이터 모델 신설 자체)은 값이
-- 안 바뀌어 버전을 유지했지만, 이번엔 건물이 늘고 캔버스가 넓어진다(D8).

update layout_settings set width = 150, layout_version = 2, effective_from = now() where id = 1;

insert into layout_buildings
    (building_code, name, pos_x, pos_y, width, height, floor_count, display_order, created_at, updated_at) values
    ('BLDG-A', '가공동(신관)',     85,  1, 26, 24, 1, 4, now(), now()),
    ('BLDG-B', '조립·물류동(신관)', 118, 1, 26, 24, 1, 5, now(), now());

insert into layout_floors (building_id, floor_no, name, created_at, updated_at) values
    ((select id from layout_buildings where building_code = 'BLDG-A'), 1, '가공 라인(신관)', now(), now()),
    ((select id from layout_buildings where building_code = 'BLDG-B'), 1, '조립·물류 라인(신관)', now(), now());

-- ---- 노드 ----
-- GATE 타입 둘이 "문"이다 — GATE-WH-A는 기존 품질동(QC-OUT)에서 신관으로 들어가는 자리,
-- GATE-A-B는 가공동(신관)과 조립·물류동(신관) 사이의 문. 나머지는 그 안의 정차 자리.
insert into layout_nodes (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    ('GATE-WH-A', '신관 진입 게이트',   'GATE',      85, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',    'STATION',   95, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',    'STATION',  105, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',    112, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',    'STATION',  122, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)', 'WAREHOUSE', 135, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 ----
-- 기존 그래프(QC-OUT)에서 신관까지 한 줄로 이어지는 단순 체인 — 게이트를 두 번 거친다.
-- (수평 이동이라 구간 ID가 "AU:85-95"처럼 나온다 — 실제 상단 통로(y=9)는 아니지만
-- x범위가 옛 통로 구간과 겹치지 않아 충돌하지 않는다. LaneGraph의 구간 ID는 좌표로 계산되는
-- 불투명 문자열일 뿐이라 정확한 의미보다 유일성·안정성이 중요하다.)
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 23, true, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, now(), now());
