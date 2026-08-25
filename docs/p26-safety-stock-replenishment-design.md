# P26 설계 문서 — 안전재고 기반 보충 작업 자동 생성

> 상태: **구현 + 실기동 검증 완료(2026-08-25).** `docs/p20~p25-*.md`와 같은 형식. 원래
> "난이도 낮음"으로
> 분류했으나, 실제로 뭘 옮기는지 따라가 보니 **새 주문 종류(내부 이동) 하나가 필요하다는
> 게 드러났다** — 그래도 P23~P25가 이미 만들어 둔 조각(파렛트·FIFO·M4형 주문 생성)을
> 그대로 재사용해서 작아진다.
>
> 선행 문서: P23(파렛트 단위 재고, FIFO) · P24(M4형 `orders/create`) · P25(로봇 규격 강제).
> 근거 자료: `상품리스트.xlsx`("파렛트 안전재고"/"피킹 안전재고" 컬럼) · 동방플라스틱
> 표준업무정의서 6-1("부족 재고 식별(부족=안전재고−현재고) → 보충 방식 판단 → 파렛트
> 호출 → 이송").

---

## 0. 범위 재확정 — 안전재고는 로케이션 단위다, 품목 총량 단위가 아니다

`상품리스트.xlsx`의 안전재고 컬럼은 **품목(item) 단위**다 — 그런데 품목 총량이 안전재고
아래로 떨어졌다고 로봇이 뭘 할 수 있는 건 아니다(그건 "더 사거나 만들어라"는 구매·생산
신호이지, 로봇이 옮길 대상이 없다 — 이미 있는 재고를 이 로케이션에서 저 로케이션으로
옮겨도 총량은 그대로다). **로봇이 실제로 일을 만들어 내는 경우는 "특정 로케이션의 재고가
낮은데 다른 로케이션엔 아직 있다"는 상황뿐이다** — 그때 로봇이 남는 곳에서 모자란 곳으로
파렛트를 옮긴다.

그래서 이번 설계는 원본 자료의 "품목 단위" 컬럼을 **로케이션 단위**로 재해석한다 — 이
포트폴리오가 증명하려는 게 "재고 관리"가 아니라 "로봇 작업이 실제 업무 근거로 생긴다"는
것이기 때문이다(root CLAUDE.md, "게임 메커닉 금지, 실제 이벤트로 반영"). 품목 총량
알림(구매팀에게 "더 만들어라")은 이번 범위 밖(8절)이다.

---

## 1. 핵심 관찰 — 왜 새 주문 종류가 필요한가

지금 `OutboundOrder`는 **재고가 WMS 밖으로 나가는 것**만 표현한다 — 완료되면 파렛트를
은퇴시키고 재고 행을 지운다(P23 D5). 보충은 반대다: **파렛트가 그대로 살아서 다른
로케이션에 다시 나타나야 한다.** `OutboundOrder`를 그대로 쓰면 보충으로 옮긴 재고가
완료 시점에 삭제돼 버린다 — 틀린 동작이다.

그래서 `ReplenishmentOrder`(내부 이동)를 신설한다. 하지만 **입고/출고를 만들 때 쓴
조각을 거의 다 재사용한다** — 파렛트 FIFO 선택(P23 D3), fleet M4형 `orders/create`
호출(P24), `materialId`(P23 D6)까지 전부 그대로다. 다른 건 완료 처리 하나뿐이다: 파렛트를
은퇴시키는 대신 **위치만 옮긴다**.

---

## 2. 핵심 원칙 재확인

| 원칙 | 이 설계가 지키는 방법 |
|---|---|
| 이벤트가 단일 진실 공급원 | 보충도 fleet 운송 완료 통지(MQTT)를 받아야 파렛트가 실제로 옮겨진다 — 생성 시점에 미리 옮기지 않는다(출고와 같은 원칙, P23) |
| 게임 메커닉 금지, 실제 이벤트로 반영 | 트리거는 **실제 출고 완료 이벤트**다(0절 근거) — 타이머나 무작위 생성이 아니다 |
| DB per module | 전부 WMS DB. fleet은 이번에도 새 스키마 없음(P24가 이미 만든 `POST /api/orders`를 그대로 부른다) |
| 전례 재사용 | FIFO 선택(D3)·파렛트 예약(`reserveForTransit`)·슬롯 검증(D7)까지 P23의 메서드를 그대로 부른다. 새 다익스트라·새 라우팅 없음 |

---

## 3. 목표 구조

```
OutboundOrder 완료(handleTransportCompleted)
        │
        ▼
ReplenishmentService.checkAndReplenish(depletedLocationId, itemId)
        │
        ├─ location.safetyStockQty == null → 모니터링 대상 아님, 종료
        ├─ 현재고(그 로케이션의 그 품목 총량) >= 안전재고 → 아직 충분, 종료
        ├─ 이미 진행 중인 보충이 있음 → 중복 생성 방지, 종료
        ├─ 다른 로케이션에 그 품목 파렛트가 없음 → 경고 로그, 종료(정직한 실패)
        │
        └─ 도너 파렛트 선정(FIFO, StockService 재사용) → ReplenishmentOrder 생성
                │
                ▼
           fleet POST /api/orders (M4형, P24) — pickup(도너) → dropoff(고갈 로케이션)
                │  materialId = palletCode (P23 D6)
                ▼
           MQTT 완료 통지 → ReplenishmentService.handleTransportCompleted(taskCode)
                │
                ▼
           StockService.relocatePallet(palletId, 도착 로케이션)
             — 파렛트 은퇴 안 함, 재고 행 안 지움. location_id만 옮기고 LOADED로 되돌린다.
```

---

## 4. 확정 결정 (D1 ~ D8)

### D1. `locations.safety_stock_qty` — 로케이션 단위(0절 근거)

```sql
alter table locations add column safety_stock_qty integer;
```

`max_pallet`(P23 D7)과 같은 자리, 같은 성격 — WMS 자체 값, null이면 모니터링 안 함.

### D2. `ReplenishmentOrder` 신설 — `OutboundOrder`와 구조는 비슷하지만 완료 의미가 다르다

```sql
create table replenishment_orders (
    id                 bigserial primary key,
    order_no           varchar(50) not null unique,
    item_id            bigint not null references items (id),
    from_location_id   bigint not null references locations (id),
    to_location_id     bigint not null references locations (id),
    pallet_id          bigint not null references pallets (id),
    quantity           integer not null,
    status             varchar(20) not null,
    task_code          varchar(50) unique,
    completed_at       timestamp,
    created_at         timestamp not null,
    updated_at         timestamp not null
);
create sequence replenishment_order_seq start with 1 increment by 1;
```

`InboundOrder`/`OutboundOrder`가 이미 별도 엔티티로 나뉜 전례(구조는 겹쳐도 의미가
다르면 따로 둔다)를 그대로 따른다 — `OutboundOrder`에 `toLocationId`를 얹어 분기하는
안도 검토했지만, "이 주문이 끝나면 파렛트가 사라지는가 옮겨지는가"라는 근본적으로 다른
완료 의미를 필드 하나로 조건 분기하면 `handleTransportCompleted`가 두 갈래로 쪼개져야
해서(현재 로직·미래 로직 둘 다) 오히려 더 헷갈린다.

`orderNo`는 시스템이 채번한다(`RPL-########`, `pallet_code_seq`와 같은 패턴, D3) — 이건
외부 호출자가 만드는 지시가 아니라 시스템이 스스로 판단해서 만드는 지시이기 때문이다.

### D3. 채번 — `ReplenishmentCodeGenerator` (기존 패턴 그대로)

`PalletCodeGenerator`(P23)·fleet `OrderCodeGenerator`(P19)와 정확히 같은 모양 —
`nextval('replenishment_order_seq')` 기반, `"RPL-" + %08d`.

### D4. 트리거 지점 — 출고 완료 직후, 그 한 곳뿐

`OrderService.handleTransportCompleted()`가 `stockService.issuePallet(...)`을 부른
직후, `replenishmentService.checkAndReplenish(order.getFromLocationId(),
order.getItemId())`를 호출한다. **주기적 스윕은 두지 않는다** — 지금 시스템에서 재고가
줄어드는 유일한 경로가 출고 완료이므로(실사 조정 등은 아직 없다), 이 시점 하나로
충분하다. 필요해지면 스케줄러를 별도로 얹을 수 있다(8절).

### D5. 도너 파렛트 선정 — FIFO, `StockService`에 메서드 2개 추가

```java
/** 다른 로케이션 중 그 품목을 실은 가장 오래된(FIFO) 파렛트. 없으면 빈 값. */
public Optional<Pallet> findDonorPallet(Long excludeLocationId, Long itemId) { ... }

/** 로케이션의 특정 품목 총수량(그 위의 모든 LOADED 파렛트 합). */
public int totalQuantityAt(Long locationId, Long itemId) { ... }
```

둘 다 `findFifoPallet`(D3, P23)이 이미 쓰는 `palletRepository`/`stockRepository` 조합을
그대로 재사용한다 — 새 쿼리 패턴이 아니다.

### D6. 완료 처리 — 파렛트를 은퇴시키지 않고 옮긴다

```java
// StockService — 신규
@Transactional
public void relocatePallet(Long palletId, Long destinationLocationId) {
    Pallet pallet = requirePallet(palletId);
    Location destination = requireLocation(destinationLocationId);
    assertSlotAvailable(destination);       // D7(P23) 그대로 재사용 — 도착지 슬롯도 지킨다
    pallet.relocateTo(destinationLocationId);
    pallet.markLoaded();                    // IN_TRANSIT → LOADED로 되돌린다(은퇴 아님)
}
```

`Pallet`에 `relocateTo(Long)`/`markLoaded()` 두 메서드를 추가한다(지금은
`markInTransit()`/`markRetired()`만 있다). `stocks` 행은 **손대지 않는다** — 위치는
파렛트에서만 파생되므로(P23 D2) 파렛트의 `location_id`만 바뀌면 재고 조회는 자동으로
새 위치를 반영한다.

**재고 이력(`stock_movements`)에는 남기지 않는다.** 그 테이블은 수량 증감(`quantity_delta`)
의 근거인데, 보충은 품목 수량이 어디서도 늘거나 줄지 않는다(같은 파렛트가 자리만 옮긴다)
— 억지로 끼워 맞추면 "수량이 안 변했는데 왜 이력이 있는가"라는 새 혼란만 생긴다. 파렛트
자체의 `updatedAt`(BaseEntity)과 서버 로그로 추적성은 충분하다(8절에 향후 필요 시
파렛트 이동 이력 테이블 후보로 남긴다).

### D7. 중복 생성 방지

```java
// ReplenishmentOrderRepository
boolean existsByItemIdAndToLocationIdAndStatusIn(Long itemId, Long toLocationId, List<OrderStatus> statuses);
// CREATED, IN_TRANSIT 상태면 "이미 처리 중" — 매번 새 출고가 끝날 때마다 같은 로케이션에
// 보충 주문이 쌓이는 걸 막는다.
```

### D8. 도너가 없을 때 — 정직하게 실패, 예외를 던지지 않는다

호출부(`OrderService.handleTransportCompleted`)는 출고 자체는 이미 성공적으로
완료됐다 — 보충을 못 찾았다고 그 트랜잭션을 되감을 이유가 없다. 로그로 경고만 남긴다
("이 품목은 다른 로케이션에도 재고가 없어 보충할 수 없습니다") — P21 D9 이후 계속 써 온
"없는 데이터로 억지로 뭘 만들지 않는다"는 원칙 그대로다.

---

## 5. 실행 단계

- [ ] WMS Flyway V8 — `locations.safety_stock_qty`(D1), `replenishment_orders` +
      `replenishment_order_seq`(D2), 데모 시연용 시드값(1~2개 로케이션에 안전재고 지정)
- [ ] `ReplenishmentOrder`/`ReplenishmentOrderRepository`/`ReplenishmentCodeGenerator`(D2·D3)
- [ ] `Pallet.relocateTo`/`markLoaded`(D6), `StockService.findDonorPallet`/`totalQuantityAt`/
      `relocatePallet`(D5·D6)
- [ ] `ReplenishmentService`(신규) — `checkAndReplenish`/`handleTransportCompleted`(D4·D7·D8)
- [ ] `OrderService.handleTransportCompleted`에서 보충 체크 호출 배선(D4)
- [ ] `MqttMessageHandler` — `completed` 이벤트를 `orderService`와 `replenishmentService`
      양쪽에 다 넘긴다(각자 자기 taskCode가 아니면 조용히 무시 — 기존 관례)
- [ ] `GET /api/replenishment-orders`(관찰용, 기존 `OrderController`에 추가)
- [ ] e2e: 안전재고 지정된 로케이션에서 출고 완료 → 보충 주문 자동 생성 → fleet
      `/api/orders` 생성 확인(materialId=도너 파렛트 코드) → MQTT 완료 → 도착 로케이션에
      파렛트가 LOADED로 다시 나타남을 확인 → 도너가 없는 품목은 경고 로그만 남고 예외
      없이 넘어감을 확인

---

## 6. 리스크 & 롤백

- **가장 위험한 지점은 D6(파렛트 위치 이전)이다** — 파렛트를 은퇴시키지 않는 새로운
  완료 경로라, 슬롯 검증(D6이 D7/P23을 재사용)을 빠뜨리면 로케이션이 `max_pallet`을
  넘어설 수 있다. `relocatePallet`이 `assertSlotAvailable`을 반드시 거치도록
  구현·테스트에서 확인한다.
- **연쇄 보충(도미노)** — 도너 로케이션도 안전재고를 두고 있었다면, 도너에서 파렛트를
  빼가는 것 자체가 도너를 또 고갈시킬 수 있다. 이번 범위에서는 도너 선정 시 도너 자신의
  안전재고를 보지 않는다(단순 FIFO) — 실제로 도미노가 관찰되면 후속 항목으로 "도너도
  자기 안전재고 이상 남겨야 한다" 조건을 추가한다(8절).
- **롤백 수단**: `ReplenishmentOrder`는 완전히 새 테이블·새 서비스라 기능을 끄려면
  `checkAndReplenish` 호출 한 줄(D4)만 지우면 된다 — 나머지(D1~D3, D5~D8)는 죽은 코드로
  남아도 무해하다.

---

## 7. 완료 기준

- [ ] 안전재고 미만 로케이션에서 출고가 완료되면 보충 주문이 자동 생성된다
- [ ] 보충 주문이 fleet에 M4형으로 생성되고, 완료 시 도너 파렛트가 은퇴하지 않고
      도착 로케이션에서 LOADED로 다시 나타난다
- [ ] 도착 로케이션의 파렛트 슬롯(`max_pallet`)을 넘으면 거절된다
- [ ] 같은 (품목, 도착 로케이션)에 이미 진행 중인 보충이 있으면 중복 생성되지 않는다
- [ ] 도너가 전혀 없으면 예외 없이 경고 로그만 남고 원래 출고 트랜잭션은 그대로 성공한다

---

## 8. 이번엔 안 하는 것 (범위 밖)

- **품목 총량 안전재고(구매/생산 알림)** — 0절 근거대로 로봇 작업과 무관해 이번 포트폴리오
  범위 밖. 필요해지면 `items.safety_stock_qty`(별도 필드)로 갈 자리이지 이번 D1과는
  다른 기능이다.
- **주기적 스윕 스케줄러** — 지금은 출고 완료 시점 하나로 충분하다(D4 근거). 재고가
  줄어드는 다른 경로(실사 조정 등)가 생기면 그때 같이 검토한다.
- **도너의 자기 안전재고 보호(연쇄 보충 방지)** — 6절에 위험으로 적어 뒀다. 실제로
  문제가 관찰된 뒤에 조건을 추가하는 게 "없는 문제를 미리 복잡하게 막지 않는다"는
  원칙에 맞다.
- **파렛트 이동 이력 테이블** — D6이 `stock_movements`에 안 남기기로 한 대신, 필요해지면
  별도 `pallet_movements`(from/to/reference) 테이블을 만드는 게 정직하다. 지금은 로그로
  충분하다고 판단한다.
- **부분 보충(여러 파렛트를 나눠 보충)** — 한 번의 부족 감지에 파렛트 하나만 옮긴다.
  그래도 안전재고 미달이면 다음 출고 완료 때 다시 감지돼 또 하나가 옮겨진다(D7의 "진행
  중이면 중복 생성 안 함"이 자연스럽게 배치를 만든다) — 별도 배치 로직은 필요 없었다.
