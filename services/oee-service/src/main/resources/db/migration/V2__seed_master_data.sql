insert into production_lines (line_code, name, created_at, updated_at) values
    ('LINE-1', '1라인 CNC 가공', now(), now());

insert into equipments (equipment_code, name, line_id, ideal_cycle_time_ms, status, created_at, updated_at) values
    ('CNC-01', 'CNC 선반 1호기', (select id from production_lines where line_code = 'LINE-1'), 30000, 'IDLE', now(), now()),
    ('CNC-02', 'CNC 선반 2호기', (select id from production_lines where line_code = 'LINE-1'), 30000, 'IDLE', now(), now()),
    ('MCT-01', '머시닝센터 1호기', (select id from production_lines where line_code = 'LINE-1'), 45000, 'IDLE', now(), now());
