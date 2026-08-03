-- 층별 AMR + 화물 엘리베이터 인수인계.
--
-- 창고동이 3층이 되면서 "물건은 층을 오가지만 AMR은 자기 층에 머문다"는 규칙이 생겼다.
-- 실제 물류센터의 화물 엘리베이터가 그렇다 — 사람·로봇용과 화물용이 따로다.
--
-- 그래서 운송작업도 층을 안다:
--  - 로봇에 floor_no — 배차는 같은 층 로봇만 후보로 본다.
--  - 작업에 floor_no — 출발지 노드가 속한 층. 위층 노드는 아래층과 **좌표가 겹치므로**
--    좌표로는 구분되지 않는다(WH-DOCK-1과 WH-DOCK-2F는 둘 다 4,3).
--  - 층이 다른 이송은 엘리베이터에서 두 구간으로 끊는다:
--      A층 출발지 → 엘리베이터(A층)  [A층 로봇]
--         ... 물건만 승강 ...
--      엘리베이터(B층) → B층 목적지   [B층 로봇]
--    앞 구간에 handoff_destination(최종 목적지)을 달아 두고, 그 구간이 끝나면
--    뒷 구간을 만든다. available_at은 엘리베이터가 도착하는 시각 — 그 전에는 배차되지 않는다.

alter table robots add column floor_no smallint not null default 1;

-- 1층은 창고·생산·품질을 모두 돌아야 하므로 4대, 위층은 각 1대씩.
-- (층을 오가지 못하므로 위층 로봇은 그 층 안에서만 일한다.)
update robots set floor_no = 2 where robot_code = 'AMR-05';
update robots set floor_no = 3 where robot_code = 'AMR-06';

alter table transport_tasks add column floor_no smallint not null default 1;

-- 이 구간이 끝나면 물건이 엘리베이터를 타고 올라/내려가서 여기로 간다.
-- null이면 층을 넘지 않는 보통 작업이다.
alter table transport_tasks add column handoff_destination varchar(30);

-- 엘리베이터가 도착하는 시각. 이 시각 전에는 배차하지 않는다 — 물건이 아직 안 왔는데
-- 로봇을 승강장으로 보내면 빈손으로 서 있는 그림이 된다.
alter table transport_tasks add column available_at timestamp;

-- 뒷 구간이 어느 구간을 이어받은 것인지. 화면에서 한 흐름으로 읽으려고 남긴다.
alter table transport_tasks add column handoff_of varchar(50);
