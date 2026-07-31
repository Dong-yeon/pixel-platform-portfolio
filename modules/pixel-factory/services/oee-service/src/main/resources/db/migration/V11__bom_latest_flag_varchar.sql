-- boms.latest_yn 을 char(1) → varchar(1) 로.
--
-- **왜 앞 마이그레이션을 고치지 않고 새로 만드나.** V10은 이미 적용돼 체크섬이 기록됐다.
-- 파일을 고치면 다음 기동에서 Flyway가 "checksum mismatch"로 막는다. 적용된 마이그레이션은
-- 고치지 않고 앞으로 나아간다.
--
-- **왜 틀렸나.** Postgres의 `char(1)`은 bpchar(고정폭·공백 채움)이고 Hibernate의 String은
-- varchar를 기대한다. 그래서 `ddl-auto: validate`가
-- "found [bpchar], but expecting [varchar(1)]" 로 기동을 막았다.
-- 이 리포에서 numeric↔Double로 이미 한 번 겪은 것과 같은 계열의 함정이다 —
-- **스키마와 엔티티 타입이 정확히 같아야 하고, 어긋나면 컴파일이 아니라 기동에서 터진다.**
-- 값이 Y/N 두 가지뿐이라도 고정폭 char를 쓸 이유가 없으므로 varchar로 맞춘다.

alter table boms alter column latest_yn type varchar(1);
alter table boms alter column latest_yn set default 'Y';
