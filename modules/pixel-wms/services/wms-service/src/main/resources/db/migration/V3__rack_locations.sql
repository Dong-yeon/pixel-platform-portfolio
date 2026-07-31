-- 렉 1기 = 로케이션 1개. 평면도 2.0(factory V9)의 렉과 코드로 짝을 맞춘다.
--
-- **왜 코드로만 잇는가.** 렉의 물리 속성(위치·열·단·용량)은 평면도라 factory가 갖고, 지금
-- 몇 개가 있는지는 재고라 WMS가 갖는다. 모듈 DB가 다르므로 FK가 아니라
-- `layout_racks.rack_code == locations.location_code` 로 잇고, 적재율은 소비 측(대시보드)이
-- 두 값을 맞춰 계산한다.
--
-- **노드 코드도 바뀌었다**(V9에서 건물 체계로 재명명): WAREHOUSE → WH-PICK, SHIPPING → WH-SHIP.
-- 이 값이 어긋나면 AMR이 엉뚱한 좌표로 간다 — fleet은 모르는 노드도 거부하지 않고
-- 해시 좌표로 "resolve" 해 버리기 때문에 조용히 틀어진다.

-- ---- 기존 로케이션의 노드 코드 갱신 ----
-- WH-A는 지우지 않는다 — 지난 출고지시(outbound_orders.from_location_id)가 참조한다.
update locations set node_code = 'WH-PICK'  where location_code = 'WH-A';
update locations set node_code = 'WH-SHIP', name = '출하장' where location_code = 'SHIP';

-- ---- 렉 로케이션 ----
-- 2·3층 렉의 노드 코드도 지상 피킹존(WH-PICK)이다. 층간 리프트는 모델링하지 않았고,
-- AMR은 지상만 다닌다 — 위층 물건은 리프트로 내려온 뒤 피킹존에서 실린다고 본다.
insert into locations (location_code, name, node_code, created_at, updated_at) values
    ('WH-1F-R01', '1층 렉 01', 'WH-PICK', now(), now()),
    ('WH-1F-R02', '1층 렉 02', 'WH-PICK', now(), now()),
    ('WH-1F-R03', '1층 렉 03', 'WH-PICK', now(), now()),
    ('WH-1F-R04', '1층 렉 04', 'WH-PICK', now(), now()),
    ('WH-1F-R05', '1층 렉 05', 'WH-PICK', now(), now()),
    ('WH-1F-R06', '1층 렉 06', 'WH-PICK', now(), now()),
    ('WH-1F-R07', '1층 렉 07', 'WH-PICK', now(), now()),
    ('WH-1F-R08', '1층 렉 08', 'WH-PICK', now(), now()),
    ('WH-1F-R09', '1층 렉 09', 'WH-PICK', now(), now()),
    ('WH-1F-R10', '1층 렉 10', 'WH-PICK', now(), now()),
    ('WH-1F-R11', '1층 렉 11', 'WH-PICK', now(), now()),
    ('WH-1F-R12', '1층 렉 12', 'WH-PICK', now(), now()),
    ('WH-2F-R01', '2층 렉 01', 'WH-PICK', now(), now()),
    ('WH-2F-R02', '2층 렉 02', 'WH-PICK', now(), now()),
    ('WH-2F-R03', '2층 렉 03', 'WH-PICK', now(), now()),
    ('WH-2F-R04', '2층 렉 04', 'WH-PICK', now(), now()),
    ('WH-2F-R05', '2층 렉 05', 'WH-PICK', now(), now()),
    ('WH-2F-R06', '2층 렉 06', 'WH-PICK', now(), now()),
    ('WH-2F-R07', '2층 렉 07', 'WH-PICK', now(), now()),
    ('WH-2F-R08', '2층 렉 08', 'WH-PICK', now(), now()),
    ('WH-2F-R09', '2층 렉 09', 'WH-PICK', now(), now()),
    ('WH-2F-R10', '2층 렉 10', 'WH-PICK', now(), now()),
    ('WH-3F-R01', '3층 렉 01', 'WH-PICK', now(), now()),
    ('WH-3F-R02', '3층 렉 02', 'WH-PICK', now(), now()),
    ('WH-3F-R03', '3층 렉 03', 'WH-PICK', now(), now()),
    ('WH-3F-R04', '3층 렉 04', 'WH-PICK', now(), now()),
    ('WH-3F-R05', '3층 렉 05', 'WH-PICK', now(), now()),
    ('WH-3F-R06', '3층 렉 06', 'WH-PICK', now(), now()),
    ('WH-3F-R07', '3층 렉 07', 'WH-PICK', now(), now()),
    ('WH-3F-R08', '3층 렉 08', 'WH-PICK', now(), now());

-- ---- 재고를 렉으로 옮긴다 ----
-- 데모 시드 재배치라 이동 이력(stock_movements)은 남기지 않는다. 지난 이력은 그대로 두고,
-- 여기서부터의 증감만 이력으로 쌓인다.
delete from stocks where location_id = (select id from locations where location_code = 'WH-A');

-- 만재(200/120/240)에 비해 적재율이 골고루 흩어지게 둔다 — 지도에서 색이 단조롭지 않도록.
insert into stocks (location_id, item_id, quantity, created_at, updated_at)
select l.id, i.id, v.qty, now(), now()
from (values
    ('WH-1F-R01', 'ITEM-1001', 180),
    ('WH-1F-R02', 'ITEM-1002', 120),
    ('WH-1F-R03', 'ITEM-1003',  40),
    ('WH-1F-R04', 'ITEM-1001',   0),
    ('WH-1F-R05', 'ITEM-1002', 200),
    ('WH-1F-R06', 'ITEM-1003',  90),
    ('WH-1F-R07', 'ITEM-1001', 150),
    ('WH-1F-R08', 'ITEM-1002',  30),
    ('WH-1F-R09', 'ITEM-1003',   0),
    ('WH-1F-R10', 'ITEM-1001', 110),
    ('WH-1F-R11', 'ITEM-1002',  60),
    ('WH-1F-R12', 'ITEM-1003', 190),
    ('WH-2F-R01', 'ITEM-1001', 100),
    ('WH-2F-R02', 'ITEM-1002',  60),
    ('WH-2F-R03', 'ITEM-1003',  20),
    ('WH-2F-R04', 'ITEM-1001',   0),
    ('WH-2F-R05', 'ITEM-1002',  90),
    ('WH-2F-R06', 'ITEM-1003', 110),
    ('WH-2F-R07', 'ITEM-1001',  45),
    ('WH-2F-R08', 'ITEM-1002',  75),
    ('WH-2F-R09', 'ITEM-1003',   0),
    ('WH-2F-R10', 'ITEM-1001',  30),
    ('WH-3F-R01', 'ITEM-1001', 220),
    ('WH-3F-R02', 'ITEM-1002', 180),
    ('WH-3F-R03', 'ITEM-1003',   0),
    ('WH-3F-R04', 'ITEM-1001', 120),
    ('WH-3F-R05', 'ITEM-1002',  60),
    ('WH-3F-R06', 'ITEM-1003', 240),
    ('WH-3F-R07', 'ITEM-1001',  90),
    ('WH-3F-R08', 'ITEM-1002',  30)
) as v(location_code, item_code, qty)
join locations l on l.location_code = v.location_code
join items i on i.item_code = v.item_code;
