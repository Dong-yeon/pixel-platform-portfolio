-- Live robot state (status/battery/position) moved to Redis. The robots table keeps
-- master data only; per-tick telemetry no longer writes to Postgres.
alter table robots
    drop column status,
    drop column battery_percent,
    drop column pos_x,
    drop column pos_y,
    drop column last_heartbeat_at;
