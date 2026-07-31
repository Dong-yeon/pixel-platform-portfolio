-- 불량 유형 마스터 시드. 가공 라인에서 실제로 자주 쓰는 분류를 최소로 둔다.
insert into defect_types (defect_code, name, created_at, updated_at) values
    ('DIM',     '치수불량',   now(), now()),
    ('SURFACE', '외관불량',   now(), now()),
    ('CRACK',   '균열',       now(), now()),
    ('ASSY',    '조립불량',   now(), now()),
    ('OTHER',   '기타',       now(), now());
