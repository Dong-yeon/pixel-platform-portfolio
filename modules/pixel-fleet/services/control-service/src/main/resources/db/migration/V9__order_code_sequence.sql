-- externalId 관례 정리 (P19 나머지 작업): fleet이 자체 order_code를 발급한다.
--
-- 지금까지는 호환 어댑터(TaskController)가 WMS가 보낸 taskCode를 order_code와
-- external_id에 동시에 박아 넣어, 두 값이 실제로 구분된 적이 없었다. 이제 fleet이
-- 이 시퀀스로 자기 order_code를 스스로 발급하고, WMS의 taskCode는 external_id
-- 자리에만 들어간다 — 완료/실패 통지는 notificationCode()가 이미 external_id를
-- 우선하므로 WMS 쪽 계약은 그대로 유지된다.
create sequence fleet_order_code_seq start with 1 increment by 1;
