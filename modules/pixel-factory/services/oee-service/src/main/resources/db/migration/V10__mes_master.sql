-- MES 기준정보 — 차종 · 품번 · BOM(개정).
--
-- **왜 필요한가.** 지금까지 이 공장은 "무엇을 만드는지"를 몰랐다. `work_orders.item_id`와
-- `process_id`는 가리키는 테이블이 없는 맨 bigint였고, 시더가 `1L`을 박아 넣었다
-- ("품목 마스터는 아직 없다(BACKLOG)"). 화면에도 지시번호만 떴다.
-- 차종 → 품번 → BOM을 세우고 작업지시가 **실제 품번**을 참조하게 한다.
--
-- **WMS `items`와의 관계.** WMS의 items는 *재고 단위 품목*, 여기 parts는 *생산 기준 품번*이다.
-- 같은 세계를 가리키므로 FK가 아니라 **코드로 정합**한다(`parts.part_code == wms.items.item_code`).
-- 이 리포가 이미 쓰는 방식이다(layout_racks.rack_code ↔ wms.locations.location_code).

-- ---- 차종 ----
create table vehicle_models (
    id            bigserial primary key,
    model_code    varchar(30) not null unique,
    name          varchar(50) not null,
    in_production boolean not null default true,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

insert into vehicle_models (model_code, name, in_production, created_at, updated_at) values
    ('SUV-A', '준중형 SUV A', true, now(), now()),
    ('SDN-B', '중형 세단 B',  true, now(), now());

-- ---- 품번 ----
-- part_type: PRODUCT(완제품 어셈블리) / SEMI(가공 반제품) / MATERIAL(원자재)
-- model_id 가 null 이면 차종 공용 부품이다.
--
-- **part_code 에 unique 를 건다.** 마스터가 1:1이 아니면 BOM 트리를 조립할 때 노드가 곱해져
-- 화면에 같은 자재가 여러 번 뜬다(실 운영 MES에서 겪은 사고). 원천에서 막는다.
create table parts (
    id         bigserial primary key,
    part_code  varchar(30) not null unique,
    name       varchar(100) not null,
    part_type  varchar(20) not null,
    unit       varchar(10) not null default 'EA',
    model_id   bigint references vehicle_models (id),
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into parts (part_code, name, part_type, unit, model_id, created_at, updated_at) values
    -- 완제품 어셈블리 (차종별)
    ('ASSY-2001', '전륜 허브 어셈블리', 'PRODUCT', 'EA', (select id from vehicle_models where model_code='SUV-A'), now(), now()),
    ('ASSY-2002', '후륜 허브 어셈블리', 'PRODUCT', 'EA', (select id from vehicle_models where model_code='SUV-A'), now(), now()),
    ('ASSY-2101', '조향 기어 어셈블리', 'PRODUCT', 'EA', (select id from vehicle_models where model_code='SDN-B'), now(), now()),
    -- 가공 반제품 — ITEM-* 는 WMS 품목 코드와 일치시킨다
    ('ITEM-1001', '구동축 샤프트',     'SEMI', 'EA', null, now(), now()),
    ('ITEM-1002', '허브 베어링',       'SEMI', 'EA', null, now(), now()),
    ('ITEM-1003', '기어 하우징',       'SEMI', 'EA', null, now(), now()),
    ('SEMI-1101', '너클 하우징',       'SEMI', 'EA', null, now(), now()),
    ('SEMI-1102', '피니언 샤프트',     'SEMI', 'EA', null, now(), now()),
    -- 원자재
    ('MAT-3001', '환봉 SCM440',        'MATERIAL', 'KG', null, now(), now()),
    ('MAT-3002', '베어링 볼세트',      'MATERIAL', 'SET', null, now(), now()),
    ('MAT-3003', '알루미늄 잉곳',      'MATERIAL', 'KG', null, now(), now()),
    ('MAT-3004', '시일 키트',          'MATERIAL', 'SET', null, now(), now()),
    ('MAT-3005', '볼트 세트',          'MATERIAL', 'SET', null, now(), now());

-- ---- BOM ----
-- 개정(rev)은 행을 고치지 않고 **새 rev로 쌓는다**(개정이력 방식). 최신은 latest_yn 으로 가른다.
--
-- **개정 시 대상 rev 는 반드시 DB MAX(rev_no)+1 로 뽑아야 한다.** 클라이언트가 준 rev+1로
-- 계산하면 최신이 아닌 rev 에서 개정할 때 기존 rev 와 충돌해 같은 rev 에 트리가 통째로
-- 중복 적재된다(실 운영 MES 사고). 서비스(BomService)가 그 규칙을 지킨다.
--
-- qty_per 는 numeric 이므로 엔티티도 BigDecimal 이어야 한다 — 타입이 어긋나면 컴파일이 아니라
-- `ddl-auto: validate` 가 기동을 막는다(이 리포에서 좌표 numeric/Double 로 이미 겪었다).
create table boms (
    id             bigserial primary key,
    parent_part_id bigint not null references parts (id),
    child_part_id  bigint not null references parts (id),
    rev_no         integer not null,
    seq            smallint not null,
    qty_per        numeric(10,3) not null,
    latest_yn      char(1) not null default 'Y',
    created_at     timestamp not null,
    updated_at     timestamp not null,
    constraint uq_bom unique (parent_part_id, child_part_id, rev_no),
    constraint ck_bom_no_self check (parent_part_id <> child_part_id)
);

create index idx_boms_parent_rev on boms (parent_part_id, rev_no);

insert into boms (parent_part_id, child_part_id, rev_no, seq, qty_per, latest_yn, created_at, updated_at)
select p.id, c.id, 1, v.seq, v.qty, 'Y', now(), now()
from (values
    -- 1단계: 완제품 ← 반제품·자재
    ('ASSY-2001', 'ITEM-1001', 1, 1.000),
    ('ASSY-2001', 'ITEM-1002', 2, 2.000),
    ('ASSY-2001', 'SEMI-1101', 3, 1.000),
    ('ASSY-2001', 'MAT-3004',  4, 1.000),
    ('ASSY-2002', 'ITEM-1002', 1, 2.000),
    ('ASSY-2002', 'SEMI-1101', 2, 1.000),
    ('ASSY-2002', 'MAT-3005',  3, 4.000),
    ('ASSY-2101', 'ITEM-1003', 1, 1.000),
    ('ASSY-2101', 'SEMI-1102', 2, 1.000),
    ('ASSY-2101', 'MAT-3004',  3, 1.000),
    -- 2단계: 반제품 ← 원자재
    ('ITEM-1001', 'MAT-3001',  1, 2.400),
    ('ITEM-1002', 'MAT-3002',  1, 1.000),
    ('ITEM-1003', 'MAT-3003',  1, 3.100),
    ('SEMI-1101', 'MAT-3001',  1, 1.800),
    ('SEMI-1102', 'MAT-3001',  1, 1.200)
) as v(parent_code, child_code, seq, qty)
join parts p on p.part_code = v.parent_code
join parts c on c.part_code = v.child_code;

-- ---- 작업지시를 품번에 붙인다 ----
-- 컬럼 이름도 바꾼다: item_id 는 아무것도 가리키지 않던 이름이었다.
alter table work_orders rename column item_id to part_id;

-- 설비마다 무엇을 만드는지 정해 준다(가공 라인은 반제품, 조립 라인은 완제품).
update work_orders wo
set part_id = p.id
from equipments e
join (values
    ('CNC-01', 'ITEM-1001'),
    ('CNC-02', 'ITEM-1002'),
    ('CNC-03', 'SEMI-1101'),
    ('MCT-01', 'ITEM-1003'),
    ('ASM-01', 'ASSY-2001'),
    ('ASM-02', 'ASSY-2002'),
    ('INS-01', 'ASSY-2101'),
    ('PKG-01', 'ASSY-2001')
) as v(equipment_code, part_code) on e.equipment_code = v.equipment_code
join parts p on p.part_code = v.part_code
where wo.equipment_id = e.id;

-- 설비 매핑에서 빠진 잔여 행(수동 생성분 등)은 대표 품번으로 — FK를 걸기 전에 정리한다.
update work_orders
set part_id = (select id from parts where part_code = 'ITEM-1001')
where part_id not in (select id from parts);

alter table work_orders
    add constraint fk_work_orders_part foreign key (part_id) references parts (id);
