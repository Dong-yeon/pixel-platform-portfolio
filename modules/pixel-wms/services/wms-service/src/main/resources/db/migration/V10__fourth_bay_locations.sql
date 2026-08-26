-- P30: 창고동 4번째 베이(factory V21) 증설에 맞춰 새 로케이션 18개를 추가한다.
--
-- factory가 4번째 베이를 신설했다(WH-{1,2,3}F-R19~R24, 설계 근거:
-- docs/p30-warehouse-fourth-bay-design.md D4). 좌표는 factory 소관이라 여기선 모른다 —
-- WMS는 코드로만 렉과 결합한다(P23 D1과 같은 원칙). node_code는 렉 코드 자신을 쓴다
-- (P23 V6 이후 관례).
insert into locations (location_code, name, node_code, max_pallet, created_at, updated_at) values
    ('WH-1F-R19', '1층 렉 19', 'WH-1F-R19', 20, now(), now()),
    ('WH-1F-R20', '1층 렉 20', 'WH-1F-R20', 20, now(), now()),
    ('WH-1F-R21', '1층 렉 21', 'WH-1F-R21', 20, now(), now()),
    ('WH-1F-R22', '1층 렉 22', 'WH-1F-R22', 20, now(), now()),
    ('WH-1F-R23', '1층 렉 23', 'WH-1F-R23', 20, now(), now()),
    ('WH-1F-R24', '1층 렉 24', 'WH-1F-R24', 20, now(), now()),
    ('WH-2F-R19', '2층 렉 19', 'WH-2F-R19', 12, now(), now()),
    ('WH-2F-R20', '2층 렉 20', 'WH-2F-R20', 12, now(), now()),
    ('WH-2F-R21', '2층 렉 21', 'WH-2F-R21', 12, now(), now()),
    ('WH-2F-R22', '2층 렉 22', 'WH-2F-R22', 12, now(), now()),
    ('WH-2F-R23', '2층 렉 23', 'WH-2F-R23', 12, now(), now()),
    ('WH-2F-R24', '2층 렉 24', 'WH-2F-R24', 12, now(), now()),
    ('WH-3F-R19', '3층 렉 19', 'WH-3F-R19', 12, now(), now()),
    ('WH-3F-R20', '3층 렉 20', 'WH-3F-R20', 12, now(), now()),
    ('WH-3F-R21', '3층 렉 21', 'WH-3F-R21', 12, now(), now()),
    ('WH-3F-R22', '3층 렉 22', 'WH-3F-R22', 12, now(), now()),
    ('WH-3F-R23', '3층 렉 23', 'WH-3F-R23', 12, now(), now()),
    ('WH-3F-R24', '3층 렉 24', 'WH-3F-R24', 12, now(), now());

-- ---- 시드 파렛트 ---- V9와 같은 원칙 — 렉 윤곽만 생기고 비어 있으면 "빈 공간이 많다"는
-- 불만이 그대로다. 품목 3종을 돌려가며 적재율을 다양하게.
insert into pallets (plt_code, location_id, status, created_at, updated_at)
select 'PLT-SEED-' || v.location_code, l.id, 'LOADED', now(), now()
from (values
    ('WH-1F-R19', 'ITEM-1001', 140), ('WH-1F-R20', 'ITEM-1002',  70), ('WH-1F-R21', 'ITEM-1003', 180),
    ('WH-1F-R22', 'ITEM-1001',  90), ('WH-1F-R23', 'ITEM-1002', 160), ('WH-1F-R24', 'ITEM-1003',  40),
    ('WH-2F-R19', 'ITEM-1001',  80), ('WH-2F-R20', 'ITEM-1002',  50), ('WH-2F-R21', 'ITEM-1003', 100),
    ('WH-2F-R22', 'ITEM-1001',  60), ('WH-2F-R23', 'ITEM-1002',  90), ('WH-2F-R24', 'ITEM-1003',  30),
    ('WH-3F-R19', 'ITEM-1001', 170), ('WH-3F-R20', 'ITEM-1002', 110), ('WH-3F-R21', 'ITEM-1003', 200),
    ('WH-3F-R22', 'ITEM-1001',  80), ('WH-3F-R23', 'ITEM-1002', 190), ('WH-3F-R24', 'ITEM-1003', 120)
) as v(location_code, item_code, qty)
join locations l on l.location_code = v.location_code;

insert into stocks (pallet_id, item_id, quantity, lot_no, inbound_dt, created_at, updated_at)
select p.id, i.id, v.qty, p.plt_code, now(), now(), now()
from (values
    ('WH-1F-R19', 'ITEM-1001', 140), ('WH-1F-R20', 'ITEM-1002',  70), ('WH-1F-R21', 'ITEM-1003', 180),
    ('WH-1F-R22', 'ITEM-1001',  90), ('WH-1F-R23', 'ITEM-1002', 160), ('WH-1F-R24', 'ITEM-1003',  40),
    ('WH-2F-R19', 'ITEM-1001',  80), ('WH-2F-R20', 'ITEM-1002',  50), ('WH-2F-R21', 'ITEM-1003', 100),
    ('WH-2F-R22', 'ITEM-1001',  60), ('WH-2F-R23', 'ITEM-1002',  90), ('WH-2F-R24', 'ITEM-1003',  30),
    ('WH-3F-R19', 'ITEM-1001', 170), ('WH-3F-R20', 'ITEM-1002', 110), ('WH-3F-R21', 'ITEM-1003', 200),
    ('WH-3F-R22', 'ITEM-1001',  80), ('WH-3F-R23', 'ITEM-1002', 190), ('WH-3F-R24', 'ITEM-1003', 120)
) as v(location_code, item_code, qty)
join pallets p on p.plt_code = 'PLT-SEED-' || v.location_code
join items i on i.item_code = v.item_code;
