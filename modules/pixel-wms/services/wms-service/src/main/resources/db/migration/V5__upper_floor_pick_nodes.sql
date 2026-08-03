-- 위층 렉은 위층에서 집는다.
--
-- 지금까지 2·3층 렉의 node_code가 전부 1층 피킹존(WH-PICK)이었다. 층간 운반 수단이 없어서
-- "리프트로 내려온 셈 친다"고 적어 둔 자리다(V3 주석). 이제 화물 엘리베이터가 실제로
-- 생겼으므로 그 가정을 지운다 — 3층 재고를 빼면 운송은 3층에서 시작해야 한다.
--
-- 그러면 출고 운송이 저절로 층을 넘는다: fleet이 출발지·목적지의 층이 다른 것을 보고
-- 승강장에서 두 구간으로 끊는다(3층 AMR이 승강장까지 → 화물만 승강 → 1층 AMR이 출하장까지).
-- WMS는 그 사정을 모른다 — 자기가 낸 작업코드 하나로 완료를 통지받는다.
--
-- 어느 피킹 노드로 보내는가: 렉 줄 위치를 따른다. R01~R03은 위쪽 줄(y=4)이라 P1(y=6),
-- R04~R09는 아래 줄(y=13.5·22)이라 P2(y=13)가 가깝다.

update locations set node_code = 'WH-2F-P1', updated_at = now()
 where location_code in ('WH-2F-R01', 'WH-2F-R02', 'WH-2F-R03');
update locations set node_code = 'WH-2F-P2', updated_at = now()
 where location_code in ('WH-2F-R04', 'WH-2F-R05', 'WH-2F-R06',
                         'WH-2F-R07', 'WH-2F-R08', 'WH-2F-R09');

update locations set node_code = 'WH-3F-P1', updated_at = now()
 where location_code in ('WH-3F-R01', 'WH-3F-R02', 'WH-3F-R03');
update locations set node_code = 'WH-3F-P2', updated_at = now()
 where location_code in ('WH-3F-R04', 'WH-3F-R05', 'WH-3F-R06',
                         'WH-3F-R07', 'WH-3F-R08', 'WH-3F-R09');
