-- pixel-qms 초기 스키마 (품질관리).
--
-- **DB per module.** factory의 설비·작업지시를 FK로 잡지 않는다. 다른 모듈의 개념은
-- 코드 문자열(equipment_code, work_order_no, lot_no)로만 들고 있어, QMS를 따로 띄우고
-- 따로 내릴 수 있게 한다(컴포저블).

-- 공통 코어(com.pixelplatform.core)의 User 엔티티가 스캔되므로 이 DB에도 있어야
-- `ddl-auto: validate`가 통과한다. 로그인 창구는 플랫폼에 하나뿐이라 **시드하지 않는다**.
create table users (
    id bigserial primary key,
    username varchar(50) not null unique,
    password varchar(255) not null,
    name varchar(50) not null,
    role varchar(30) not null,
    department varchar(50),
    status varchar(20) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

-- ---- 불량 유형 마스터 ----
create table defect_types (
    id          bigserial primary key,
    defect_code varchar(30) not null unique,
    name        varchar(50) not null,
    created_at  timestamp not null,
    updated_at  timestamp not null
);

-- ---- 검사 ----
-- inspection_type: INCOMING(수입) | IN_PROCESS(공정) | FINAL(최종)
-- result: PENDING | PASSED | FAILED
create table inspections (
    id              bigserial primary key,
    inspection_no   varchar(50) not null unique,
    inspection_type varchar(20) not null,
    equipment_code  varchar(30),
    work_order_no   varchar(50),
    lot_no          varchar(50),
    inspector_id    bigint,
    result          varchar(20) not null,
    inspected_qty   integer not null default 0,
    defect_qty      integer not null default 0,
    note            varchar(500),
    completed_at    timestamp,
    created_at      timestamp not null,
    updated_at      timestamp not null
);

-- ---- 부적합 (NCR) ----
create table nonconformances (
    id             bigserial primary key,
    ncr_no         varchar(50) not null unique,
    inspection_id  bigint references inspections (id),
    defect_type_id bigint references defect_types (id),
    equipment_code varchar(30),
    work_order_no  varchar(50),
    lot_no         varchar(50),
    defect_qty     integer not null,
    description    varchar(500),
    created_at     timestamp not null,
    updated_at     timestamp not null
);

-- ---- MRB 심의 ----
-- status: RAISED → UNDER_REVIEW → DECIDED → CLOSED
-- decision: USE_AS_IS(특채) | REWORK(재작업) | SCRAP(폐기) | RETURN(반품)
--
-- **MRB가 열리면 factory의 설비를 QUALITY_HOLD, 작업지시를 ON_HOLD로 만든다.**
-- 판정이 끝나면 홀드를 푼다. 별개 서비스·별개 DB가 계약만으로 연동되는 지점이다.
create table mrb_reviews (
    id                bigserial primary key,
    mrb_no            varchar(50) not null unique,
    nonconformance_id bigint not null references nonconformances (id),
    equipment_code    varchar(30),
    work_order_no     varchar(50),
    lot_no            varchar(50),
    status            varchar(20) not null,
    decision          varchar(20),
    decided_by        bigint,
    decision_note     varchar(500),
    /** factory에 홀드를 실제로 걸었는지 — 판정 시 풀어야 할 대상인지 판단한다. */
    hold_applied      boolean not null default false,
    decided_at        timestamp,
    closed_at         timestamp,
    created_at        timestamp not null,
    updated_at        timestamp not null
);

-- ---- 알림 발송함 (Outbox) ----
-- **실제 SMTP를 붙이지 않는다.** 배포 환경에서 포트가 막히고 스팸 처리되며 데모에서 재현이
-- 안 된다. 대신 발송 내역을 여기 쌓고 대시보드가 메일 카드로 보여준다.
-- 확장점은 NotificationSender 인터페이스(OutboxSender 기본 / SmtpSender 프로필 전환).
create table notifications (
    id           bigserial primary key,
    recipient    varchar(200) not null,
    subject      varchar(200) not null,
    body         text not null,
    channel      varchar(20) not null,
    reference_no varchar(50),
    sent_at      timestamp not null,
    created_at   timestamp not null,
    updated_at   timestamp not null
);

create index idx_inspections_result on inspections (result);
create index idx_mrb_reviews_status on mrb_reviews (status);
create index idx_notifications_sent_at on notifications (sent_at desc);
