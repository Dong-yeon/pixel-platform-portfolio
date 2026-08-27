-- P32 D9 — 충전존과 겹치던 창고동 렉 4기(WH-1F-B12-R37~R40)를 factory가 지웠다(V23).
-- WMS도 같은 4개 로케이션을 정리한다. 설계 근거:
-- docs/p32-warehouse-realistic-relayout-design.md "2-1. D8~D10".
--
-- R37에 시드 파렛트(V11)가 하나 꽂혀 있었다(ITEM-1003, 70개) — 재고를 먼저 지우고
-- 파렛트, 그다음 로케이션 순서로 지운다(FK 순서, V4의 정리 패턴과 같다).
delete from stocks
where pallet_id in (select id from pallets where plt_code = 'PLT-SEED-WH-1F-B12-R37');

delete from pallets where plt_code = 'PLT-SEED-WH-1F-B12-R37';

delete from locations
where location_code in ('WH-1F-B12-R37', 'WH-1F-B12-R38', 'WH-1F-B12-R39', 'WH-1F-B12-R40');
