-- P23: 파렛트를 1급 엔티티로 — 재고를 (로케이션,품목)에서 (파렛트,품목)으로 재정의한다.
--
-- **왜.** EMMA 600K(닝보 동방 종합 용기 AMR 사양서)는 품목을 옮기지 않는다 — 1100×1100mm
-- 팔레트를 통째로 잭업해서 옮긴다. 지금까지 로케이션이 품목 수량을 뭉텅이로 갖고 있어
-- "몇 장의 파렛트로 나뉘어 있는지"를 표현할 수 없었다(설계 근거: docs/p23-pallet-unit-design.md
-- D1~D3, D9). 이 마이그레이션 이후 위치는 파렛트에서만 갖고, stocks는 파렛트에 딸린다.
--
-- 되돌리기 어려운 변경(stocks.location_id 제거)이라 순서를 지킨다:
--   1) pallets 신설, stocks.pallet_id를 nullable로 추가
--   2) 기존 재고를 파렛트로 백필
--   3) pallet_id를 not null로 잠그고, location_id·구 유니크 제약을 제거

-- ---- 1. 파렛트 ----
-- location은 로케이션 개념을 재사용한다(P21/P22가 이미 렉·피킹존·도크를 전부 locations로
-- 표현해 뒀다 — 파렛트 전용 위치 체계를 새로 만들 이유가 없다).
create table pallets (
    id            bigserial primary key,
    plt_code      varchar(30) not null unique,
    location_id   bigint not null references locations (id),
    status        varchar(20) not null,
    weight_kg     numeric(6,2),
    created_at    timestamp not null,
    updated_at    timestamp not null
);

create index idx_pallets_location on pallets (location_id);

-- 신규 파렛트 코드 채번용(fleet의 fleet_order_code_seq, V9와 같은 패턴).
create sequence pallet_code_seq start with 1 increment by 1;

-- ---- 2. 품목 단위중량 (D4) ----
-- nullable — 미입력 품목은 중량 검증을 건너뛴다(없는 데이터로 억지로 막지 않는다).
alter table items add column unit_weight_kg numeric(8,3);

-- ---- 3. 로케이션 파렛트 슬롯 용량 (D7) ----
-- factory layout_racks.capacityQty(EA, "만재 수량")와는 다른 축이다 — 몇 개(EA)가 아니라
-- 몇 장(파렛트)까지 앉는가. factory 스키마는 건드리지 않고 WMS 자체 값으로 둔다.
alter table locations add column max_pallet integer;

-- 렉 27기: P21 문서의 실좌표 배치(1층 4열×5단, 2층 3열×4단, 3층 2열×6단) — 열×단을 슬롯
-- 수의 근사로 쓴다(칸 하나에 단마다 파렛트 한 장, 각 단은 독립된 물리 선반이라 겹치지
-- 않는다는 가정). 실측이 아니므로 근사다.
update locations set max_pallet = 20 where location_code ~ '^WH-1F-R\d+$';  -- 4열×5단
update locations set max_pallet = 12 where location_code ~ '^WH-2F-R\d+$';  -- 3열×4단
update locations set max_pallet = 12 where location_code ~ '^WH-3F-R\d+$';  -- 2열×6단
-- WH-A(레거시, 더는 재고를 안 받는다)·WH-SHIP(출하장, 통과 지점)은 슬롯 상한을 두지
-- 않는다(NULL — 검증 생략, D7·D4와 같은 원칙).

-- ---- 4. stocks 재정의 ----
alter table stocks add column pallet_id bigint references pallets (id);
alter table stocks add column lot_no varchar(50);
alter table stocks add column inbound_dt timestamp;

-- ---- 5. 백필: 기존 27개 로케이션 재고를 파렛트로 ----
-- 수량이 0인 재고 행은 "빈 파렛트"가 아니라 애초에 파렛트가 없던 자리다(재고 없음과
-- 동의어) — 백필 대상에서 뺀다. 파렛트는 항상 무언가를 싣고 있는 물리적 실체다.
insert into pallets (plt_code, location_id, status, created_at, updated_at)
select 'PLT-LEGACY-' || lpad(s.id::text, 6, '0'), s.location_id, 'LOADED', now(), now()
from stocks s
where s.quantity > 0;

-- 실제 입고일 정보가 없으므로 마이그레이션 실행 시각을 임시 LOT 기준으로 쓴다(정직하게
-- 표시 — 진짜 입고일이 아니다). 신규 입고분부터는 OrderService.createInbound가 실제
-- 입고 시각을 채운다.
update stocks s
   set pallet_id = p.id,
       lot_no = p.plt_code,
       inbound_dt = now(),
       updated_at = now()
  from pallets p
 where p.plt_code = 'PLT-LEGACY-' || lpad(s.id::text, 6, '0');

-- 수량 0이라 파렛트를 안 만든 재고 행은 이제 의미가 없다 — 지운다.
delete from stocks where quantity = 0;

-- ---- 6. 잠금 ----
-- 파렛트당 재고 행 하나(= 파렛트당 품목 하나). EMMA 600K 사양서의 전제(지지 다리가
-- 일체형으로 고정된 단일 용도 파렛트) 그대로 잠근다 — 필요해지면 이 제약을
-- (pallet_id, item_id)로 완화할 수 있다(design doc D2).
alter table stocks drop constraint uq_stock_location_item;
alter table stocks alter column pallet_id set not null;
alter table stocks alter column lot_no set not null;
alter table stocks alter column inbound_dt set not null;
alter table stocks add constraint uq_stock_pallet unique (pallet_id);
alter table stocks drop column location_id;

create index idx_stocks_item_inbound on stocks (item_id, inbound_dt);

-- ---- 7. 입출고 지시가 파렛트를 참조한다 (D5) ----
-- 입고 = 새 파렛트 하나를 만든다. 출고 = 특정 파렛트를 통째로 옮긴다(부분 피킹 없음).
-- 둘 다 nullable — 이 마이그레이션 이전에 완료된 지시는 파렛트 개념이 없었으므로 비워 둔다
-- (이력 데이터를 억지로 채우지 않는다).
alter table inbound_orders add column pallet_id bigint references pallets (id);
alter table outbound_orders add column pallet_id bigint references pallets (id);

-- 이력에도 파렛트를 남긴다(D3 근거와 같은 이유 — 추적성). 기존 이력 행은 null로 둔다.
alter table stock_movements add column pallet_id bigint references pallets (id);
