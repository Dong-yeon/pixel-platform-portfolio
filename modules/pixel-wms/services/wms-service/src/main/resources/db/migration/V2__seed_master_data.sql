-- 마스터 시드. 품번은 factory 작업지시가 쓰는 가공 품목과 같은 세계를 가정한다.
--
-- 표준CT는 factory 설비의 ideal_cycle_time_ms(가공 30000 / 조립 25000 / 검사 20000 / 포장 15000)와
-- 같은 눈금으로 맞춰 둔다 — 나중에 factory가 이 값을 주입받아도 지표가 튀지 않게 하기 위해서다.

insert into items (item_code, name, unit, created_at, updated_at) values
    ('ITEM-1001', '구동축 샤프트',   'EA', now(), now()),
    ('ITEM-1002', '허브 베어링',     'EA', now(), now()),
    ('ITEM-1003', '기어 하우징',     'EA', now(), now());

insert into item_standard_cycle_times (item_id, process_code, standard_cycle_time_ms, created_at, updated_at) values
    ((select id from items where item_code = 'ITEM-1001'), 'MACHINING', 30000, now(), now()),
    ((select id from items where item_code = 'ITEM-1001'), 'ASSEMBLY',  25000, now(), now()),
    ((select id from items where item_code = 'ITEM-1002'), 'MACHINING', 28000, now(), now()),
    ((select id from items where item_code = 'ITEM-1002'), 'INSPECTION', 20000, now(), now()),
    ((select id from items where item_code = 'ITEM-1003'), 'ASSEMBLY',  25000, now(), now()),
    ((select id from items where item_code = 'ITEM-1003'), 'PACKAGING', 15000, now(), now());

-- 로케이션의 node_code 는 factory 평면도 노드와 일치해야 AMR이 제자리로 간다.
insert into locations (location_code, name, node_code, created_at, updated_at) values
    ('WH-A',  '자재 창고 A',  'WAREHOUSE', now(), now()),
    ('SHIP',  '출하장',       'SHIPPING',  now(), now());

-- 초기 재고 — 창고에만 쌓아 둔다(출하장은 0에서 시작).
insert into stocks (location_id, item_id, quantity, created_at, updated_at) values
    ((select id from locations where location_code = 'WH-A'), (select id from items where item_code = 'ITEM-1001'), 500, now(), now()),
    ((select id from locations where location_code = 'WH-A'), (select id from items where item_code = 'ITEM-1002'), 300, now(), now()),
    ((select id from locations where location_code = 'WH-A'), (select id from items where item_code = 'ITEM-1003'), 200, now(), now());
