-- P21: 랙 피더(창고동 렉 취출 로봇) — AMR과 다른 로봇 풀.
--
-- 지금까지 로봇은 전부 AMR(자기 층을 못 벗어난다, floor_no)이었다. 랙 피더는 한 술 더 떠서
-- 자기 존(zone_code — 피킹존 하나 + 그 존이 커버하는 렉들) 밖의 렉에도 못 간다. AMR과
-- 배차·상태·이벤트·MQTT 텔레메트리 파이프라인을 전부 공유하므로(설계 근거:
-- docs/p21-warehouse-rack-feeder-design.md D1) 별도 테이블을 만들지 않고 종류 컬럼만 더한다.
alter table robots add column robot_type varchar(20) not null default 'AMR';
alter table robots add column zone_code varchar(30);

-- 주문도 어느 로봇 풀이 실행해야 하는지 알아야 한다(D6) — 배차 후보 필터가 이 값을 본다.
-- zone_code는 robot_type=RACK_FEEDER인 주문만 채운다(그 존 로봇만 후보가 된다).
alter table fleet_orders add column robot_type varchar(20) not null default 'AMR';
alter table fleet_orders add column zone_code varchar(30);

-- 존 5개, 존당 1~2대(design doc D10) — 존당 1대뿐인 존은 그 1대가 충전에 들어가면 존 전체가
-- 멈춘다는 걸 이미 위층 AMR 증차(V6)에서 겪었다. 1층만 여유를 둔다.
insert into robots (robot_code, name, floor_no, robot_type, zone_code, created_at, updated_at) values
    ('RF-01', '1층 랙 피더 1호', 1, 'RACK_FEEDER', 'WH-PICK',   now(), now()),
    ('RF-02', '1층 랙 피더 2호', 1, 'RACK_FEEDER', 'WH-PICK',   now(), now()),
    ('RF-03', '2층 랙 피더 1호', 2, 'RACK_FEEDER', 'WH-2F-P1',  now(), now()),
    ('RF-04', '2층 랙 피더 2호', 2, 'RACK_FEEDER', 'WH-2F-P2',  now(), now()),
    ('RF-05', '3층 랙 피더 1호', 3, 'RACK_FEEDER', 'WH-3F-P1',  now(), now()),
    ('RF-06', '3층 랙 피더 2호', 3, 'RACK_FEEDER', 'WH-3F-P2',  now(), now());
