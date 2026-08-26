-- P29: 창고동 충전 도크를 좌하단 한 코너로 모은다.
-- 설계 근거: docs/p29-warehouse-charge-corner-design.md D1·D2.
--
-- **왜.** 실무 창고 평면도를 참고 이미지로 받았다 — 충전 포인트가 모서리 하나에 여러 개
-- 뭉쳐 있는 구조다. 지금은 WH-DOCK-1/2(위쪽, y=3/5)와 WH-DOCK-3/4(아래쪽, y=21/23)가
-- 좌측 벽 위아래로 갈라져 있다. 도크 좌표만 코너로 모으고 그 외(렉·통로·연결로·건물
-- 크기)는 전혀 안 바꾼다.
--
-- **왜 노드·엣지를 통째로 지웠다 다시 넣는가.** V14~V17과 같은 이유 —
-- NodeMapLayoutConsistencyTest가 "최신 평면도 마이그레이션 파일 하나"를 정본으로 파싱한다.
-- 이번에도 이 파일이 새 정본이 된다(테스트의 MIGRATION 경로도 함께 옮긴다).
--
-- **연결 교차점도 같이 바꾼다.** 도크가 전부 하단 통로(y=18) 아래로 내려가므로, 기존에
-- JCT-4-U(상단)에 붙어 있던 WH-DOCK-1/2/2F/3F도 JCT-4-L(하단)로 다시 붙인다 — 좌표만
-- 옮기고 엣지 대상을 안 바꾸면 그래프가 끊긴 도크가 생긴다(설계 문서 4절 리스크 참고).

delete from layout_settings;
insert into layout_settings
    (id, width, height, upper_aisle_y, lower_aisle_y, layout_version, effective_from, created_at, updated_at)
values (1, 160, 26, 9, 18, 6, now(), now(), now());

delete from layout_edges;
delete from layout_nodes;

insert into layout_nodes
    (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    -- 창고동 1층 — 도크 4개를 좌하단 코너로(P29). WH-DOCK-3/4는 원래 이미 이 코너였다.
    ('WH-DOCK-1', '1번 충전 베이(AGV)', 'DOCK',       4, 19,   'WH', 1, now(), now()),
    ('WH-DOCK-2', '2번 충전 베이(AGV)', 'DOCK',       4, 20.5, 'WH', 1, now(), now()),
    ('WH-DOCK-3', '3번 충전 베이(AGV)', 'DOCK',       4, 21,   'WH', 1, now(), now()),
    ('WH-DOCK-4', '4번 충전 베이(AGV)', 'DOCK',       4, 23,   'WH', 1, now(), now()),
    ('WH-RECV',   '입고장',        'WAREHOUSE', 17,  6, 'WH', 1, now(), now()),
    ('WH-PICK',   '피킹존',        'WAREHOUSE', 17, 13, 'WH', 1, now(), now()),
    ('WH-SHIP',   '출하장',        'SHIPPING',  30, 21, 'WH', 1, now(), now()),
    ('WH-ELEV-1F','엘리베이터 1층','ELEVATOR',  30, 13, 'WH', 1, now(), now()),
    -- 창고동 2층 (범위 밖 — 도크만 같은 코너로 옮긴다. P21 D10 그대로 AMR 담당)
    ('WH-DOCK-2F','2층 충전 베이', 'DOCK',       4, 21, 'WH', 2, now(), now()),
    ('WH-2F-P1',  '2층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 2, now(), now()),
    ('WH-2F-P2',  '2층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 2, now(), now()),
    ('WH-ELEV-2F','엘리베이터 2층','ELEVATOR',  30, 13, 'WH', 2, now(), now()),
    -- 창고동 3층 (범위 밖 — 도크만 같은 코너로)
    ('WH-DOCK-3F','3층 충전 베이', 'DOCK',       4, 21, 'WH', 3, now(), now()),
    ('WH-3F-P1',  '3층 피킹 A',    'WAREHOUSE', 17,  6, 'WH', 3, now(), now()),
    ('WH-3F-P2',  '3층 피킹 B',    'WAREHOUSE', 17, 13, 'WH', 3, now(), now()),
    ('WH-ELEV-3F','엘리베이터 3층','ELEVATOR',  30, 13, 'WH', 3, now(), now()),
    -- 교차점(JUNCTION) — V17과 동일
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
    -- P22: AMR ↔ AGV 게이트 — V17과 동일
    ('WH-GATE-U', '창고동 게이트 · 상단', 'GATE', 43,  9, 'PROD', 1, now(), now()),
    ('WH-GATE-L', '창고동 게이트 · 하단', 'GATE', 43, 18, 'PROD', 1, now(), now()),
    -- P22: 생산동 쪽 AMR 충전 베이 — 이번 요청 범위 밖, V17과 동일
    ('PROD-DOCK-1', '1번 충전 베이(AMR)', 'DOCK', 49,  3, 'PROD', 1, now(), now()),
    ('PROD-DOCK-2', '2번 충전 베이(AMR)', 'DOCK', 49,  5, 'PROD', 1, now(), now()),
    ('PROD-DOCK-3', '3번 충전 베이(AMR)', 'DOCK', 49, 21, 'PROD', 1, now(), now()),
    ('PROD-DOCK-4', '4번 충전 베이(AMR)', 'DOCK', 49, 23, 'PROD', 1, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장) — V17과 동일
    ('PROD-A1',   'A1 하역',       'STATION',   49,  6, 'PROD', 1, now(), now()),
    ('PROD-A2',   'A2 하역',       'STATION',   56,  6, 'PROD', 1, now(), now()),
    ('PROD-A3',   'A3 하역',       'STATION',   63,  6, 'PROD', 1, now(), now()),
    ('PROD-A4',   'A4 하역',       'STATION',   70,  6, 'PROD', 1, now(), now()),
    ('PROD-B1',   'B1 하역',       'STATION',   49, 21, 'PROD', 1, now(), now()),
    ('PROD-B2',   'B2 하역',       'STATION',   56, 21, 'PROD', 1, now(), now()),
    ('PROD-B3',   'B3 하역',       'STATION',   63, 21, 'PROD', 1, now(), now()),
    ('PROD-B4',   'B4 하역',       'STATION',   70, 21, 'PROD', 1, now(), now()),
    -- 품질동 — V17과 동일
    ('QC-IN',     '검사 입고',     'INSPECTION', 84, 21, 'QC', 1, now(), now()),
    ('QC-OUT',    '판정 출고',     'INSPECTION', 84,  6, 'QC', 1, now(), now()),
    -- 신관(V14) — V17과 동일
    ('GATE-WH-A', '신관 진입 게이트',    'GATE',      95, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-1',    '가공기 1(신관)',     'STATION',  105, 6, 'BLDG-A', 1, now(), now()),
    ('MACH-2',    '가공기 2(신관)',     'STATION',  115, 6, 'BLDG-A', 1, now(), now()),
    ('GATE-A-B',  '가공동-물류동 게이트', 'GATE',    122, 6, 'BLDG-B', 1, now(), now()),
    ('ASM-1',     '조립대 1(신관)',     'STATION',  132, 6, 'BLDG-B', 1, now(), now()),
    ('LOGI-1',    '물류 적재장(신관)',  'WAREHOUSE', 145, 6, 'BLDG-B', 1, now(), now());

-- ---- 엣지 ---- (연결로 내부·통로 가로 엣지는 V17과 동일 — 도크만 바뀐다)
-- width_mm: V18이 NOT NULL로 만들었다 — 통째로 지웠다 다시 넣는 마이그레이션은 이 컬럼도
-- 반드시 같이 채워야 한다(V18과 같은 값 2000mm, "실측 데이터 없음 → 임계값을 넉넉히
-- 웃도는 값" 원칙 그대로).
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('JCT-4-U',  'JCT-4-L',  9, true, 2000, now(), now()),
    ('JCT-9-U',  'JCT-9-L',  9, true, 2000, now(), now()),
    ('JCT-14-U', 'JCT-14-L', 9, true, 2000, now(), now()),
    ('JCT-27-U', 'JCT-27-L', 9, true, 2000, now(), now()),
    ('JCT-34-U', 'JCT-34-L', 9, true, 2000, now(), now()),
    ('JCT-41-U', 'JCT-41-L', 9, true, 2000, now(), now()),
    ('JCT-48-U', 'JCT-48-L', 9, true, 2000, now(), now()),
    ('JCT-62-U', 'JCT-62-L', 9, true, 2000, now(), now());

insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('JCT-4-U',  'JCT-9-U',  13, true, 2000, now(), now()),
    ('JCT-9-U',  'JCT-14-U', 13, true, 2000, now(), now()),
    ('JCT-14-U', 'WH-GATE-U', 13, true, 2000, now(), now()),
    ('WH-GATE-U', 'JCT-27-U',  6, true, 2000, now(), now()),
    ('JCT-27-U', 'JCT-34-U', 7,  true, 2000, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, 2000, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, 2000, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, 2000, now(), now()),
    ('JCT-4-L',  'JCT-9-L',  13, true, 2000, now(), now()),
    ('JCT-9-L',  'JCT-14-L', 13, true, 2000, now(), now()),
    ('JCT-14-L', 'WH-GATE-L', 13, true, 2000, now(), now()),
    ('WH-GATE-L', 'JCT-27-L',  6, true, 2000, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, 2000, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, 2000, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, 2000, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, 2000, now(), now());

-- 명명된 노드 → 교차점. **도크 4개가 전부 JCT-4-L로 바뀐 것이 이번 마이그레이션의 핵심**
-- (설계 문서 D1 표 — 비용은 |도크 y − 18|). 그 외는 V17과 동일.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('WH-DOCK-1',  'JCT-4-L',  1,   true, 2000, now(), now()),
    ('WH-DOCK-2',  'JCT-4-L',  2.5, true, 2000, now(), now()),
    ('WH-DOCK-3',  'JCT-4-L',  3,   true, 2000, now(), now()),
    ('WH-DOCK-4',  'JCT-4-L',  5,   true, 2000, now(), now()),
    ('WH-RECV',    'JCT-9-U',  3, true, 2000, now(), now()),
    ('WH-PICK',    'JCT-9-U',  4, true, 2000, now(), now()),
    ('WH-PICK',    'JCT-9-L',  5, true, 2000, now(), now()),
    ('WH-SHIP',    'JCT-14-L', 3, true, 2000, now(), now()),
    ('WH-ELEV-1F', 'JCT-14-U', 4, true, 2000, now(), now()),
    ('WH-ELEV-1F', 'JCT-14-L', 5, true, 2000, now(), now()),
    ('WH-DOCK-2F', 'JCT-4-L',  3, true, 2000, now(), now()),
    ('WH-2F-P1',   'JCT-9-U',  3, true, 2000, now(), now()),
    ('WH-2F-P2',   'JCT-9-U',  4, true, 2000, now(), now()),
    ('WH-2F-P2',   'JCT-9-L',  5, true, 2000, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-U', 4, true, 2000, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-L', 5, true, 2000, now(), now()),
    ('WH-DOCK-3F', 'JCT-4-L',  3, true, 2000, now(), now()),
    ('WH-3F-P1',   'JCT-9-U',  3, true, 2000, now(), now()),
    ('WH-3F-P2',   'JCT-9-U',  4, true, 2000, now(), now()),
    ('WH-3F-P2',   'JCT-9-L',  5, true, 2000, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-U', 4, true, 2000, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-L', 5, true, 2000, now(), now()),
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

insert into layout_edges (from_node, to_node, base_cost, bidirectional, width_mm, created_at, updated_at) values
    ('QC-OUT',     'GATE-WH-A', 11, true, 2000, now(), now()),
    ('GATE-WH-A',  'MACH-1',    10, true, 2000, now(), now()),
    ('MACH-1',     'MACH-2',    10, true, 2000, now(), now()),
    ('MACH-2',     'GATE-A-B',   7, true, 2000, now(), now()),
    ('GATE-A-B',   'ASM-1',     10, true, 2000, now(), now()),
    ('ASM-1',      'LOGI-1',    13, true, 2000, now(), now());

-- ---- 충전존 사각형 — 좌하단 코너 박스로 축소(D2) ----
-- 렉·엘리베이터·설비·POP 단말은 이번 마이그레이션에서 안 움직인다.
update layout_charging_zones set pos_x = 1.6, pos_y = 18.2, width = 4.8, height = 6.6, updated_at = now()
    where zone_code = 'CZ-1F';
update layout_charging_zones set pos_x = 1.6, pos_y = 19.0, width = 4.8, height = 4.5, updated_at = now()
    where zone_code = 'CZ-2F';
update layout_charging_zones set pos_x = 1.6, pos_y = 19.0, width = 4.8, height = 4.5, updated_at = now()
    where zone_code = 'CZ-3F';
