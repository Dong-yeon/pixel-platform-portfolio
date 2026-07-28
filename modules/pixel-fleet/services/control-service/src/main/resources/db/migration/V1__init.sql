-- PixelFleet control-service initial schema.
-- Passwords are bcrypt-encoded at runtime, so demo users are seeded in code
-- (UserDataInitializer), not here. Robots are seeded in V2.

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

create table robots (
    id bigserial primary key,
    robot_code varchar(30) not null unique,
    name varchar(50) not null,
    status varchar(20) not null,
    battery_percent integer not null,
    pos_x double precision not null,
    pos_y double precision not null,
    last_heartbeat_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table transport_tasks (
    id bigserial primary key,
    task_code varchar(50) not null unique,
    origin_node varchar(30) not null,
    destination_node varchar(30) not null,
    priority varchar(20) not null,
    status varchar(20) not null,
    assigned_robot_id bigint references robots (id),
    retry_count integer not null,
    assigned_at timestamp,
    started_at timestamp,
    finished_at timestamp,
    failure_reason varchar(500),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_transport_tasks_status on transport_tasks (status);

-- Append-only event log: the single source of truth for fleet state history.
create table fleet_events (
    id bigserial primary key,
    event_type varchar(50) not null,
    source_type varchar(30) not null,
    source_id bigint,
    target_type varchar(30) not null,
    target_id bigint,
    task_id bigint,
    severity varchar(20) not null,
    message varchar(500) not null,
    payload_json text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_fleet_events_task_id on fleet_events (task_id);
create index idx_fleet_events_id_desc on fleet_events (id desc);
