-- P33 — 생산동·품질동 통합. 설계 근거: docs/p33-production-quality-zone-merge-design.md
-- (D1~D6, "2-1" 보정 반영). 요청 원문: "생산동 품질동 가공동 조립물류동은 합치고
-- 거기 안에서 분류 하는것은 어떻게 생각에? 그럼 조금 더 로봇이 세밀하게 움직이는 것
-- 처럼 보일 수 있을것 같아."
--
-- **왜 지웠다 다시 넣는가.** V15/V16/V20/V21/V22/V23와 같은 이유 —
-- NodeMapLayoutConsistencyTest가 최신 마이그레이션 파일 하나를 정본으로 파싱한다.
--
-- **D1 — 건물 병합.** QC 건물 행을 지우고 PROD 폭을 32→44(x 58~102, 옛 QC 우측 끝까지)로
-- 늘린다. QC-IN/QC-OUT/JCT-62-U/L의 building_code만 'QC'→'PROD'로 바꾼다 — 좌표·노드
-- 코드는 무변경(opaque identifier 관례, PROD-A/B와 같은 방식). 조사 결과 QC라는 별도
-- "게이트"는 애초에 없었다(JCT-48↔JCT-62 직결 엣지뿐, GATE 타입 노드 없음) — D6가
-- 우려한 위험은 실재하지 않는다.
--
-- **D2/D3 — 좌표는 그대로, 물류(L) 구역만 신설.** QC-IN/QC-OUT 좌표(x=97)는 옮기지
-- 않는다 — 건물 통합만으로 "한 건물 안 이동"이라는 시각 효과는 달성된다. 대신
-- JCT-48(x=83)↔JCT-62(x=97) 직결 엣지(비용14) 사이 정중앙 x=90에 새 교차점
-- JCT-55-U/L을 끼워 7+7로 쪼갠다(기존 "Δx=비용" 관례). 그 교차점에 물류(WIP 스테이징)
-- 노드 PROD-L1(90, 13.5 — 상하단 통로 중간)을 매달아 JCT-55-U/L 양쪽에 비용 4.5로
-- 연결한다 — 요청 원문의 "로봇이 더 세밀하게 움직인다" 효과는 이 웨이포인트 하나가
-- 실제 라우팅 홉을 늘리는 데서 나온다(P32에서 창고동 밀도를 올렸을 때와 같은 원리).
--
-- **D4/D5/D6 — 조사 결과 할 일 없음.** QMS는 좌표·건물코드를 전혀 참조하지 않는다.
-- BLDG-A(pos_x=108)는 QC 우측 끝(102) 뒤에 이미 여유가 있고 QC 좌표를 안 옮기므로
-- 그대로 유지된다. PROD/QC 사이 게이트는 원래 없었다(위 D1 설명).

delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 173, 74, 9, 18, 10, now(), now(), now());

-- D1 — QC 건물 행 삭제, PROD 폭 확장(옛 QC 우측 끝까지).
-- layout_floors가 building_id FK로 걸려 있어(V9) 건물 행보다 먼저 그 층 행을 지워야
-- 한다 — 실제로 부팅해 보고 나서 발견(FK violation, layout_floors_building_id_fkey).
delete from layout_floors
    where building_id = (select id from layout_buildings where building_code = 'QC');
delete from layout_buildings where building_code = 'QC';
update layout_buildings set width = 44 where building_code = 'PROD';

delete from layout_edges;
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- ---- 창고동 1층 — V23 그대로(이번엔 창고동을 안 건드렸으므로 무변경) ----
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
    ('WH-SPINE-R-GATE-U', '우측 스파인 · 상단 게이트 접속', 'JUNCTION', 52, 9,  'WH', 1, now(), now()),
    ('WH-SPINE-R-GATE-L', '우측 스파인 · 하단 게이트 접속', 'JUNCTION', 52, 18, 'WH', 1, now(), now()),
    ('WH-SPINE-R-ELEV', '우측 스파인 · 엘리베이터 접속', 'JUNCTION', 52, 13.5, 'WH', 1, now(), now()),
    ('WH-RECV', '입고장', 'WAREHOUSE', 2, 3.00,  'WH', 1, now(), now()),
    ('WH-PICK', '피킹존', 'WAREHOUSE', 2, 33.50, 'WH', 1, now(), now()),
    ('WH-SHIP', '출하장', 'SHIPPING',  52, 65.70, 'WH', 1, now(), now()),
    ('WH-ELEV-1F', '엘리베이터 1층', 'ELEVATOR', 52, 13.5, 'WH', 1, now(), now()),
    ('WH-DOCK-1', '1번 충전 베이(AGV)', 'DOCK', 2.0, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이(AGV)', 'DOCK', 3.5, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이(AGV)', 'DOCK', 5.0, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이(AGV)', 'DOCK', 6.5, 68.5, 'WH', 1, now(), now()),
    ('WH-DOCK-5', '5번 충전 베이(AGV)', 'DOCK', 2.0, 70.0, 'WH', 1, now(), now()),
    ('WH-DOCK-6', '6번 충전 베이(AGV)', 'DOCK', 3.5, 70.0, 'WH', 1, now(), now()),
    ('WH-DOCK-7', '7번 충전 베이(AGV)', 'DOCK', 5.0, 70.0, 'WH', 1, now(), now()),
    ('WH-DOCK-8', '8번 충전 베이(AGV)', 'DOCK', 6.5, 70.0, 'WH', 1, now(), now()),
    -- ---- 창고동 2·3층 — 무변경(V23 그대로 옮겨 적는다) ----
    ('WH-DOCK-2F', '2층 충전 베이', 'DOCK',       4, 21, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  52, 13.5, 'WH', 2, now(), now()),
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4, 21, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  52, 13.5, 'WH', 3, now(), now()),
    -- ---- PROD(구 QC 포함)/신관 교차점 ----
    ('JCT-27-U', '연결로 62 · 상단 통로 교차점',  'JUNCTION', 62,  9, 'PROD', 1, now(), now()),
    ('JCT-27-L', '연결로 62 · 하단 통로 교차점',  'JUNCTION', 62, 18, 'PROD', 1, now(), now()),
    ('JCT-34-U', '연결로 69 · 상단 통로 교차점',  'JUNCTION', 69,  9, 'PROD', 1, now(), now()),
    ('JCT-34-L', '연결로 69 · 하단 통로 교차점',  'JUNCTION', 69, 18, 'PROD', 1, now(), now()),
    ('JCT-41-U', '연결로 76 · 상단 통로 교차점',  'JUNCTION', 76,  9, 'PROD', 1, now(), now()),
    ('JCT-41-L', '연결로 76 · 하단 통로 교차점',  'JUNCTION', 76, 18, 'PROD', 1, now(), now()),
    ('JCT-48-U', '연결로 83 · 상단 통로 교차점',  'JUNCTION', 83,  9, 'PROD', 1, now(), now()),
    ('JCT-48-L', '연결로 83 · 하단 통로 교차점',  'JUNCTION', 83, 18, 'PROD', 1, now(), now()),
    -- P33 D2/D3 — JCT-48↔JCT-62 직결(비용14) 사이 정중앙(x=90)에 새 교차점을 끼워
    -- 넣는다. 여기 매달린 PROD-L1이 "물류(WIP 스테이징)" 구역.
    ('JCT-55-U', '연결로 90 · 상단 통로 교차점(물류)', 'JUNCTION', 90,  9, 'PROD', 1, now(), now()),
    ('JCT-55-L', '연결로 90 · 하단 통로 교차점(물류)', 'JUNCTION', 90, 18, 'PROD', 1, now(), now()),
    ('PROD-L1', '물류 스테이징', 'WAREHOUSE', 90, 13.5, 'PROD', 1, now(), now()),
    -- P33 D1 — 옛 QC 건물이 폐지되고 PROD로 흡수됐다. 좌표·노드 코드는 무변경
    -- (opaque identifier 관례) — building_code만 'QC'→'PROD'.
    ('JCT-62-U', '연결로 97 · 상단 통로 교차점',  'JUNCTION', 97,  9, 'PROD', 1, now(), now()),
    ('JCT-62-L', '연결로 97 · 하단 통로 교차점',  'JUNCTION', 97, 18, 'PROD', 1, now(), now()),
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
    ('QC-IN',     '검사 입고',     'INSPECTION', 97, 21, 'PROD', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 97,  6, 'PROD', 1, now(), now()),
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',     108, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',  118, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  128, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',   135, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  145, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 158, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 ----
-- 좌측 스파인 — V23 그대로.
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

-- 우측 스파인 — V23 그대로.
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
    ('WH-SPINE-R-GATE-U', 'WH-GATE-U', 4.0, true, 2000, now(), now()),
    ('WH-SPINE-R-GATE-L', 'WH-GATE-L', 4.0, true, 2000, now(), now());

-- 밴드 아이슬(가로) — V23 그대로.
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

-- 기능 노드 → 가장 가까운 스파인 진입 노드 — V23 그대로.
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

-- ---- 창고동 2·3층 엣지 — V23 그대로 ----
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-DOCK-2F', 'WH-B04-L', 2.1,  true, 2000, now(), now()),
    ('WH-2F-P1',   'WH-B01-L', 17.0, true, 2000, now(), now()),
    ('WH-2F-P2',   'WH-B03-L', 17.4, true, 2000, now(), now()),
    ('WH-ELEV-2F', 'WH-SPINE-R-ELEV', 1.0, true, 2000, now(), now()),
    ('WH-DOCK-3F', 'WH-B04-L', 2.1,  true, 2000, now(), now()),
    ('WH-3F-P1',   'WH-B01-L', 17.0, true, 2000, now(), now()),
    ('WH-3F-P2',   'WH-B03-L', 17.4, true, 2000, now(), now()),
    ('WH-ELEV-3F', 'WH-SPINE-R-ELEV', 1.0, true, 2000, now(), now());

-- ---- 상단/하단 통로(PROD 내부, 구 QC 포함) ----
-- P33 D2/D3 — JCT-48↔JCT-62 직결(비용14)을 JCT-55 경유(7+7)로 교체하고, JCT-55-U/L에
-- 다른 JCT들과 같은 패턴(수직 연결, 비용=Δy=9)의 상하 연결을 추가한다.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('JCT-27-U', 'JCT-34-U', 7,  true, 2000, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, 2000, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, 2000, now(), now()),
    ('JCT-48-U', 'JCT-55-U', 7,  true, 2000, now(), now()),
    ('JCT-55-U', 'JCT-62-U', 7,  true, 2000, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, 2000, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, 2000, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, 2000, now(), now()),
    ('JCT-48-L', 'JCT-55-L', 7,  true, 2000, now(), now()),
    ('JCT-55-L', 'JCT-62-L', 7,  true, 2000, now(), now()),
    ('JCT-27-U', 'JCT-27-L', 9, true, 2000, now(), now()),
    ('JCT-34-U', 'JCT-34-L', 9, true, 2000, now(), now()),
    ('JCT-41-U', 'JCT-41-L', 9, true, 2000, now(), now()),
    ('JCT-48-U', 'JCT-48-L', 9, true, 2000, now(), now()),
    ('JCT-55-U', 'JCT-55-L', 9, true, 2000, now(), now()),
    ('JCT-62-U', 'JCT-62-L', 9, true, 2000, now(), now()),
    -- 물류(L) 구역 — JCT-55-U/L 양쪽에 짧은 엣지로 매단다(Δy=4.5 = 13.5-9, 18-13.5).
    ('PROD-L1', 'JCT-55-U', 4.5, true, 2000, now(), now()),
    ('PROD-L1', 'JCT-55-L', 4.5, true, 2000, now(), now()),
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
