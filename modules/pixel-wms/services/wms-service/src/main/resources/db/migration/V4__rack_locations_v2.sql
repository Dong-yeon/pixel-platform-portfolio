-- 렉 재배치(factory V12)에 로케이션을 맞춘다.
--
-- **왜.** 충전존을 만들려고 창고동 왼쪽 렉 기둥을 통째로 비웠다(AMR이 도크에 주차하면서 렉과
-- 겹쳤기 때문이다). 그래서 층당 렉이 12/10/8 → **9/9/9** 로 바뀌었고, 사라진 코드를 가리키는
-- 로케이션이 남으면 지도에서 적재율이 붙지 않는 유령 재고가 된다
-- (`layout_racks.rack_code == locations.location_code` 로 잇는 구조라 코드가 곧 계약이다).
--
-- 없어진 렉의 재고는 남은 렉으로 합친다 — 물건이 사라진 게 아니라 자리가 바뀐 것이다.

-- 남은 9기 밖의 로케이션에 있던 재고를 같은 층 1번 렉으로 합친다.
update stocks s
set quantity = s.quantity + moved.qty, updated_at = now()
from (
    select substring(l.location_code from 1 for 6) as floor_prefix, i.id as item_id, sum(s2.quantity) as qty
    from stocks s2
    join locations l on l.id = s2.location_id
    join items i on i.id = s2.item_id
    where l.location_code ~ '^WH-[123]F-R(1[0-2])$'
    group by 1, 2
) as moved
join locations target on target.location_code = moved.floor_prefix || 'R01'
where s.location_id = target.id and s.item_id = moved.item_id;

-- 합칠 대상이 없던(1번 렉에 그 품목이 없던) 재고는 그대로 옮겨 붙인다.
update stocks s
set location_id = (select id from locations where location_code = substring(old.location_code from 1 for 6) || 'R01'),
    updated_at = now()
from locations old
where s.location_id = old.id
  and old.location_code ~ '^WH-[123]F-R(1[0-2])$'
  and not exists (
      select 1 from stocks t
      where t.item_id = s.item_id
        and t.location_id = (select id from locations where location_code = substring(old.location_code from 1 for 6) || 'R01')
  );

-- 남은 잔재(합쳐진 원본)를 지우고, 없어진 렉의 로케이션도 정리한다.
delete from stocks
where location_id in (select id from locations where location_code ~ '^WH-[123]F-R(1[0-2])$');

delete from locations where location_code ~ '^WH-[123]F-R(1[0-2])$';

-- 3층은 원래 8기였으므로 9번 렉이 없다 — 새로 만든다(1·2층은 9번까지 이미 있다).
insert into locations (location_code, name, node_code, created_at, updated_at)
select 'WH-3F-R09', '3층 렉 09', 'WH-PICK', now(), now()
where not exists (select 1 from locations where location_code = 'WH-3F-R09');
