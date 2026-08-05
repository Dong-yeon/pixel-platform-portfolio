-- P20-1: 레이아웃을 노드-엣지 그래프로 만든다.
-- 설계 근거: docs/p20-layout-routing-design.md (D1~D8), docs/BACKLOG.md P20.
--
-- **이 마이그레이션이 하는 일과 안 하는 일.** LaneGraph(fleet)의 컴파일타임 상수
-- (CONNECTOR_X, AISLE_UPPER_Y/LOWER_Y)가 암묵적으로 표현하던 통로·연결로 토폴로지를
-- 그대로 데이터로 옮긴다 — **값은 하나도 안 바뀐다, 표현만 코드에서 데이터로 이동한다.**
-- LaneGraph 자체를 이 그래프를 읽는 알고리즘으로 바꾸는 건 P20-2다. 여기서는 데이터만 만든다.
--
-- **교차점(JUNCTION)을 왜 새로 만드는가.** 지금 LaneGraph는 "통로와 연결로가 만나는 자리"를
-- 좌표 계산으로만 다루고 노드로 두지 않는다. 노드-엣지 그래프로 표현하려면 그 교차점도 실제
-- 노드 행이 있어야 A*가 지나갈 자리가 생긴다. 그래서 연결로 8개 × 통로 2개 = 16개의 JUNCTION
-- 노드를 새로 넣는다. 로봇이 여기 정차하지 않는다 — 순수하게 경로 그래프의 분기점이다.
--
-- **비용 계산 검증.** 예를 들어 WH-DOCK-1(4,3) → PROD-A1(27,6)의 기존 LaneGraph.plan() 비용은
-- addVertical(4,3,9)=6 + addAisle(9,4→27)=23 + addVertical(27,9,6)=3 = 32.
-- 이 마이그레이션의 그래프로도 WH-DOCK-1→JCT-4-U(6) → JCT-4-U→JCT-9-U(5)→JCT-9-U→JCT-14-U(5)
-- →JCT-14-U→JCT-27-U(13) → JCT-27-U→PROD-A1(3) = 6+5+5+13+3 = 32로 **일치한다.**
--
-- **층은 아직 안 가른다(알려진 한계).** LaneGraph는 오늘도 층을 모른다 — 창고동 2·3층 노드가
-- 1층과 같은 (x,y)를 재사용하므로, 이 그래프도 층 구분 없이 같은 좌표는 같은 교차점을 공유한다.
-- 실제로는 배차가 `robot.floorNo() == order.floorNo()`로 층을 먼저 거르기 때문에 다른 층
-- 로봇끼리 이 그래프에서 마주칠 일이 없어 지금은 문제가 안 되지만, 층별 통로가 생기면
-- (P20-3/P20-4에서 검토) 교차점에도 floor_no를 실질적으로 나눠야 한다.

alter table layout_settings add column layout_version integer not null default 1;
alter table layout_settings add column effective_from timestamp not null default now();
-- 주의: 이 마이그레이션은 평면도 자체(좌표)를 바꾸지 않으므로 버전을 올리지 않는다.
-- 버전은 "물리적 배치가 바뀌는" 마이그레이션(예: P20-3의 Building 추가)에서 올린다.

create table layout_edges (
    id            bigserial primary key,
    from_node     varchar(30) not null references layout_nodes (node_code),
    to_node       varchar(30) not null references layout_nodes (node_code),
    base_cost     double precision not null,
    bidirectional boolean not null default true,
    created_at    timestamp not null,
    updated_at    timestamp not null,
    constraint uq_layout_edge unique (from_node, to_node)
);

-- ---- 교차점 노드 (연결로 8개 × 통로 2개) ----
-- building_code는 LaneGraph.java 주석의 건물별 연결로 분류를 그대로 따른다(4,9,14=창고동 /
-- 27,34,41,48=생산동 / 62=품질동). floor_no=1은 임의값이다 — 위 "알려진 한계" 참고.
insert into layout_nodes (node_code, name, node_type, pos_x, pos_y, building_code, floor_no, created_at, updated_at) values
    ('JCT-4-U',  '연결로 4 · 상단 통로 교차점',  'JUNCTION', 4,  9, 'WH',   1, now(), now()),
    ('JCT-4-L',  '연결로 4 · 하단 통로 교차점',  'JUNCTION', 4, 18, 'WH',   1, now(), now()),
    ('JCT-9-U',  '연결로 9 · 상단 통로 교차점',  'JUNCTION', 9,  9, 'WH',   1, now(), now()),
    ('JCT-9-L',  '연결로 9 · 하단 통로 교차점',  'JUNCTION', 9, 18, 'WH',   1, now(), now()),
    ('JCT-14-U', '연결로 14 · 상단 통로 교차점', 'JUNCTION', 14,  9, 'WH',   1, now(), now()),
    ('JCT-14-L', '연결로 14 · 하단 통로 교차점', 'JUNCTION', 14, 18, 'WH',   1, now(), now()),
    ('JCT-27-U', '연결로 27 · 상단 통로 교차점', 'JUNCTION', 27,  9, 'PROD', 1, now(), now()),
    ('JCT-27-L', '연결로 27 · 하단 통로 교차점', 'JUNCTION', 27, 18, 'PROD', 1, now(), now()),
    ('JCT-34-U', '연결로 34 · 상단 통로 교차점', 'JUNCTION', 34,  9, 'PROD', 1, now(), now()),
    ('JCT-34-L', '연결로 34 · 하단 통로 교차점', 'JUNCTION', 34, 18, 'PROD', 1, now(), now()),
    ('JCT-41-U', '연결로 41 · 상단 통로 교차점', 'JUNCTION', 41,  9, 'PROD', 1, now(), now()),
    ('JCT-41-L', '연결로 41 · 하단 통로 교차점', 'JUNCTION', 41, 18, 'PROD', 1, now(), now()),
    ('JCT-48-U', '연결로 48 · 상단 통로 교차점', 'JUNCTION', 48,  9, 'PROD', 1, now(), now()),
    ('JCT-48-L', '연결로 48 · 하단 통로 교차점', 'JUNCTION', 48, 18, 'PROD', 1, now(), now()),
    ('JCT-62-U', '연결로 62 · 상단 통로 교차점', 'JUNCTION', 62,  9, 'QC',   1, now(), now()),
    ('JCT-62-L', '연결로 62 · 하단 통로 교차점', 'JUNCTION', 62, 18, 'QC',   1, now(), now());

-- ---- 연결로 내부 수직 엣지 (상단 교차점 ↔ 하단 교차점, 통로 사이 구간) ----
-- 비용 9 = 하단통로(18) - 상단통로(9). LaneGraph.addVertical의 "mid" 밴드에 해당.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('JCT-4-U',  'JCT-4-L',  9, true, now(), now()),
    ('JCT-9-U',  'JCT-9-L',  9, true, now(), now()),
    ('JCT-14-U', 'JCT-14-L', 9, true, now(), now()),
    ('JCT-27-U', 'JCT-27-L', 9, true, now(), now()),
    ('JCT-34-U', 'JCT-34-L', 9, true, now(), now()),
    ('JCT-41-U', 'JCT-41-L', 9, true, now(), now()),
    ('JCT-48-U', 'JCT-48-L', 9, true, now(), now()),
    ('JCT-62-U', 'JCT-62-L', 9, true, now(), now());

-- ---- 통로(가로) 엣지 — 인접 연결로 교차점끼리, 상단/하단 각각 ----
-- 비용 = 두 연결로 x의 차. LaneGraph.CONNECTOR_X = {4,9,14,27,34,41,48,62}의 인접 쌍.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    ('JCT-4-U',  'JCT-9-U',  5,  true, now(), now()),
    ('JCT-9-U',  'JCT-14-U', 5,  true, now(), now()),
    ('JCT-14-U', 'JCT-27-U', 13, true, now(), now()),
    ('JCT-27-U', 'JCT-34-U', 7,  true, now(), now()),
    ('JCT-34-U', 'JCT-41-U', 7,  true, now(), now()),
    ('JCT-41-U', 'JCT-48-U', 7,  true, now(), now()),
    ('JCT-48-U', 'JCT-62-U', 14, true, now(), now()),
    ('JCT-4-L',  'JCT-9-L',  5,  true, now(), now()),
    ('JCT-9-L',  'JCT-14-L', 5,  true, now(), now()),
    ('JCT-14-L', 'JCT-27-L', 13, true, now(), now()),
    ('JCT-27-L', 'JCT-34-L', 7,  true, now(), now()),
    ('JCT-34-L', 'JCT-41-L', 7,  true, now(), now()),
    ('JCT-41-L', 'JCT-48-L', 7,  true, now(), now()),
    ('JCT-48-L', 'JCT-62-L', 14, true, now(), now());

-- ---- 명명된 노드 → 교차점 수직 엣지 ----
-- 규칙(LaneGraph.addVertical과 동일): y<9면 상단 교차점 하나, y>18이면 하단 교차점 하나,
-- 9<=y<=18(mid 밴드)이면 양쪽 교차점 모두에 엣지 — 그 노드가 어느 통로로도 나갈 수 있으므로.
insert into layout_edges (from_node, to_node, base_cost, bidirectional, created_at, updated_at) values
    -- 창고동 1층
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
    -- 창고동 2층 (같은 (x,y) 재사용 — 마이그레이션 헤더 "알려진 한계" 참고)
    ('WH-DOCK-2F', 'JCT-4-U',  6, true, now(), now()),
    ('WH-2F-P1',   'JCT-9-U',  3, true, now(), now()),
    ('WH-2F-P2',   'JCT-9-U',  4, true, now(), now()),
    ('WH-2F-P2',   'JCT-9-L',  5, true, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-2F', 'JCT-14-L', 5, true, now(), now()),
    -- 창고동 3층
    ('WH-DOCK-3F', 'JCT-4-U',  6, true, now(), now()),
    ('WH-3F-P1',   'JCT-9-U',  3, true, now(), now()),
    ('WH-3F-P2',   'JCT-9-U',  4, true, now(), now()),
    ('WH-3F-P2',   'JCT-9-L',  5, true, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-U', 4, true, now(), now()),
    ('WH-ELEV-3F', 'JCT-14-L', 5, true, now(), now()),
    -- 생산동 A열(가공) / B열(조립·검사·포장)
    ('PROD-A1', 'JCT-27-U', 3, true, now(), now()),
    ('PROD-A2', 'JCT-34-U', 3, true, now(), now()),
    ('PROD-A3', 'JCT-41-U', 3, true, now(), now()),
    ('PROD-A4', 'JCT-48-U', 3, true, now(), now()),
    ('PROD-B1', 'JCT-27-L', 3, true, now(), now()),
    ('PROD-B2', 'JCT-34-L', 3, true, now(), now()),
    ('PROD-B3', 'JCT-41-L', 3, true, now(), now()),
    ('PROD-B4', 'JCT-48-L', 3, true, now(), now()),
    -- 품질동
    ('QC-OUT', 'JCT-62-U', 3, true, now(), now()),
    ('QC-IN',  'JCT-62-L', 3, true, now(), now());
