-- Platform Postgres bootstrap: one instance, one database per module (DB per module).
--
-- The container's POSTGRES_DB/POSTGRES_USER already created:
--   database "pixelfactory" owned by "pixel"
-- This script adds the fleet module's own database and role, so each module keeps the
-- credentials it uses standalone and no module config has to change.
--
-- NOTE: docker-entrypoint-initdb.d scripts run ONLY on first initialization of an empty
-- data volume. After changing this file, recreate the volume:
--   docker compose down -v && docker compose up -d

create role fleet with login password 'fleet';
create database pixelfleet owner fleet;

-- Future modules get their own role + database here:
--   create role vision with login password 'vision';
--   create database pixelvision owner vision;
