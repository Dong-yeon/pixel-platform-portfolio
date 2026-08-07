-- 로봇 운영 동사 (P19 나머지 작업): off-duty / disabled.
--
-- RobotStatus(IDLE/MOVING/CHARGING/ERROR/OFFLINE)는 오직 MQTT 텔레메트리로만 바뀐다
-- (RobotService.changeStatus, MqttMessageHandler.handleStatus 경유). 그 값에 조작자 의도를
-- 얹으면 다음 하트비트가 조작자의 "배차 제외" 결정을 조용히 덮어써버린다.
--
-- 그래서 조작자가 세우는 플래그는 텔레메트리가 닿지 않는 Postgres 마스터(robots)에 둔다 —
-- V3가 이미 정립한 "마스터는 정지 정체성, 라이브 상태는 Redis" 분리를 그대로 따른다.
alter table robots add column off_duty boolean not null default false;
alter table robots add column disabled boolean not null default false;
