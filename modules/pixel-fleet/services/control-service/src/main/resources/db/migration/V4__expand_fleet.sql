-- 공장 규모 확장에 맞춰 AMR을 6대로 늘린다.
-- 로봇의 라이브 상태(위치·배터리·상태)는 Redis에 있으므로 여기에는 마스터만 넣는다.
insert into robots (robot_code, name, created_at, updated_at) values
    ('AMR-04', '4호기', now(), now()),
    ('AMR-05', '5호기', now(), now()),
    ('AMR-06', '6호기', now(), now());
