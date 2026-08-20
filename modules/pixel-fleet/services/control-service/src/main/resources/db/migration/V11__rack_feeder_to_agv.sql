-- P22: AMR이 창고동에 아예 들어오지 않게 — 랙 피더(RACK_FEEDER)를 AGV로 확장·개명한다.
-- 설계 근거: docs/p22-amr-agv-boundary-design.md.
--
-- P21에서 랙 피더는 "렉 → 피킹존" 구간만 맡았다. 이번엔 창고동 1층 안쪽 전체(입고장·
-- 출하장·도크 포함)를 맡도록 담당 구역을 넓힌다 — 이름도 실제 역할에 맞게 AGV로 바꾼다.
-- 값만 바뀌고 스키마(robot_type/zone_code 컬럼)는 그대로다.
update robots set robot_type = 'AGV' where robot_type = 'RACK_FEEDER';
update fleet_orders set robot_type = 'AGV' where robot_type = 'RACK_FEEDER';

-- 로봇 코드도 이름에 맞춘다(RF-01~06 → AGV-01~06). 1층 2대는 창고동 도크(WH-DOCK-1/2)로
-- 집을 옮긴다 — 이제 창고동 전체가 담당 구역이라 도크가 자연스럽다(2·3층은 그대로 자기
-- 존의 피킹존이 집이다, P21 D10 범위 밖).
update robots set robot_code = 'AGV-01', name = '1층 AGV 1호' where robot_code = 'RF-01';
update robots set robot_code = 'AGV-02', name = '1층 AGV 2호' where robot_code = 'RF-02';
update robots set robot_code = 'AGV-03', name = '2층 AGV 1호' where robot_code = 'RF-03';
update robots set robot_code = 'AGV-04', name = '2층 AGV 2호' where robot_code = 'RF-04';
update robots set robot_code = 'AGV-05', name = '3층 AGV 1호' where robot_code = 'RF-05';
update robots set robot_code = 'AGV-06', name = '3층 AGV 2호' where robot_code = 'RF-06';

-- 창고동 도크(WH-DOCK-*)가 이제 AGV 전용이 되므로, 지금까지 그 도크에서 충전하던 1층
-- AMR(AMR-01~04)은 생산동 쪽 새 도크(PROD-DOCK-1~4, factory V17)로 옮긴다. fleet DB에는
-- 로봇의 "집" 좌표를 저장하지 않으므로(로봇-sim이 자기 설정으로 안다) 여기선 할 일이 없다
-- — robot-sim application.yml의 AMR-01~04 home만 맞추면 된다(이미 반영).
