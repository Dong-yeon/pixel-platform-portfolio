-- P23 D6: fleet 계약에 물리 단위 식별자(파렛트 코드 등)를 실을 자리를 만든다.
--
-- M4의 containerId/Cloudia의 material_id에 대응한다. 지금은 저장·조회만 한다 — 적재/도킹
-- 검증에 쓰는 건 범위 밖(설계 근거: docs/p23-pallet-unit-design.md D6). nullable이라
-- 이 값을 안 보내는 호출부(TaskController를 거치지 않는 옛 경로 포함)는 그대로 동작한다.
alter table fleet_orders add column material_id varchar(50);
