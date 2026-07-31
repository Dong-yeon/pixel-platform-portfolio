-- pixel-wms 초기 스키마.
--
-- **DB per module.** 이 스키마는 pixelwms DB에만 있고, factory/fleet DB를 참조하지 않는다.
-- 다른 모듈의 개념은 FK가 아니라 **코드 문자열**로만 들고 있다:
--   - locations.node_code  → factory layout_nodes.node_code 와 코드 정합(FK 아님)
--   - outbound_orders.task_code → fleet transport_tasks.task_code 와 코드 정합(FK 아님)
-- 코드로 느슨히 묶어야 모듈을 따로 띄우고 따로 내릴 수 있다(컴포저블).

-- ---- 공통 코어 사용자 ----
-- 공통 코어(com.pixelplatform.core)의 User 엔티티가 스캔되므로 이 DB에도 테이블이 있어야
-- `ddl-auto: validate`가 통과한다(fleet도 같은 이유로 갖고 있다).
-- **여기에 계정을 시드하지 않는다** — 로그인 창구는 플랫폼에 하나(pixel-factory)뿐이고,
-- WMS는 그 토큰을 검증만 한다. 이 테이블은 스키마 정합을 위해 비워 둔다.
create table users (
    id bigserial primary key,
    username varchar(50) not null unique,
    password varchar(255) not null,
    name varchar(50) not null,
    role varchar(30) not null,
    department varchar(50),
    status varchar(20) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

-- ---- 품목 마스터 ----
-- **표준CT를 여기서 관리한다(D6).** 지금까지 factory가 설비 고정값(equipments.ideal_cycle_time_ms)으로
-- OEE의 P를 계산했는데, 표준CT는 설비가 아니라 **품번×공정**의 속성이다.
create table items (
    id          bigserial primary key,
    item_code   varchar(30) not null unique,
    name        varchar(100) not null,
    unit        varchar(10) not null default 'EA',
    created_at  timestamp not null,
    updated_at  timestamp not null
);

-- 품번 × 공정 표준CT. 같은 품번이라도 공정마다 다르다.
create table item_standard_cycle_times (
    id                bigserial primary key,
    item_id           bigint not null references items (id),
    process_code      varchar(30) not null,
    standard_cycle_time_ms integer not null,
    created_at        timestamp not null,
    updated_at        timestamp not null,
    constraint uq_item_process unique (item_id, process_code)
);

-- ---- 창고 로케이션 ----
-- node_code 는 factory 평면도의 노드 코드와 맞춘다(WAREHOUSE, SHIPPING 등).
-- AMR 운송의 출발/도착지가 되므로 코드가 어긋나면 로봇이 엉뚱한 곳으로 간다.
create table locations (
    id            bigserial primary key,
    location_code varchar(30) not null unique,
    name          varchar(50) not null,
    node_code     varchar(30) not null,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

-- ---- 재고 (로케이션 × 품목) ----
create table stocks (
    id          bigserial primary key,
    location_id bigint not null references locations (id),
    item_id     bigint not null references items (id),
    quantity    integer not null,
    created_at  timestamp not null,
    updated_at  timestamp not null,
    constraint uq_stock_location_item unique (location_id, item_id),
    constraint ck_stock_quantity_non_negative check (quantity >= 0)
);

-- ---- 입출고 지시 ----
create table inbound_orders (
    id            bigserial primary key,
    order_no      varchar(50) not null unique,
    item_id       bigint not null references items (id),
    location_id   bigint not null references locations (id),
    quantity      integer not null,
    status        varchar(20) not null,
    completed_at  timestamp,
    created_at    timestamp not null,
    updated_at    timestamp not null,
    constraint ck_inbound_quantity_positive check (quantity > 0)
);

-- 출고지시가 fleet 운송 작업을 만든다. task_code 로 느슨히 연결하고(FK 아님),
-- 운송 완료 이벤트를 받으면 그 코드로 되찾아 재고를 차감한다.
create table outbound_orders (
    id               bigserial primary key,
    order_no         varchar(50) not null unique,
    item_id          bigint not null references items (id),
    from_location_id bigint not null references locations (id),
    to_node_code     varchar(30) not null,
    quantity         integer not null,
    status           varchar(20) not null,
    task_code        varchar(50) unique,
    completed_at     timestamp,
    created_at       timestamp not null,
    updated_at       timestamp not null,
    constraint ck_outbound_quantity_positive check (quantity > 0)
);

-- ---- 이동 이력 (이벤트 소싱) ----
-- 재고 수량은 이 이력의 접힌 결과다. 수량만 고쳐 쓰면 "왜 줄었는지"에 답할 수 없다.
create table stock_movements (
    id            bigserial primary key,
    item_id       bigint not null references items (id),
    location_id   bigint not null references locations (id),
    quantity_delta integer not null,
    movement_type varchar(20) not null,
    reference_no  varchar(50),
    occurred_at   timestamp not null,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

create index idx_stock_movements_occurred_at on stock_movements (occurred_at desc);
create index idx_outbound_orders_status on outbound_orders (status);
create index idx_outbound_orders_task_code on outbound_orders (task_code);
