# P23 설계 문서 — 파렛트 단위 재고 (WMS를 로봇 규격에 맞춘다)

> 상태: **구현 + 실기동 검증 완료(2026-08-25).** 9절 열린 질문은 전부 권장안대로 확정해
> 진행했다. `docs/p20~p22-*.md`와 같은 형식.
>
> 선행 문서: P13(WMS 모듈) · P19(fleet를 M4 모양으로 — 스텝 기반 주문 엔진, `forLoad`/
> `forUnload`, `stepFixed`, `externalId`) · P21(창고동 렉 취출) · P22(AMR/AGV 경계).
> 이 문서는 그 위에서 "재고가 담기는 물리 단위가 스키마에 없다"는 구멍을 다룬다.
>
> 근거 자료(외부, `D:\happyeon\05.DongBang\Book`): `amr 사양요구서.docx`(IPLUS MOBOT
> EMMA 600K 기술협의서, 닝보 동방 종합 용기), Cloudia/M4 Fleet API 문서, 동방플라스틱
> `WMS_스키마도면.html`(실무 28테이블 SQL Server 스키마), `상품리스트.xlsx`(품목 마스터 컬럼).
> 이 자료들은 실제 고객사 프로젝트 산출물이라 **값(품번·거래처·주문 데이터)은 가져오지
> 않는다** — 물리 규격 수치와 스키마 구조만 참고한다.

---

## 0. 요청 원문과 범위

> "이 폴더가 메인이였어. 이 폴더의 파일 분석해서 현재 portfolio에 적용할 수 있을지
> 분석해줘." "독립적으로 사용될것이고 fleet 도 독립적이야. 로봇규격을 중심으로 봐서 그
> 로봇 규격에 맞게 WMS를 구성해놓고 싶어."

분석 결과(대화 앞부분) 확정된 기준: **EMMA 600K 실측 규격 + Cloudia API 계약**을 물리·
프로토콜 기준으로 삼고, **M4의 order/step 모델**(이미 pixel-fleet `FleetOrder`/`OrderStep`이
이 모양이다 — P19)을 참고해 WMS 쪽을 그 위에 맞춘다. 그 시리즈의 첫 착수 지점으로
**파렛트 단위 재고(P23)** 를 골랐다 — 로봇이 실제로 옮기는 물리 단위가 지금 스키마에
없으면, 그 위에 규격(중량·통로폭·존)을 얹을 자리 자체가 없기 때문이다.

이번 문서의 범위는 **WMS(`pixel-wms`) 내부**로 한정한다. factory/fleet DB는 건드리지
않는다(D8에서 그 전제를 확인한다). fleet 계약 확장 여지는 D6에 결정 후보로 남기되, 실행
여부는 9절 열린 질문에서 승인받는다.

---

## 1. 왜 지금 필요한가 — 코드로 확인한 지금의 구멍

**로봇이 실제로 들어 올리는 물체가 스키마에 없다.**

- 지금 재고는 `stocks(location_id, item_id, quantity)`([V1__init.sql][v1])뿐이다 —
  로케이션 안의 품목 수량이 뭉텅이로 있을 뿐, "몇 장의 파렛트에 나뉘어 있는지"는 존재하지
  않는 개념이다.
- EMMA 600K는 **품목을 옮기지 않는다.** 1100×1100mm 팔레트를 잭업(리프팅)해서 옮긴다
  (사양서 §"设计输入" — *"이송 대상: 지지 다리(支腿)가 일체형으로 고정되어 있는 플라스틱
  팔레트 구조"*). 팔레트 바닥면 중심의 QR코드를 읽어 상대 위치를 확인하는 이유도 **편심
  리프팅을 방지하기 위해서**다(*"팔레트 코드는 팔레트의 기하학적 중심(重心) 위치에 부착해야
  합니다"*). 파렛트가 1급 엔티티가 아니면 이 정합성 요구 자체를 표현할 수 없다.
- **총중량 500kg 미만**은 파렛트 자체의 규정(사양서 §"물품 사양" — *"팔레트(다리 포함),
  크기 1100×1100×3000mm, 총중량 500kg 미만"*)이고, **정격 적재 600kg**는 로봇의 규격
  (사양서 §"AMR基础参数")이다 — 지금 `Item`/`Stock` 어디에도 무게 개념이 없어 이 둘 중
  어느 쪽도 검증할 자리가 없다.
- factory `layout_racks.capacityQty`는 주석 그대로 **"만재 수량(EA)"**이다
  ([LayoutRack.java][rack]). 렉이 몇 **장**의 파렛트를 앉힐 수 있는가(실무 스키마
  `STD_WH_AREA_MGT.MAX_PALLET`이 갖는 축)와는 다른 사실인데, 지금 포트폴리오에는 이 축
  자체가 없다 — EA 총량만 알고 "그 EA가 몇 개의 물리 파렛트로 나뉘어 있는지"는 모른다.
- WMS→fleet 계약(`FleetTaskClient.createTask`)은 `taskCode`/`originNode`/
  `destinationNode`/`priority`뿐이다([FleetTaskClient.java][client]) — **"무엇을 옮기는지"가
  완전히 빠져 있다.** M4의 `containerId`, Cloudia의 `material_id`가 채우는 그 자리가 지금
  없다.

**이것이 진짜 문제다.** P21/P22가 이미 "로봇이 어디까지 갈 수 있는가"(AMR/AGV 경계)를
정교하게 잡아 놨지만, "로봇이 무엇을 들 수 있는가"는 전혀 다루지 않았다. 로봇 규격을
축으로 WMS를 다시 본다는 이번 요청은, 정확히 이 두 번째 축이 비어 있다는 사실을 겨눈다.

[v1]: ../modules/pixel-wms/services/wms-service/src/main/resources/db/migration/V1__init.sql
[rack]: ../modules/pixel-factory/services/oee-service/src/main/java/com/pixelfactory/layout/domain/LayoutRack.java
[client]: ../modules/pixel-wms/services/wms-service/src/main/java/com/pixelwms/fleet/FleetTaskClient.java

---

## 2. 핵심 원칙 재확인 (기존 CLAUDE.md들과 충돌 여부)

| 원칙 | 출처 | 이 설계가 지키는 방법 |
|---|---|---|
| DB per module | 루트 CLAUDE.md | `pallets`/재정의된 `stocks` 전부 `pixelwms` DB에만 있다. factory/fleet DB는 무변경(D8) |
| 컴포저블 — 모듈 간 직접 참조 금지 | 루트 CLAUDE.md | fleet 계약 확장(D6)은 **선택적 필드 추가**뿐 — 기존 `taskCode` 계약은 그대로 동작해야 하고, 실행 여부는 별도 승인(9절) |
| 없는 데이터를 시각효과로 지어내지 않는다 | 루트 CLAUDE.md | 무게 상한(500kg)·리프팅 규격은 실측 사양서 수치를 그대로 옮긴 것이다 — 임의로 지어낸 숫자가 아니다 |
| factory는 좌표·기하학적 사실만, 로봇/재고 개념이 스며들면 안 됨 | pixel-factory CLAUDE.md | 파렛트 **슬롯 용량**(D7)은 factory `capacityQty`(EA)를 고치지 않고 WMS 자체 값으로 둔다 — factory 스키마 변경 0건 |
| 전례 있는 패턴 재사용 | P21/P22 | 이벤트 소싱(`stock_movements`)·코드 문자열로만 연결(FK 아님) 원칙을 그대로 따른다 — 새 저장 패턴을 만들지 않는다 |

---

## 3. 목표 구조

```
                     ── 지금(P22까지) ──                          ── P23 이후 ──

  locations                                          locations
  ├─ location_code (WH-2F-R04)                        ├─ location_code
  ├─ node_code                                         ├─ node_code
  └─ (재고를 직접 안 갖는다)                              ├─ max_pallet (D7, 신규)
                                                         │
  stocks                                                pallets (D1, 신규)
  ├─ location_id ───────┐                               ├─ plt_code (QR — 예: PLT-000123)
  ├─ item_id             │                               ├─ location_id ──┘
  └─ quantity  (뭉텅이)   │                               ├─ status (EMPTY/LOADED/IN_TRANSIT)
                          │                               └─ weight_kg (D4)
                     "몇 장인지 모른다"                        │
                                                         stocks (D2, 재정의)
                                                         ├─ pallet_id ─────┘  (location은 파렛트에서만)
                                                         ├─ item_id
                                                         ├─ quantity
                                                         └─ lot_no / inbound_dt (D3, FIFO 축)

  OutboundOrder                                        OutboundOrder
  ├─ fromLocationId                                     ├─ palletId (D5 — 파렛트 단위 전량 이동)
  ├─ itemId, quantity                                    ├─ itemId, quantity (파렛트 내용 그대로 승계)
  └─ taskCode → fleet /api/tasks                         └─ taskCode → fleet /api/tasks
     {originNode, destinationNode}                          {originNode, destinationNode, palletCode(D6, 옵션)}
```

**핵심 전환**: 로케이션이 품목 수량을 직접 갖던 것에서, **로케이션 → 파렛트 → 재고**의
3단으로 바뀐다. 출고는 이제 "이 로케이션에서 이 품목 N개를 뺀다"가 아니라 "이 파렛트를
통째로 옮긴다"가 된다 — EMMA 600K가 실제로 하는 일과 스키마가 같은 모양이 된다.

---

## 4. 확정 결정 (D1 ~ D9)

### D1. 파렛트를 1급 엔티티로 도입한다 — `pallets`

```sql
create table pallets (
    id            bigserial primary key,
    plt_code      varchar(30) not null unique,   -- QR 코드 문자열 (사양서 §3의 5*5C 팔레트 코드에 대응)
    location_id   bigint not null references locations (id),
    status        varchar(20) not null,          -- EMPTY | LOADED | IN_TRANSIT
    weight_kg     numeric(6,2),                   -- D4. 총중량 검증용
    created_at    timestamp not null,
    updated_at    timestamp not null
);
```

- `plt_code`가 QR 스캔 값이라는 전제는 사양서 그대로다 — *"팔레트 고밀도 적재 구역은
  300mm 간격, 일반 주행 경로는 1000mm 간격으로 2×2C QR코드를 바닥에 부착"*(로케이션용)과
  별개로 *"팔레트 바닥면 QR코드"*(파렛트 자신의 신원)가 따로 있다. 포트폴리오에는 실물
  스캐너가 없으므로 `plt_code`는 서버가 채번한다(D9 채번 규칙은 9절 열린 질문).
- **왜 새 저장소가 아니라 기존 `locations`를 그대로 참조하는가.** 파렛트의 "지금 어디
  있는가"는 로케이션 개념을 재사용하면 충분하다 — P21/P22가 이미 렉·피킹존·도크를 전부
  `locations`/노드 코드로 표현해 뒀다. 파렛트 전용 위치 체계를 새로 만들면 그 매핑을
  다시 해야 한다.

### D2. `stocks`를 (로케이션, 품목)에서 (파렛트, 품목)으로 재정의한다

```sql
alter table stocks add column pallet_id bigint references pallets (id);
-- 백필(D9) 후:
alter table stocks alter column pallet_id set not null;
alter table stocks drop column location_id;   -- 위치는 이제 파렛트에서만 갖는다(중복 제거)
alter table stocks add constraint uq_stock_pallet unique (pallet_id);
```

- **파렛트당 품목 하나(1:1)로 지금은 잠근다.** EMMA 600K 사양서의 전제 자체가 "지지
  다리가 일체형으로 고정된" 단일 용도 파렛트다 — 실무 스키마(`WMS_스키마도면.html`)의
  `TB_INVENTORY`는 `(PLT_ID, ITEM_CD, LOT_NO)`를 조합키로 둬 이론상 혼적 파렛트를
  허용하지만, 그건 지게차가 파렛트를 통째로 쌓는 랙 창고의 관례이지 이번 로봇 규격의
  전제가 아니다. **나중에 필요해지면** `uq_stock_pallet`을 `(pallet_id, item_id)`로
  완화하면 된다 — 지금 1:1로 잠그는 이유를 여기 남겨 둔다(P21 D7과 같은 종류의, "필요할
  때 정직하게 완화한다" 결정).
- 로케이션은 더 이상 재고를 직접 갖지 않는다 — `location_id`는 파렛트에서만 온다. 지금
  `stocks.location_id`와 미래의 `stocks.pallet_id → pallets.location_id`가 같은 정보를
  두 곳에 들고 있으면 어긋날 수 있으므로, 파렛트가 진실을 갖고 `stocks`에서는 뺀다.

### D3. LOT/FIFO 축을 `stocks`에 명시한다

```sql
alter table stocks add column lot_no varchar(50);
alter table stocks add column inbound_dt timestamp;
create index idx_stocks_item_inbound on stocks (item_id, inbound_dt);
```

- 지금은 `stock_movements.occurred_at`이 이력에만 있고, "이 재고 줄이 언제 들어왔는지"를
  재고 자체가 갖지 않는다. 출고가 파렛트 단위가 되면(D5) "같은 품목의 여러 파렛트 중
  어느 것부터 뺄지" 판단이 필요한데, FIFO 정렬 축이 재고 테이블에 없으면 매번
  `stock_movements`를 조인해야 한다. 실무 스키마의 `VW_ITEM_STOCK` 주석(*"FIFO 차감은
  `ITEM_CD + INBOUND_DT` 인덱스를 탑니다"*)과 같은 이유로 인덱스를 직접 둔다.
- `lot_no`는 채번 규칙을 정하지 않고 자리만 만든다 — 실무 스키마도 "LOT 채번 규칙"을
  "아직 정하지 않은 것"으로 남겨 뒀다(같은 문서 하단). 포트폴리오 범위에서는 입고
  시각으로 유일성을 확보하는 임시 규칙(`plt_code` 그대로 재사용해도 무방 — 1파렛트=1LOT)
  으로 시작한다.

### D4. 파렛트 총중량을 검증한다 — `items.unit_weight_kg`

```sql
alter table items add column unit_weight_kg numeric(8,3);  -- nullable: 미입력 품목은 검증 생략
```

```java
// Item 도메인에 필드 추가, PalletService(신규) 또는 StockService.receive() 확장 지점에서:
BigDecimal palletWeight = item.getUnitWeightKg() == null
        ? null
        : item.getUnitWeightKg().multiply(BigDecimal.valueOf(quantity));
if (palletWeight != null && palletWeight.compareTo(MAX_PALLET_WEIGHT_KG) >= 0) {   // 500kg
    throw new BusinessException(ErrorCode.INVALID_REQUEST,
            "파렛트 총중량이 규격을 초과합니다: " + palletWeight + "kg (상한 500kg 미만)");
}
```

- 사양서 수치 그대로: **파렛트 자체 규정은 500kg 미만**, **로봇 정격 적재는 600kg**.
  둘은 다른 숫자이고 다른 소유자다 — 파렛트 상한은 WMS가 입고 시점에 지키고, 로봇 정격은
  fleet가 배차 시점에 지킬 값이다(이번 범위 밖 — P25). 이번 문서는 **WMS가 지켜야 할 쪽만**
  다룬다.
- `unit_weight_kg`를 `items`에 두는 이유: 실무 스키마도 무게를 품목 속성으로 보지 않고
  `STD_BOX_MGT.MAX_LOAD_KG`(박스 규격)로 간접 계산한다 — 포트폴리오는 박스 단계를 만들지
  않으므로 품목에 직접 단위중량을 둬 계산을 한 단계 줄인다. `unit_weight_kg`가 없는
  품목(대부분의 데모 데이터)은 검증을 건너뛴다 — 없는 데이터로 억지로 막지 않는다(2절
  원칙).

### D5. 출고는 파렛트 단위 전량 이동이다 — 부분 피킹은 하지 않는다

```java
public record OutboundOrderCreateRequest(
        String orderNo,
        String palletCode,     // 신규 — 특정 파렛트를 지정
        String itemCode,       // 검증용(파렛트 내용과 일치해야 함) — palletCode 생략 시 FIFO 자동 선택
        String toNodeCode
) {}
```

- **왜 부분 피킹을 하지 않는가.** EMMA 600K는 리프팅식이다 — 파렛트를 **통째로** 들어
  올려 옮긴다. "파렛트에서 30개만 꺼내 나머지는 그대로 둔다"는 동작 자체가 이 로봇의
  물리적 능력 밖이다(그건 사람이 피킹존에서 하는 일이지 이 로봇이 하는 일이 아니다 —
  실무 표준업무정의서 1-3의 "PDA Lot 바코드 스캔"이 그 사람 작업이다). 부분 피킹은
  **범위 밖**으로 8절에 명시한다.
- `palletCode`를 생략하면 서비스가 `itemCode` + `fromLocationCode`로 그 위치의 파렛트
  중 `inbound_dt`가 가장 빠른 것을 자동 선택한다(D3 인덱스 사용) — 지금 API의 편의성을
  유지하면서 내부적으로만 파렛트를 고른다.
- 출고 완료(`handleTransportCompleted`) 시 그 파렛트의 `stocks` 행을 삭제하고 파렛트
  상태를 소진 상태로 바꾼다 — **빈 파렛트 회수·재사용 순환은 이번 범위 밖**(8절)이다.
  지금 스키마도 "출고 = 그 재고가 없어진다"는 전제였으므로(`OutboundOrder` 주석 그대로)
  이 부분은 기존 동작을 유지하는 것에 가깝다.

### D6. fleet 계약에 파렛트 코드를 실을지는 **이번엔 결정만 하고 실행은 미룬다**

```java
// fleet CreateTaskRequest에 추가할 후보(현재 미실행):
public record CreateTaskRequest(
        @NotBlank String taskCode,
        @NotBlank String originNode,
        @NotBlank String destinationNode,
        @NotBlank String priority,
        String materialId   // 신규, nullable — M4의 containerId/Cloudia의 material_id에 대응
) {}
```

- fleet의 `TaskController`는 스스로를 "호환 어댑터"라 부르며 *"P19-2에서 M4형 주문
  API로 옮기면 이 컨트롤러는 삭제"*라고 이미 적어 뒀다(`docs/BACKLOG.md` P19 "의도적으로
  미룬 것" — *"M4형 `orders/create`(steps 배열 입력) — 생성은 계속 `TaskController` 전담"*).
  즉 fleet 쪽에 파렛트 코드를 제대로 실으려면 이 어댑터를 넘어 M4형 생성 API로 가는
  작업(P19가 이미 범위 밖으로 미룬 것)이 선행돼야 정직하다.
- **이번 P23에서는 WMS 쪽 파렛트 모델만 완성한다.** fleet 계약 확장은 `materialId`
  같은 **선택적(nullable) 필드 추가**로 하위호환을 지키는 안만 여기 적어 두고, 실제
  실행은 9절 열린 질문으로 승인받는다 — 실행하면 fleet 쪽 마이그레이션이 필요없는
  변경(nullable 컬럼 없이 요청 바디에만 필드 추가, 무시해도 무방)이라 P23 안에서 같이
  가도 리스크가 작다는 점은 미리 적어 둔다.

### D7. `locations`에 파렛트 슬롯 용량을 추가한다 — factory의 EA 용량과는 다른 축

```sql
alter table locations add column max_pallet integer;
```

- factory `layout_racks.capacityQty`는 "몇 **개**(EA)까지 쌓이는가"이고, 이건 "몇 **장**
  (파렛트)까지 앉는가"다 — 실무 스키마 `STD_WH_AREA_MGT.MAX_PALLET`이 정확히 이 축을
  따로 갖는 이유와 같다. **factory 스키마는 건드리지 않는다**(D8) — WMS 자신의 값으로
  둔다. 지금 27개 렉 로케이션의 `max_pallet` 시드값은 P21 문서의 렉 배치(1층 4열×5단,
  2층 3열×4단, 3층 2열×6단)에서 "열 수"를 파렛트 슬롯 수의 근사로 쓴다(칸마다 파렛트
  하나, 단은 위아래로 쌓이는 것이 아니라 렉의 물리적 레벨이므로 겹치지 않는다는 가정 —
  실측이 아니므로 근사임을 마이그레이션 주석에 남긴다).
- 입고 시 그 로케이션의 현재 파렛트 수가 `max_pallet`에 닿으면 거절한다 — 지금은 이
  상한 자체가 없어 렉이 무한정 쌓일 수 있다.

### D8. factory/fleet DB·API는 이번 범위에서 **변경하지 않는다** (전제 확인)

- factory: `layout_racks.capacityQty`(EA)는 그대로 둔다 — 대시보드의 적재율 계산
  (`WMS 재고 수량 합 / capacityQty`)은 로케이션 단위 합산이므로, 그 로케이션 안이 파렛트
  몇 장으로 나뉘든 **합계는 그대로다.** 즉 대시보드는 이번 변경으로 무변경이다(실제로는
  `sum(stocks.quantity where pallet.location_id = X)`로 조인 한 단계가 늘 뿐, 응답 값은
  같다).
- fleet: `robots`/`fleet_orders` 스키마 무변경. D6을 실행하기로 결정하더라도
  `CreateTaskRequest`에 nullable 필드 하나가 늘 뿐 마이그레이션이 없다.
- **이 전제가 틀리면**(예: 대시보드가 실제로는 로케이션 내부의 파렛트 분포를 보여줘야
  한다는 요구가 나오면) 이번 설계를 다시 열어야 한다 — 실행 단계 완료 기준에 이 전제
  재확인을 넣는다(7절).

### D9. 마이그레이션 — 기존 27개 로케이션 재고를 파렛트로 백필한다

```sql
-- V7__pallet_unit_stock.sql (예시)
insert into pallets (plt_code, location_id, status, created_at, updated_at)
select 'PLT-LEGACY-' || lpad(s.id::text, 6, '0'), s.location_id, 'LOADED', now(), now()
from stocks s;

update stocks s
   set pallet_id = p.id,
       inbound_dt = now(),           -- 백필 시점을 임시 LOT 기준으로(실제 입고일 정보 없음)
       lot_no = p.plt_code
  from pallets p
 where p.location_id = s.location_id
   and p.plt_code = 'PLT-LEGACY-' || lpad(s.id::text, 6, '0');

alter table stocks alter column pallet_id set not null;
alter table stocks drop column location_id;
```

- 기존 데모 데이터(27개 렉 로케이션 각각의 재고 1행)를 "로케이션당 파렛트 1장"으로
  기계적으로 백필한다 — 실제 입고 이력이 없으므로 `inbound_dt`는 마이그레이션 실행
  시각을 임시로 쓴다(정직하게 주석에 "실제 입고일이 아니다" 명시).
- 채번 규칙(`PLT-LEGACY-######`)은 임시다 — 9절 열린 질문에서 정식 채번 규칙(신규
  입고분)과 함께 확정한다.

---

## 5. 실행 단계 (미착수 — 9절 승인 후 착수)

### P23-1. 데이터 모델
- [ ] WMS Flyway V7 — `pallets` 테이블, `stocks` 재정의(`pallet_id`/`lot_no`/`inbound_dt`,
      `location_id` 제거), `locations.max_pallet`, `items.unit_weight_kg`(D1~D4, D7, D9)
- [ ] 기존 27개 로케이션 재고 백필 확인 — 마이그레이션 후 `stocks` 행 수·품목별 합계가
      마이그레이션 전과 동일한지 검증 쿼리로 확인

### P23-2. 서비스 계층
- [ ] `StockService.receive()` — 입고 시 파렛트를 새로 만든다(중량 검증 D4, 슬롯 상한 D7)
- [ ] `StockService.issue()` → 파렛트 단위로 재작성 — `issuePallet(palletId, ...)`로
      시그니처 변경, 부분 수량 인자 제거(D5)
- [ ] `OrderService.createOutbound()` — `palletCode` 생략 시 FIFO 자동 선택 로직 추가(D3)
- [ ] 기존 `OutboundOrderCreateRequest`(itemCode+fromLocationCode+quantity) 호환 여부 결정
      — 완전 대체할지, 과거 계약을 당분간 병행할지는 9절에서 확인

### P23-3. API/DTO
- [ ] `StockResponse`에 `palletCode`/`lotNo` 노출
- [ ] 신규 `GET /api/pallets?locationCode=` (로케이션 안의 파렛트 목록 — 대시보드에서
      "이 렉에 파렛트 몇 장" 확인용, 이번 범위에서 대시보드 연동까지는 하지 않는다)

### P23-4. fleet 계약 확장 (D6 — 9절 승인 시에만)
- [ ] `CreateTaskRequest.materialId`(nullable) 추가, `FleetTaskClient.createTask()`가
      `palletCode`를 실어 보내도록 확장
- [ ] fleet 쪽은 받은 값을 지금은 저장만 한다(`FleetOrder`에 이미 있는 필드 활용 여지
      확인 — 신규 컬럼 필요 여부는 착수 시점에 다시 판단)

### P23-5. 검증
- [ ] 입고 → 파렛트 생성 → 출고(파렛트 선택) → 운송 완료 통지 → 파렛트 소진까지
      로컬 e2e로 확인
- [ ] 대시보드 적재율 표시가 무변경임을 확인(D8)
- [ ] 500kg 초과 입고 거부, 슬롯 초과 입고 거부 각각 확인

---

## 6. 리스크 & 롤백

- **가장 위험한 지점은 D2(`stocks` 재정의)다.** `location_id` 제거는 되돌리기 어려운
  변경이다 — `pallet_id`를 nullable로 먼저 추가하고 백필까지 검증한 뒤에 `not null`을
  걸고 `location_id`를 지우는 **2단계 마이그레이션**으로 진행한다(위 D9 SQL 순서 그대로).
  중간에 문제가 발견되면 `location_id` 제거 전 단계에서 멈출 수 있다.
- **D5(부분 피킹 제거)가 기존 데모 시나리오를 깰 가능성** — 지금 출고지시가 로케이션의
  일부 수량만 빼는 흐름을 쓰고 있다면(현재 데모 스크립트/시드 데이터 확인 필요), 파렛트
  단위 전량 이동으로 바뀌면 그 흐름이 더는 성립하지 않는다. 착수 전 기존 데모 스텝을
  다시 확인해야 한다.
- **롤백 수단**: D1~D4, D7, D9는 WMS DB 안에서 닫히므로 마이그레이션 하나를 되돌리는
  것으로 롤백 가능(`stocks.location_id` 복원 포함). D6(fleet 계약)은 별도 커밋으로
  분리해 두면, WMS 쪽만 롤백하고 fleet 변경은 남겨도 하위호환이 깨지지 않는다(nullable
  필드이므로).

---

## 7. 완료 기준

- [ ] 입고지시가 파렛트를 만들고, 그 파렛트가 `plt_code`로 조회된다
- [ ] 출고지시가 특정 파렛트(또는 FIFO 자동 선택된 파렛트)를 통째로 대상으로 하고, 운송
      완료 통지 후 그 파렛트의 재고가 사라진다(부분 잔량이 남지 않는다)
- [ ] 500kg 이상이 되는 입고 수량은 거절된다(단위중량이 설정된 품목 한정)
- [ ] 로케이션의 파렛트 수가 `max_pallet`을 넘는 입고는 거절된다
- [ ] 대시보드의 렉 적재율 표시가 이번 변경 전후로 동일한 값을 보인다(D8 전제 확인)
- [ ] factory/fleet 코드는 한 줄도 안 바뀐다(D6을 실행하지 않기로 했다면) 또는 fleet의
      변경이 하위호환 필드 추가 하나로 끝난다(D6을 실행하기로 했다면)

---

## 8. 이번엔 안 하는 것 (범위 밖 → 후속 항목)

- **부분 피킹 / LOT 분할** — 파렛트에서 일부만 꺼내는 동작(사람이 피킹존에서 하는 일)은
  다루지 않는다. EMMA 600K는 파렛트 통째로만 옮긴다(D5의 근거 그대로). 필요해지면 별도
  "피킹 작업"(로봇이 아니라 사람 또는 별도 피킹로봇의 일)을 추가하는 큰 작업이다.
- **토트(Tote) / 피킹로봇 캐리어 종류** — 실무 표준업무정의서는 "무인지게차(파렛트)"와
  "피킹로봇(토트)"을 별도 로봇으로 나눈다. 이건 P21/P22의 AMR/AGV 축(어디까지 갈 수
  있는가)과는 **다른 축**(무엇을 들 수 있는가 안에서도 파렛트냐 토트냐)이라, 지금 섞으면
  두 축이 혼동된다. 파렛트 축이 자리 잡은 뒤 별도 문서로 다룬다.
- **빈 파렛트 회수·재사용 순환** — 출고로 소진된 파렛트가 다시 `EMPTY` 상태로 창고에
  돌아와 재사용되는 흐름은 다루지 않는다. 지금은 출고 = 그 파렛트 레코드의 수명이 끝나는
  것으로 단순화한다.
- **factory `capacityQty`를 파렛트 수 기준으로 바꾸는 것** — EA 축은 그대로 두고, 파렛트
  슬롯 축(D7)을 WMS 쪽에 독립적으로 추가하는 것으로 끝낸다. 두 축을 하나로 합치는 리팩터는
  factory 스키마를 건드리는 별도 작업이라 범위 밖.
- **로봇 정격(600kg) 배차 검증, 통로폭(§4.3 규격), 회전반경 판정** — 이건 fleet/factory
  쪽 작업(P25 후보)이다. 이번 문서는 **WMS가 파렛트 자체 규정(500kg)을 지키는 것**까지만
  다룬다.
- **엘리베이터 7단계 핸드셰이크 상태기계, 배터리 3단계(80/30%) + 50% 룰** — 사양서에
  근거가 있지만 이번 파렛트 작업과 독립적인 별도 항목(P27 후보)이다.

---

## 9. 열린 질문 — 승인 필요

착수 전 아래 4가지를 확정해야 한다(P21/P22의 관행 그대로 — 결정 없이 시작하지 않는다).

1. **파렛트-품목 1:1 제약**: 지금 1:1로 잠그고 나중에 완화하는 D2안대로 갈지, 처음부터
   `(pallet_id, item_id)` 복합 유니크로 열어 둘지?
2. **D6(fleet 계약 확장)**: 이번 P23 안에서 같이 실행할지(리스크는 작다 — nullable 필드
   추가뿐), 아니면 WMS 쪽만 먼저 끝내고 fleet 쪽 정식 개편(M4형 `orders/create`, P19가
   미뤄둔 항목)이 있을 후속 문서로 넘길지?
3. **D4(중량 검증)**: 지금 같이 넣을지, 로봇 규격 검증을 한데 모으는 별도 문서(P25/P27)로
   미룰지? (지금 넣어도 구현량이 작아 P23 안에 두는 것을 권장하지만, "로봇 규격 검증"을
   한 문서로 몰아보고 싶다면 미루는 것도 합리적이다)
4. **P23-2의 기존 `OutboundOrderCreateRequest` 처리**: 기존 계약(품목+로케이션+수량)을
   완전히 대체할지, 아니면 과거 계약을 당분간 병행하며 내부적으로 파렛트를 자동 선택하는
   어댑터로만 둘지? (후자가 P19의 `TaskController` 호환 어댑터 패턴과 일관된다 — 권장)

권장안: 1번은 **1:1로 잠금**(EMMA 600K 전제에 가장 정직), 2번은 **P23 안에서 같이 실행**
(리스크가 실제로 작다), 3번은 **P23 안에서 같이 실행**(구현량 대비 로봇 규격 서사에
기여가 크다), 4번은 **병행 어댑터**(P19 패턴 재사용, 회귀 위험 최소).
