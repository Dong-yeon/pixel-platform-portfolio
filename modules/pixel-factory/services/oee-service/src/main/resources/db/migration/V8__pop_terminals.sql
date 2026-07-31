-- POP(Point of Production) 단말 마스터.
--
-- **왜 factory가 소유하는가.** 단말도 설비·하역 지점과 같은 바닥(layout) 위에 선다(V7 주석 참고).
-- 평면도 마스터를 factory가 갖는 것과 같은 이유로, 단말 좌표도 여기 둔다.
--
-- **좌표 타입 주의.** pos_x/pos_y 는 기하값이라 double precision 이다. numeric(6,2)로 두고
-- 엔티티를 Double 로 매핑하면 `ddl-auto: validate` 가 기동을 막는다
-- (found [numeric], but expecting [float(53)]). layout_nodes/equipments 와 동일하게 맞춘다.

create table pop_terminals (
    id            bigserial primary key,
    terminal_code varchar(30) not null unique,   -- POP-A1
    name          varchar(50) not null,
    line_id       bigint not null references production_lines (id),
    pos_x         double precision not null,
    pos_y         double precision not null,
    created_at    timestamp not null,
    updated_at    timestamp not null
);

-- 설비 여러 대당 단말 1대가 현실적이다. LINE-1 = POP-A1, LINE-2 = POP-B1.
-- 좌표는 도크 열(x=3)과 첫 설비(x=11) 사이의 빈 공간, 각 라인 열(A행 y=2.4 / B행 y=21.6)에 맞춘다.
insert into pop_terminals (terminal_code, name, line_id, pos_x, pos_y, created_at, updated_at) values
    ('POP-A1', 'POP 단말 A1', (select id from production_lines where line_code = 'LINE-1'), 6.0, 2.4,  now(), now()),
    ('POP-B1', 'POP 단말 B1', (select id from production_lines where line_code = 'LINE-2'), 6.0, 21.6, now(), now());
