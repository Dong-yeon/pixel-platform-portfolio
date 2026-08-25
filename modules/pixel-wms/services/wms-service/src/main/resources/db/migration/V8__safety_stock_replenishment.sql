-- P26: 안전재고 기반 보충 작업 자동 생성.
--
-- **왜 로케이션 단위인가.** 원본 자료(상품리스트.xlsx)의 안전재고 컬럼은 품목 단위지만,
-- 품목 총량이 낮다고 로봇이 옮길 대상이 생기는 건 아니다(그건 구매/생산 신호다) — 로봇이
-- 실제로 일을 만들어 내는 건 "이 로케이션은 낮은데 저 로케이션엔 아직 있다"는 상황뿐이다.
-- 그래서 안전재고를 로케이션에 둔다(설계 근거: docs/p26-safety-stock-replenishment-design.md
-- 0절).
alter table locations add column safety_stock_qty integer;

-- 데모 시연용 — 몇몇 1층 로케이션에 안전재고를 지정한다(나머지는 null, 모니터링 안 함).
-- 실제 값이 이 아래로 떨어져야 트리거되므로(D4 — 출고 완료 시점에만 확인), 지금 시드
-- 수량보다 넉넉히 높게 잡아 두면 그 로케이션에서 나가는 출고가 생기는 순간 자연히
-- 보충이 걸린다.
update locations set safety_stock_qty = 50  where location_code = 'WH-1F-R08';  -- ITEM-1002, 시드 30
update locations set safety_stock_qty = 60  where location_code = 'WH-1F-R03';  -- ITEM-1003, 시드 40

-- ---- 보충 지시(내부 이동) ----
-- OutboundOrder와 구조는 비슷하지만 완료 의미가 다르다 — 파렛트를 은퇴시키지 않고
-- 위치만 옮긴다(D2). 그래서 별도 엔티티다(InboundOrder/OutboundOrder가 이미 그런 것처럼).
create table replenishment_orders (
    id                 bigserial primary key,
    order_no           varchar(50) not null unique,
    item_id            bigint not null references items (id),
    from_location_id   bigint not null references locations (id),
    to_location_id     bigint not null references locations (id),
    pallet_id          bigint not null references pallets (id),
    quantity           integer not null,
    status             varchar(20) not null,
    task_code          varchar(50) unique,
    completed_at       timestamp,
    created_at         timestamp not null,
    updated_at         timestamp not null
);

create index idx_replenishment_orders_status on replenishment_orders (status);

-- 시스템이 스스로 판단해서 만드는 지시라 채번도 시스템이 한다(PalletCodeGenerator와 같은 패턴).
create sequence replenishment_order_seq start with 1 increment by 1;
