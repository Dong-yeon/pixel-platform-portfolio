-- P32 D8/D9 — 구현 후 대시보드 스크린샷에서 발견된 두 가지 보정. 설계 근거:
-- docs/p32-warehouse-realistic-relayout-design.md "2-1. 구현 중 발견된 추가 결정
-- (D8~D10)".
--
-- **왜 지웠다 다시 넣는가.** V15/V16/V20/V21/V22와 같은 이유 —
-- NodeMapLayoutConsistencyTest가 최신 마이그레이션 파일 하나를 정본으로 파싱한다.
-- 이번에 실제로 바뀌는 건 엘리베이터 좌표·접속 엣지 몇 줄뿐이지만, 나머지도 전부
-- 그대로 옮겨 적어야 그 정본 대조가 성립한다.
--
-- **D8 — 엘리베이터가 렉 그리드 한복판에 있었다.** V22가 WH-ELEV-1F/2F/3F 좌표를
-- (30,13) 그대로 둔 채 밴드 그리드만 새로 깔아서, 엘리베이터가 밴드3(y=15.4)
-- 근처 렉 열 한복판에 파묻힌 모양이 됐다. 우측 스파인(x=52) 위, 게이트 접속
-- 스플라이스(WH-SPINE-R-GATE-U/L)와 같은 패턴으로 WH-B02-R↔WH-B03-R 엣지
-- 사이에 WH-SPINE-R-ELEV(52, 13.5)를 끼워 넣는다 — 옛 y=13과 가장 가까운
-- 지점이면서 스파인 선상이라 어느 밴드에서도 스파인 경유로 도달 가능하다.
--
-- **2·3층도 같이 옮긴다.** D5(2·3층 무변경)의 명시적 예외다 — 세 층 다 물리적으로
-- 같은 샤프트를 쓰므로(층마다 다른 자리일 수 없다) 애초에 좌표가 갈라져 있던
-- 게 이번에 고치는 버그다. 2·3층의 다른 노드(피킹존·도크)는 안 건드린다.
--
-- **layout_elevators도 같이 UPDATE한다** — V15/V16 전례(노드 이동 시 이 테이블도
-- 같이 맞춘 것)를 따른다. 안 하면 대시보드 엘리베이터 아이콘(⇅, layout.elevators
-- 기준)과 관제 서버가 실제로 쓰는 노드 좌표가 어긋난다.
--
-- **D9 — 충전존과 렉 4기가 겹쳤다.** CZ-1F(좌상단 1.0,67.5 / 7.0×5.5 → x∈[1,8],
-- y∈[67.5,73])와 밴드12 뒷줄(pos_y=68.45) 앞쪽 4기(R37~R40, x=3.75~7.65)가
-- 좌표상 실제로 겹친다. 렉 생성 루프에서 이 4칸을 건너뛰는 쪽(제외 구역을 먼저
-- 예약)으로 고친다 — 충전존 좌표 자체는 P29 패턴 그대로 유지. 렉 총량
-- 864 → 860. robot-sim RackMap·fleet LocationRegistry도 같은 4칸을 생성에서
-- 건너뛰도록 같이 맞춘다.

delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 173, 74, 9, 18, 9, now(), now(), now());

-- D8 — 엘리베이터 노드와 같은 좌표로. 대시보드 엘리베이터 아이콘(⇅)이 이 테이블을 그린다.
update layout_elevators set pos_x = 52, pos_y = 13.5, updated_at = now()
    where elevator_code = 'WH-ELEV';

update layout_buildings set height = 72 where building_code = 'WH';
-- PROD/QC/신관은 x좌표·width 전혀 무변경(폭을 안 늘렸으므로 밀 필요가 없다).

delete from layout_edges;
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- ---- 창고동 1층 — 좌우 스파인(수직) + 밴드 12개 진입 노드(가로 아이슬의 양 끝) ----
    -- 좌측 스파인: 입고·충전존 쪽. 우측 스파인: 게이트(PROD) 쪽.
    ('WH-B01-L', '밴드1 좌측 진입',  'JUNCTION', 2,  4.00, 'WH', 1, now(), now()),
    ('WH-B01-R', '밴드1 우측 진입',  'JUNCTION', 52, 4.00, 'WH', 1, now(), now()),
    ('WH-B02-L', '밴드2 좌측 진입',  'JUNCTION', 2,  9.70, 'WH', 1, now(), now()),
    ('WH-B02-R', '밴드2 우측 진입',  'JUNCTION', 52, 9.70, 'WH', 1, now(), now()),
    ('WH-B03-L', '밴드3 좌측 진입',  'JUNCTION', 2, 15.40, 'WH', 1, now(), now()),
    ('WH-B03-R', '밴드3 우측 진입',  'JUNCTION', 52, 15.40, 'WH', 1, now(), now()),
    ('WH-B04-L', '밴드4 좌측 진입',  'JUNCTION', 2, 21.10, 'WH', 1, now(), now()),
    ('WH-B04-R', '밴드4 우측 진입',  'JUNCTION', 52, 21.10, 'WH', 1, now(), now()),
    ('WH-B05-L', '밴드5 좌측 진입',  'JUNCTION', 2, 26.80, 'WH', 1, now(), now()),
    ('WH-B05-R', '밴드5 우측 진입',  'JUNCTION', 52, 26.80, 'WH', 1, now(), now()),
    ('WH-B06-L', '밴드6 좌측 진입',  'JUNCTION', 2, 32.50, 'WH', 1, now(), now()),
    ('WH-B06-R', '밴드6 우측 진입',  'JUNCTION', 52, 32.50, 'WH', 1, now(), now()),
    ('WH-B07-L', '밴드7 좌측 진입',  'JUNCTION', 2, 38.20, 'WH', 1, now(), now()),
    ('WH-B07-R', '밴드7 우측 진입',  'JUNCTION', 52, 38.20, 'WH', 1, now(), now()),
    ('WH-B08-L', '밴드8 좌측 진입',  'JUNCTION', 2, 43.90, 'WH', 1, now(), now()),
    ('WH-B08-R', '밴드8 우측 진입',  'JUNCTION', 52, 43.90, 'WH', 1, now(), now()),
    ('WH-B09-L', '밴드9 좌측 진입',  'JUNCTION', 2, 49.60, 'WH', 1, now(), now()),
    ('WH-B09-R', '밴드9 우측 진입',  'JUNCTION', 52, 49.60, 'WH', 1, now(), now()),
    ('WH-B10-L', '밴드10 좌측 진입', 'JUNCTION', 2, 55.30, 'WH', 1, now(), now()),
    ('WH-B10-R', '밴드10 우측 진입', 'JUNCTION', 52, 55.30, 'WH', 1, now(), now()),
    ('WH-B11-L', '밴드11 좌측 진입', 'JUNCTION', 2, 61.00, 'WH', 1, now(), now()),
    ('WH-B11-R', '밴드11 우측 진입', 'JUNCTION', 52, 61.00, 'WH', 1, now(), now()),
    ('WH-B12-L', '밴드12 좌측 진입', 'JUNCTION', 2, 66.70, 'WH', 1, now(), now()),
    ('WH-B12-R', '밴드12 우측 진입', 'JUNCTION', 52, 66.70, 'WH', 1, now(), now()),
    -- 우측 스파인이 게이트(y=9/18)와 만나는 접속점 — D1 "게이트 좌표 무변경" 요구사항.
    ('WH-SPINE-R-GATE-U', '우측 스파인 · 상단 게이트 접속', 'JUNCTION', 52, 9,  'WH', 1, now(), now()),
    ('WH-SPINE-R-GATE-L', '우측 스파인 · 하단 게이트 접속', 'JUNCTION', 52, 18, 'WH', 1, now(), now()),
    -- 우측 스파인이 엘리베이터와 만나는 접속점(P32 D8, V23) — WH-B02-R/WH-B03-R 사이.
    ('WH-SPINE-R-ELEV', '우측 스파인 · 엘리베이터 접속', 'JUNCTION', 52, 13.5, 'WH', 1, now(), now()),
    -- 기능 노드 — 입고·피킹·출하는 가까운 밴드 진입 노드 옆에 붙인다.
    ('WH-RECV', '입고장', 'WAREHOUSE', 2, 3.00,  'WH', 1, now(), now()),
    ('WH-PICK', '피킹존', 'WAREHOUSE', 2, 33.50, 'WH', 1, now(), now()),
    ('WH-SHIP', '출하장', 'SHIPPING',  52, 65.70, 'WH', 1, now(), now()),
    -- 엘리베이터 1층 — P32 D8(V23)로 우측 스파인 위(52,13.5)로 재배치. 원래(30,13)는
    -- 밴드 그리드 한복판에 파묻혀 있었다(구현 후 스크린샷으로 발견).
    ('WH-ELEV-1F', '엘리베이터 1층', 'ELEVATOR', 52, 13.5, 'WH', 1, now(), now()),
    -- 충전 도크 8개 — 좌하단 코너(새 좌표계 기준, 밴드12 아래) 클러스터(P29 패턴 재사용, D4).
    ('WH-DOCK-1', '1번 충전 베이(AGV)', 'DOCK', 2.0, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이(AGV)', 'DOCK', 3.5, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이(AGV)', 'DOCK', 5.0, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이(AGV)', 'DOCK', 6.5, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-5', '5번 충전 베이(AGV)', 'DOCK', 2.0, 70.0, 'WH', 1, now(), now()),
    ('WH-DOCK-6', '6번 충전 베이(AGV)', 'DOCK', 3.5, 70.0, 'WH', 1, now(), now()),
    ('WH-DOCK-7', '7번 충전 베이(AGV)', 'DOCK', 5.0, 70.0, 'WH', 1, now(), now()),
    ('WH-DOCK-8', '8번 충전 베이(AGV)', 'DOCK', 6.5, 70.0, 'WH', 1, now(), now()),
    -- ---- 창고동 2·3층 — 완전 무변경(D5), V21 그대로 옮겨 적는다. 단 엘리베이터는
    -- 예외(D8, V23) — 세 층 다 같은 샤프트라 1층과 같이 옮긴다.
    ('WH-DOCK-2F', '2층 충전 베이', 'DOCK',       4, 21, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  52, 13.5, 'WH', 2, now(), now()),
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4, 21, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  52, 13.5, 'WH', 3, now(), now()),
    -- ---- PROD/QC/신관 교차점 — V21 그대로(창고동 내부만 바뀌었으므로 무변경) ----
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
    ('WH-GATE-U', '창고동 게이트 · 상단', 'GATE', 56,  9, 'PROD', 1, now(), now()),
    ('WH-GATE-L', '창고동 게이트 · 하단', 'GATE', 56, 18, 'PROD', 1, now(), now()),
    ('PROD-DOCK-1', '1번 충전 베이(AMR)', 'DOCK', 62,  3, 'PROD', 1, now(), now()),
    ('PROD-DOCK-2', '2번 충전 베이(AMR)', 'DOCK', 62,  5, 'PROD', 1, now(), now()),
    ('PROD-DOCK-3', '3번 충전 베이(AMR)', 'DOCK', 62, 21, 'PROD', 1, now(), now()),
    ('PROD-DOCK-4', '4번 충전 베이(AMR)', 'DOCK', 62, 23, 'PROD', 1, now(), now()),
    ('PROD-A1',   'A1 하역',       'STATION',   62,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   69,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   76,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   83,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   62, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   69, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   76, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   83, 21, 'PROD', 1, now(), now()),
    ('QC-IN',     '검사 입고',     'INSPECTION', 97, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 97,  6, 'QC', 1, now(), now()),
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',     108, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',  118, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  128, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',   135, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  145, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 158, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 ----
-- 좌측 스파인 — 밴드 12개 진입 노드를 y순으로 잇는 수직 체인(비용=Δy=5.7).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-B01-L', 'WH-B02-L', 5.7, true, 2000, now(), now()),
    ('WH-B02-L', 'WH-B03-L', 5.7, true, 2000, now(), now()),
    ('WH-B03-L', 'WH-B04-L', 5.7, true, 2000, now(), now()),
    ('WH-B04-L', 'WH-B05-L', 5.7, true, 2000, now(), now()),
    ('WH-B05-L', 'WH-B06-L', 5.7, true, 2000, now(), now()),
    ('WH-B06-L', 'WH-B07-L', 5.7, true, 2000, now(), now()),
    ('WH-B07-L', 'WH-B08-L', 5.7, true, 2000, now(), now()),
    ('WH-B08-L', 'WH-B09-L', 5.7, true, 2000, now(), now()),
    ('WH-B09-L', 'WH-B10-L', 5.7, true, 2000, now(), now()),
    ('WH-B10-L', 'WH-B11-L', 5.7, true, 2000, now(), now()),
    ('WH-B11-L', 'WH-B12-L', 5.7, true, 2000, now(), now());

-- 우측 스파인 — 게이트 접속점 2개 + 엘리베이터 접속점(D8, V23)을 정확한 y순서
-- (밴드1<게이트U<밴드2<엘리베이터<밴드3<게이트L<밴드4<...)로 끼워 넣는다. 비용은 전부 Δy
-- (엘리베이터 접속: 13.5-9.7=3.8, 15.4-13.5=1.9).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-B01-R', 'WH-SPINE-R-GATE-U', 5.0, true, 2000, now(), now()),
    ('WH-SPINE-R-GATE-U', 'WH-B02-R', 0.7, true, 2000, now(), now()),
    ('WH-B02-R', 'WH-SPINE-R-ELEV', 3.8, true, 2000, now(), now()),
    ('WH-SPINE-R-ELEV', 'WH-B03-R', 1.9, true, 2000, now(), now()),
    ('WH-B03-R', 'WH-SPINE-R-GATE-L', 2.6, true, 2000, now(), now()),
    ('WH-SPINE-R-GATE-L', 'WH-B04-R', 3.1, true, 2000, now(), now()),
    ('WH-B04-R', 'WH-B05-R', 5.7, true, 2000, now(), now()),
    ('WH-B05-R', 'WH-B06-R', 5.7, true, 2000, now(), now()),
    ('WH-B06-R', 'WH-B07-R', 5.7, true, 2000, now(), now()),
    ('WH-B07-R', 'WH-B08-R', 5.7, true, 2000, now(), now()),
    ('WH-B08-R', 'WH-B09-R', 5.7, true, 2000, now(), now()),
    ('WH-B09-R', 'WH-B10-R', 5.7, true, 2000, now(), now()),
    ('WH-B10-R', 'WH-B11-R', 5.7, true, 2000, now(), now()),
    ('WH-B11-R', 'WH-B12-R', 5.7, true, 2000, now(), now()),
    -- 게이트 접속 — D1 계약 유지(WH-GATE-U/L 좌표 무변경).
    ('WH-SPINE-R-GATE-U', 'WH-GATE-U', 4.0, true, 2000, now(), now()),
    ('WH-SPINE-R-GATE-L', 'WH-GATE-L', 4.0, true, 2000, now(), now());

-- 밴드 아이슬(가로) — D3에서 TrafficController가 배타 잠금을 거는 세그먼트 12개(밴드당 1개,
-- "한 번에 한 AGV만 그 밴드에" — 좁고 긴 통로의 hold-and-wait 교착 방지).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-B01-L', 'WH-B01-R', 50, true, 2000, now(), now()),
    ('WH-B02-L', 'WH-B02-R', 50, true, 2000, now(), now()),
    ('WH-B03-L', 'WH-B03-R', 50, true, 2000, now(), now()),
    ('WH-B04-L', 'WH-B04-R', 50, true, 2000, now(), now()),
    ('WH-B05-L', 'WH-B05-R', 50, true, 2000, now(), now()),
    ('WH-B06-L', 'WH-B06-R', 50, true, 2000, now(), now()),
    ('WH-B07-L', 'WH-B07-R', 50, true, 2000, now(), now()),
    ('WH-B08-L', 'WH-B08-R', 50, true, 2000, now(), now()),
    ('WH-B09-L', 'WH-B09-R', 50, true, 2000, now(), now()),
    ('WH-B10-L', 'WH-B10-R', 50, true, 2000, now(), now()),
    ('WH-B11-L', 'WH-B11-R', 50, true, 2000, now(), now()),
    ('WH-B12-L', 'WH-B12-R', 50, true, 2000, now(), now());

-- 기능 노드 → 가장 가까운 스파인 진입 노드. 엘리베이터는 D8(V23)로 스파인 접속점과
-- 같은 자리가 돼서 비용이 작다(옛 (30,13) 좌표일 때는 25.3/24.4나 되는 먼 거리였다).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-RECV', 'WH-B01-L', 1.0, true, 2000, now(), now()),
    ('WH-PICK', 'WH-B06-L', 1.0, true, 2000, now(), now()),
    ('WH-SHIP', 'WH-B12-R', 1.0, true, 2000, now(), now()),
    ('WH-ELEV-1F', 'WH-SPINE-R-ELEV', 1.0, true, 2000, now(), now()),
    ('WH-DOCK-1', 'WH-B12-L', 1.8, true, 2000, now(), now()),
    ('WH-DOCK-2', 'WH-B12-L', 1.8, true, 2000, now(), now()),
    ('WH-DOCK-3', 'WH-B12-L', 1.8, true, 2000, now(), now()),
    ('WH-DOCK-4', 'WH-B12-L', 1.8, true, 2000, now(), now()),
    ('WH-DOCK-5', 'WH-B12-L', 3.3, true, 2000, now(), now()),
    ('WH-DOCK-6', 'WH-B12-L', 3.3, true, 2000, now(), now()),
    ('WH-DOCK-7', 'WH-B12-L', 3.3, true, 2000, now(), now()),
    ('WH-DOCK-8', 'WH-B12-L', 3.3, true, 2000, now(), now());

-- ---- 창고동 2·3층 엣지 ----
-- 좌표(D5: 무변경)는 그대로지만, 그 좌표가 기대던 옛 연결로(JCT-4/9/14)가 이번에 전부
-- 스파인으로 교체됐다 — 2·3층도 결국 같은 그래프 하나를 공유했으므로(1층과 물리적으로
-- 같은 자리) 연결 "대상"만 가장 가까운 새 스파인 노드로 갈아 끼운다. 노드 좌표 자체는
-- 1바이트도 안 바꿨다(D5 준수) — 바뀐 건 "어디로 가야 도달하는가"뿐이다.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-DOCK-2F', 'WH-B04-L', 2.1,  true, 2000, now(), now()),
    ('WH-2F-P1',   'WH-B01-L', 17.0, true, 2000, now(), now()),
    ('WH-2F-P2',   'WH-B03-L', 17.4, true, 2000, now(), now()),
    ('WH-ELEV-2F', 'WH-SPINE-R-ELEV', 1.0, true, 2000, now(), now()),
    ('WH-DOCK-3F', 'WH-B04-L', 2.1,  true, 2000, now(), now()),
    ('WH-3F-P1',   'WH-B01-L', 17.0, true, 2000, now(), now()),
    ('WH-3F-P2',   'WH-B03-L', 17.4, true, 2000, now(), now()),
    ('WH-ELEV-3F', 'WH-SPINE-R-ELEV', 1.0, true, 2000, now(), now());
-- ---- 상단/하단 통로 나머지 구간(PROD·QC 내부) — V21 그대로(창고동만 바뀌었으므로 무변경) ----
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('JCT-27-U', 'JCT-34-U', 7,  true, 2000, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, 2000, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, 2000, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, 2000, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, 2000, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, 2000, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, 2000, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, 2000, now(), now()),
    ('JCT-27-U', 'JCT-27-L', 9, true, 2000, now(), now()),
    ('JCT-34-U', 'JCT-34-L', 9, true, 2000, now(), now()),
    ('JCT-41-U', 'JCT-41-L', 9, true, 2000, now(), now()),
    ('JCT-48-U', 'JCT-48-L', 9, true, 2000, now(), now()),
    ('JCT-62-U', 'JCT-62-L', 9, true, 2000, now(), now()),
    ('WH-GATE-U', 'JCT-27-U',  6, true, 2000, now(), now()),
    ('WH-GATE-L', 'JCT-27-L',  6, true, 2000, now(), now()),
    ('PROD-DOCK-1', 'JCT-27-U', 6, true, 2000, now(), now()),
    ('PROD-DOCK-2', 'JCT-27-U', 4, true, 2000, now(), now()),
    ('PROD-DOCK-3', 'JCT-27-L', 3, true, 2000, now(), now()),
    ('PROD-DOCK-4', 'JCT-27-L', 5, true, 2000, now(), now()),
    ('PROD-A1', 'JCT-27-U', 3, true, 2000, now(), now()),
    ('PROD-A2', 'JCT-34-U', 3, true, 2000, now(), now()),
    ('PROD-A3', 'JCT-41-U', 3, true, 2000, now(), now()),
    ('PROD-A4', 'JCT-48-U', 3, true, 2000, now(), now()),
    ('PROD-B1', 'JCT-27-L', 3, true, 2000, now(), now()),
    ('PROD-B2', 'JCT-34-L', 3, true, 2000, now(), now()),
    ('PROD-B3', 'JCT-41-L', 3, true, 2000, now(), now()),
    ('PROD-B4', 'JCT-48-L', 3, true, 2000, now(), now()),
    ('QC-OUT', 'JCT-62-U', 3, true, 2000, now(), now()),
    ('QC-IN',  'JCT-62-L', 3, true, 2000, now(), now());

-- 신관(BLDG-A/B) — V21 그대로.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 11, true, 2000, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, 2000, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, 2000, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, 2000, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, 2000, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, 2000, now(), now());

-- ---- D9: 충전존과 겹치는 렉 4기 제거 ----
delete from layout_racks
where rack_code in ('WH-1F-B12-R37', 'WH-1F-B12-R38', 'WH-1F-B12-R39', 'WH-1F-B12-R40');
