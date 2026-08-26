-- P28: 창고동 렉 밀도 증가(factory V19)에 맞춰 새 로케이션 27개를 추가한다.
--
-- factory가 베이마다 렉을 2열로 늘렸다(WH-{1,2,3}F-R10~R18, 설계 근거:
-- docs/p28-warehouse-rack-density-design.md D1). 좌표는 factory 소관이라 여기선 모른다 —
-- WMS는 코드로만 렉과 결합한다(P23 D1과 같은 원칙). node_code는 렉 코드 자신을 쓴다
-- (P23 V6 이후 관례 — fleet이 좌표로 피킹존을 계산한다, P21 D4).
insert into locations (location_code, name, node_code, max_pallet, created_at, updated_at) values
    ('WH-1F-R10', '1층 렉 10', 'WH-1F-R10', 20, now(), now()),
    ('WH-1F-R11', '1층 렉 11', 'WH-1F-R11', 20, now(), now()),
    ('WH-1F-R12', '1층 렉 12', 'WH-1F-R12', 20, now(), now()),
    ('WH-1F-R13', '1층 렉 13', 'WH-1F-R13', 20, now(), now()),
    ('WH-1F-R14', '1층 렉 14', 'WH-1F-R14', 20, now(), now()),
    ('WH-1F-R15', '1층 렉 15', 'WH-1F-R15', 20, now(), now()),
    ('WH-1F-R16', '1층 렉 16', 'WH-1F-R16', 20, now(), now()),
    ('WH-1F-R17', '1층 렉 17', 'WH-1F-R17', 20, now(), now()),
    ('WH-1F-R18', '1층 렉 18', 'WH-1F-R18', 20, now(), now()),
    ('WH-2F-R10', '2층 렉 10', 'WH-2F-R10', 12, now(), now()),
    ('WH-2F-R11', '2층 렉 11', 'WH-2F-R11', 12, now(), now()),
    ('WH-2F-R12', '2층 렉 12', 'WH-2F-R12', 12, now(), now()),
    ('WH-2F-R13', '2층 렉 13', 'WH-2F-R13', 12, now(), now()),
    ('WH-2F-R14', '2층 렉 14', 'WH-2F-R14', 12, now(), now()),
    ('WH-2F-R15', '2층 렉 15', 'WH-2F-R15', 12, now(), now()),
    ('WH-2F-R16', '2층 렉 16', 'WH-2F-R16', 12, now(), now()),
    ('WH-2F-R17', '2층 렉 17', 'WH-2F-R17', 12, now(), now()),
    ('WH-2F-R18', '2층 렉 18', 'WH-2F-R18', 12, now(), now()),
    ('WH-3F-R10', '3층 렉 10', 'WH-3F-R10', 12, now(), now()),
    ('WH-3F-R11', '3층 렉 11', 'WH-3F-R11', 12, now(), now()),
    ('WH-3F-R12', '3층 렉 12', 'WH-3F-R12', 12, now(), now()),
    ('WH-3F-R13', '3층 렉 13', 'WH-3F-R13', 12, now(), now()),
    ('WH-3F-R14', '3층 렉 14', 'WH-3F-R14', 12, now(), now()),
    ('WH-3F-R15', '3층 렉 15', 'WH-3F-R15', 12, now(), now()),
    ('WH-3F-R16', '3층 렉 16', 'WH-3F-R16', 12, now(), now()),
    ('WH-3F-R17', '3층 렉 17', 'WH-3F-R17', 12, now(), now()),
    ('WH-3F-R18', '3층 렉 18', 'WH-3F-R18', 12, now(), now());

-- ---- 시드 파렛트 ---- 렉 윤곽만 생기고 전부 비어 있으면 "빈 공간이 많다"는 불만이
-- 그대로다 — 골고루 채워 둔다(V3 시드와 같은 원칙, 품목 3종을 돌려가며 적재율을 다양하게).
-- 파렛트 코드는 PalletCodeGenerator(시퀀스 기반, "PLT-########")와 겹치지 않게
-- 별도 접두어를 쓴다(V7 백필의 "PLT-LEGACY-"와 같은 이유).
insert into pallets (plt_code, location_id, status, created_at, updated_at)
select 'PLT-SEED-' || v.location_code, l.id, 'LOADED', now(), now()
from (values
    ('WH-1F-R10', 'ITEM-1001', 150), ('WH-1F-R11', 'ITEM-1002',  80), ('WH-1F-R12', 'ITEM-1003', 190),
    ('WH-1F-R13', 'ITEM-1001',  60), ('WH-1F-R14', 'ITEM-1002', 200), ('WH-1F-R15', 'ITEM-1003',  30),
    ('WH-1F-R16', 'ITEM-1001', 110), ('WH-1F-R17', 'ITEM-1002', 170), ('WH-1F-R18', 'ITEM-1003',  50),
    ('WH-2F-R10', 'ITEM-1001',  90), ('WH-2F-R11', 'ITEM-1002',  40), ('WH-2F-R12', 'ITEM-1003', 120),
    ('WH-2F-R13', 'ITEM-1001',  70), ('WH-2F-R14', 'ITEM-1002', 100), ('WH-2F-R15', 'ITEM-1003',  20),
    ('WH-2F-R16', 'ITEM-1001',  60), ('WH-2F-R17', 'ITEM-1002', 110), ('WH-2F-R18', 'ITEM-1003',  35),
    ('WH-3F-R10', 'ITEM-1001', 200), ('WH-3F-R11', 'ITEM-1002',  90), ('WH-3F-R12', 'ITEM-1003', 150),
    ('WH-3F-R13', 'ITEM-1001',  60), ('WH-3F-R14', 'ITEM-1002', 220), ('WH-3F-R15', 'ITEM-1003', 100),
    ('WH-3F-R16', 'ITEM-1001', 180), ('WH-3F-R17', 'ITEM-1002',  40), ('WH-3F-R18', 'ITEM-1003', 210)
) as v(location_code, item_code, qty)
join locations l on l.location_code = v.location_code;

insert into stocks (pallet_id, item_id, quantity, lot_no, inbound_dt, created_at, updated_at)
select p.id, i.id, v.qty, p.plt_code, now(), now(), now()
from (values
    ('WH-1F-R10', 'ITEM-1001', 150), ('WH-1F-R11', 'ITEM-1002',  80), ('WH-1F-R12', 'ITEM-1003', 190),
    ('WH-1F-R13', 'ITEM-1001',  60), ('WH-1F-R14', 'ITEM-1002', 200), ('WH-1F-R15', 'ITEM-1003',  30),
    ('WH-1F-R16', 'ITEM-1001', 110), ('WH-1F-R17', 'ITEM-1002', 170), ('WH-1F-R18', 'ITEM-1003',  50),
    ('WH-2F-R10', 'ITEM-1001',  90), ('WH-2F-R11', 'ITEM-1002',  40), ('WH-2F-R12', 'ITEM-1003', 120),
    ('WH-2F-R13', 'ITEM-1001',  70), ('WH-2F-R14', 'ITEM-1002', 100), ('WH-2F-R15', 'ITEM-1003',  20),
    ('WH-2F-R16', 'ITEM-1001',  60), ('WH-2F-R17', 'ITEM-1002', 110), ('WH-2F-R18', 'ITEM-1003',  35),
    ('WH-3F-R10', 'ITEM-1001', 200), ('WH-3F-R11', 'ITEM-1002',  90), ('WH-3F-R12', 'ITEM-1003', 150),
    ('WH-3F-R13', 'ITEM-1001',  60), ('WH-3F-R14', 'ITEM-1002', 220), ('WH-3F-R15', 'ITEM-1003', 100),
    ('WH-3F-R16', 'ITEM-1001', 180), ('WH-3F-R17', 'ITEM-1002',  40), ('WH-3F-R18', 'ITEM-1003', 210)
) as v(location_code, item_code, qty)
join pallets p on p.plt_code = 'PLT-SEED-' || v.location_code
join items i on i.item_code = v.item_code;
