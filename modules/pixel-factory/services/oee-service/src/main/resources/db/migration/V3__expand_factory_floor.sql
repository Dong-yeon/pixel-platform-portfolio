-- 공장 규모 확장: 2개 라인 · 설비 8대.
-- LINE-1 가공(CNC/MCT) → LINE-2 조립·검사(ASM/INS/PKG)로 이어지는 흐름.
-- 좌표는 대시보드가 들고 있다(EQUIPMENT_POSITIONS) — docs/BACKLOG.md의 정식화 항목 참고.

insert into production_lines (line_code, name, created_at, updated_at) values
    ('LINE-2', '2라인 조립·검사', now(), now());

insert into equipments (equipment_code, name, line_id, ideal_cycle_time_ms, status, created_at, updated_at) values
    ('CNC-03', 'CNC 선반 3호기', (select id from production_lines where line_code = 'LINE-1'), 30000, 'IDLE', now(), now()),
    ('ASM-01', '조립기 1호기',   (select id from production_lines where line_code = 'LINE-2'), 25000, 'IDLE', now(), now()),
    ('ASM-02', '조립기 2호기',   (select id from production_lines where line_code = 'LINE-2'), 25000, 'IDLE', now(), now()),
    ('INS-01', '검사기 1호기',   (select id from production_lines where line_code = 'LINE-2'), 20000, 'IDLE', now(), now()),
    ('PKG-01', '포장기 1호기',   (select id from production_lines where line_code = 'LINE-2'), 15000, 'IDLE', now(), now());
