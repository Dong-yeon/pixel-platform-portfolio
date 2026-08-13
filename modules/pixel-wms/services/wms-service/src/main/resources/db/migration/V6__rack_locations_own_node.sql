-- 렉 로케이션의 node_code를 렉 자신의 코드로 되돌린다 (P21).
--
-- **왜 되돌리는가.** V3~V5는 렉의 node_code를 그 층 피킹존 노드(WH-PICK 등)로 덮어썼다 —
-- "AMR은 지상만 다니고 위층 물건은 리프트로 내려온 셈 친다"는 가정 아래, 출고지시가 fleet에
-- 넘기는 originNode가 이미 피킹존이면 AMR이 렉 근처에 갈 필요가 없었기 때문이다. 그 결과
-- 렉→피킹존 구간이 존재한 적이 없다 — WMS가 "이미 거기 물건이 있다"고 치는 것이었다.
--
-- 이제 fleet에 **랙 피더**(창고동 렉 전용 로봇)가 생겨 그 구간을 실제로 수행한다
-- (설계 근거: docs/p21-warehouse-rack-feeder-design.md D9). fleet의 OrderService.create()가
-- originNode를 보고 렉 코드인지 스스로 판별해(LocationRegistry.isRackCode) 랙 피더 레그 +
-- AMR 레그로 자동으로 쪼갠다 — **WMS 쪽 코드는 이 마이그레이션 데이터만 바뀌고 한 줄도
-- 안 바뀐다.** OrderService.createOutbound()는 여전히 from.getNodeCode()를 그대로 fleet에
-- 넘길 뿐이고, 그 값이 이제 (피킹존이 아니라) 렉 코드 자체가 되는 차이만 있다.
--
-- 렉이 어느 피킹존과 가까운지(예전엔 R01~R03→P1, R04~R09→P2로 이 파일이 손으로 갖고 있던
-- 지식)는 이제 WMS가 몰라도 된다 — fleet의 LocationRegistry가 렉과 피킹존의 실좌표로
-- 계산한다. 물류 좌표는 물리 배치의 사실이지 재고 시스템의 관심사가 아니었다.
update locations
   set node_code = location_code,
       updated_at = now()
 where location_code ~ '^WH-[123]F-R[0-9]+$';
