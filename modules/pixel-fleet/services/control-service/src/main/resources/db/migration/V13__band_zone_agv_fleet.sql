-- P32 D10 — 창고동 1층 AGV 존을 밴드 단위로 재정의하면서 로봇 대수를 밴드 수만큼 늘린다.
-- 설계 근거: docs/p32-warehouse-realistic-relayout-design.md "2-1. D8~D10".
--
-- **왜 지금까지 존이 하나(WH-PICK)였나.** P22까지는 창고동 1층 안쪽이 전부 AGV
-- 담당이라는 사실만 중요했지, 그 안에서 다시 나눌 이유가 없었다. P32(D3)로 밴드
-- 12개가 배타 잠금 단위가 된 뒤에도 배차 존은 여전히 하나로 뭉쳐 있었다 —
-- OrderService.requiredPool()이 존을 밴드별로 계산하도록 바뀌면서(D10, 같은 커밋의
-- Java 변경) 로봇 쪽도 밴드별 zone_code가 필요해졌다.
--
-- **왜 로봇을 24대까지 늘리나.** 배차 필터는 `order.zoneCode.equals(robot.zoneCode)`
-- 순수 문자열 완전일치라(조사 확인), 존을 밴드 12개로 쪼개면서 로봇을 그대로 2대만
-- 두면 밴드 10개가 영구히 배차 불가가 된다. V10 마이그레이션이 이미 남긴 교훈 —
-- "존당 1대뿐인 존은 그 1대가 충전에 들어가면 존 전체가 멈춘다" — 을 그대로 지켜
-- 밴드당 2대씩 채운다(12밴드 × 2대 = 24, 기존 2대 + 신규 22대).
--
-- **왜 robot-sim application.yml도 같이 고쳐야 하는가.** robot-sim의 시뮬레이션 대상
-- 로봇 목록은 이 테이블이 아니라 그쪽 설정 파일에 하드코딩돼 있다(자동 동기화 없음,
-- 기존부터 있던 수동 동기화 관행) — 이 마이그레이션과 같은 커밋에서 application.yml에
-- AGV-07~AGV-28을 추가한다.

-- 기존 2대: 밴드1/2 담당으로 zone_code 갱신.
update robots set zone_code = 'WH-1F-B01', updated_at = now() where robot_code = 'AGV-01';
update robots set zone_code = 'WH-1F-B02', updated_at = now() where robot_code = 'AGV-02';

-- 신규 22대 — 밴드당 2대 원칙(V10 주석의 교훈: 존당 1대뿐이면 그 1대가 충전
-- 들어가면 존 전체가 멈춘다). 도크는 8개를 순환 배정한다.
insert into robots (robot_code, name, floor_no, robot_type, zone_code, created_at, updated_at) values
    ('AGV-07', '1층 AGV 7호(밴드1)', 1, 'AGV', 'WH-1F-B01', now(), now()),
    ('AGV-08', '1층 AGV 8호(밴드2)', 1, 'AGV', 'WH-1F-B02', now(), now()),
    ('AGV-09', '1층 AGV 9호(밴드3)', 1, 'AGV', 'WH-1F-B03', now(), now()),
    ('AGV-10', '1층 AGV 10호(밴드3)', 1, 'AGV', 'WH-1F-B03', now(), now()),
    ('AGV-11', '1층 AGV 11호(밴드4)', 1, 'AGV', 'WH-1F-B04', now(), now()),
    ('AGV-12', '1층 AGV 12호(밴드4)', 1, 'AGV', 'WH-1F-B04', now(), now()),
    ('AGV-13', '1층 AGV 13호(밴드5)', 1, 'AGV', 'WH-1F-B05', now(), now()),
    ('AGV-14', '1층 AGV 14호(밴드5)', 1, 'AGV', 'WH-1F-B05', now(), now()),
    ('AGV-15', '1층 AGV 15호(밴드6)', 1, 'AGV', 'WH-1F-B06', now(), now()),
    ('AGV-16', '1층 AGV 16호(밴드6)', 1, 'AGV', 'WH-1F-B06', now(), now()),
    ('AGV-17', '1층 AGV 17호(밴드7)', 1, 'AGV', 'WH-1F-B07', now(), now()),
    ('AGV-18', '1층 AGV 18호(밴드7)', 1, 'AGV', 'WH-1F-B07', now(), now()),
    ('AGV-19', '1층 AGV 19호(밴드8)', 1, 'AGV', 'WH-1F-B08', now(), now()),
    ('AGV-20', '1층 AGV 20호(밴드8)', 1, 'AGV', 'WH-1F-B08', now(), now()),
    ('AGV-21', '1층 AGV 21호(밴드9)', 1, 'AGV', 'WH-1F-B09', now(), now()),
    ('AGV-22', '1층 AGV 22호(밴드9)', 1, 'AGV', 'WH-1F-B09', now(), now()),
    ('AGV-23', '1층 AGV 23호(밴드10)', 1, 'AGV', 'WH-1F-B10', now(), now()),
    ('AGV-24', '1층 AGV 24호(밴드10)', 1, 'AGV', 'WH-1F-B10', now(), now()),
    ('AGV-25', '1층 AGV 25호(밴드11)', 1, 'AGV', 'WH-1F-B11', now(), now()),
    ('AGV-26', '1층 AGV 26호(밴드11)', 1, 'AGV', 'WH-1F-B11', now(), now()),
    ('AGV-27', '1층 AGV 27호(밴드12)', 1, 'AGV', 'WH-1F-B12', now(), now()),
    ('AGV-28', '1층 AGV 28호(밴드12)', 1, 'AGV', 'WH-1F-B12', now(), now());
