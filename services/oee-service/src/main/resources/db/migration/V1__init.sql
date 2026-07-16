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

create table production_lines (
    id bigserial primary key,
    line_code varchar(30) not null unique,
    name varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table equipments (
    id bigserial primary key,
    equipment_code varchar(30) not null unique,
    name varchar(50) not null,
    line_id bigint not null references production_lines (id),
    ideal_cycle_time_ms integer not null,
    status varchar(20) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table work_orders (
    id bigserial primary key,
    work_order_no varchar(50) not null unique,
    item_id bigint not null,
    process_id bigint not null,
    equipment_id bigint not null,
    assigned_user_id bigint not null,
    lot_no varchar(50) not null,
    planned_qty integer not null,
    produced_qty integer not null,
    defect_qty integer not null,
    status varchar(30) not null,
    planned_start_at timestamp not null,
    planned_end_at timestamp not null,
    started_at timestamp,
    completed_at timestamp,
    hold_reason varchar(500),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table factory_events (
    id bigserial primary key,
    event_type varchar(50) not null,
    source_type varchar(30) not null,
    source_id bigint,
    target_type varchar(30) not null,
    target_id bigint,
    work_order_id bigint,
    lot_no varchar(50),
    severity varchar(20) not null,
    message varchar(500) not null,
    payload_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_factory_events_work_order_id on factory_events (work_order_id);
create index idx_factory_events_created_at on factory_events (created_at desc);
create index idx_work_orders_status on work_orders (status);
