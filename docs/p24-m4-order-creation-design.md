# P24 설계 문서 — WMS→fleet을 M4형 다단 스텝 `orders/create`로 전환

> 상태: **구현 + 실기동 검증 완료(2026-08-25).** `docs/p20~p23-*.md`와 같은 형식.
>
> 선행 문서: P19(fleet를 M4 모양으로 — 스텝 기반 주문 엔진) · P23(파렛트 단위 재고,
> fleet 계약에 `materialId` 추가).

---

## 0. 범위 재확정 — 원래 P24 계획은 이미 끝나 있었다

대화 앞부분에서 P24를 "`OrderStep`에 `action`(MOVE/LOAD/UNLOAD)과 멱등키(`external_id`)를
추가"로 제안했다. **P23 설계 중 코드를 다시 확인해 보니 이건 이미 P19가 끝내 놨다** —
`OrderStep.forLoad`/`forUnload`(M4의 `binTask` 개념), `FleetOrder.externalId`(M4의
`ref_uuid`), `stepFixed`(M4의 봉인)가 전부 존재한다. 원래 P24로 잡았던 일감은 없다.

**그런데 `docs/BACKLOG.md` P19 항목이 스스로 미뤄 둔 게 하나 있다**:

> 의도적으로 미룬 것 — **M4형 `orders/create`(steps 배열 입력, add/update/delete-steps)** —
> 생성은 계속 `TaskController`(호환 어댑터) 전담. `OrderController`는 조작자 동사만 다룬다.

확인해 보면 지금 `OrderController`(`/api/orders`)는 `GET`·`suspend`·`cancel`·`complete`·
`retry-failed`만 있고 **생성 엔드포인트가 없다.** `OrderService.create(...)`라는 M4형 메서드는
이미 완성돼 있는데, 그걸 부르는 진짜 HTTP 진입점이 `TaskController`(구식 2필드 어댑터)
하나뿐이다 — WMS도 이 어댑터를 거쳐 fleet과 대화한다.

**이번 문서의 P24는 이 자리를 채운다**: fleet에 `POST /api/orders`(M4형, 스텝 배열 입력)를
새로 열고, WMS가 그 엔드포인트로 갈아탄다. `TaskController`는 지우지 않는다 — 대시보드의
수동 작업 생성 UI(`TaskPanel.tsx`)가 아직 `/api/tasks`를 쓴다(확인 완료). 소비자가 전부
옮겨간 뒤에야 삭제 대상이다(P19 문서 자체가 이미 그렇게 적어 뒀다).

---

## 1. 목표 구조

```
                    ── 지금(P23까지) ──                    ── P24 이후 ──

WMS OrderService          WMS OrderService
  createOutbound()          createOutbound()
        │                         │
        ▼                         ▼
FleetTaskClient            FleetTaskClient (구현만 교체, 이름 그대로)
  POST /api/tasks            POST /api/orders   ← 신규
  {taskCode,                 {externalId, steps:[
   originNode,                 {location, forLoad:true},
   destinationNode,            {location, forUnload:true}],
   priority, materialId}       priority(int), stepFixed:true, materialId}
        │                         │
        ▼                         ▼
TaskController              OrderController.create()  ← 신규
  (호환 어댑터, 무변경)         orderService.create(...)  (기존 메서드 그대로)

대시보드 TaskPanel — 그대로 TaskController 사용(무변경, 이번 범위 밖)
```

---

## 2. 핵심 원칙 재확인

| 원칙 | 이 설계가 지키는 방법 |
|---|---|
| 컴포저블 — 기존 소비자 무변경 | `TaskController` 삭제 안 함. 대시보드는 이번 변경을 전혀 모른다 |
| 전례 재사용 | `OrderService.create(...)`(P19)·`OrderResponse`/`OrderStepResponse`(P19)가 이미 있다 — 새로 만드는 건 얇은 컨트롤러 진입점 하나뿐 |
| DB per module | fleet DB 스키마 변경 없음(P23의 `material_id` 컬럼을 그대로 씀). WMS DB도 무변경(같은 `outbound_orders.task_code` 계약 유지) |

---

## 3. 확정 결정

### D1. `POST /api/orders` 신설 — `OrderController`에 추가

```java
public record CreateOrderRequest(
        String externalId,
        @NotEmpty List<StepRequest> steps,
        Integer priority,      // null이면 1(NORMAL)
        Boolean stepFixed,     // null이면 true
        String materialId
) {
    public record StepRequest(@NotBlank String location, boolean forLoad, boolean forUnload) {}
}
```

`OrderController`가 `OrderCodeGenerator`를 새로 주입받아(`TaskController`와 같은 방식)
`orderService.create(orderCodeGenerator.next(), request.externalId(), steps, priority,
stepFixed, request.materialId())`를 그대로 호출한다 — **새 서비스 로직 없음**, 이미 있는
메서드에 진입점만 하나 더 낸다.

응답은 기존 `OrderResponse`를 재사용한다. `materialId`가 빠져 있어 D2에서 채운다.

### D2. `OrderResponse`에 `materialId` 노출

`TaskResponse`는 P23에서 이미 노출했다(D6). `OrderResponse`(M4 모양 뷰)에는 빠져 있었다 —
같이 채운다.

### D3. WMS `FleetTaskClient`가 새 엔드포인트로 갈아탄다

클래스·메서드 이름은 그대로 둔다("fleet에 운송 작업을 요청한다"는 역할은 안 바뀐다) —
바디만 M4형 스텝 배열로 바꾼다:

```java
Map<String, Object> body = Map.of(
    "externalId", taskCode,
    "steps", List.of(
        Map.of("location", originNode, "forLoad", true, "forUnload", false),
        Map.of("location", destinationNode, "forLoad", false, "forUnload", true)),
    "priority", priorityValue(priority),   // "NORMAL" 등 문자열 → 0~3 정수 (fleet 관례)
    "stepFixed", true
);
// materialId는 null이 아닐 때만 넣는다(P23 D6과 같은 원칙)
```

- 우선순위 문자열→정수 변환은 fleet `CreateTaskRequest.priorityValue()`와 **정확히 같은
  매핑**을 WMS 쪽에 둔다(LOW=0/NORMAL=1/HIGH=2/URGENT=3) — 스케일이 하나로 통일된다.
- 응답 파싱: 지금처럼 `taskCode`(=WMS가 보낸 `externalId`)를 그대로 돌려준다 — fleet이
  내부적으로 어떤 `orderCode`를 발급했는지는 WMS의 관심사가 아니다(완료 통지는
  `externalId`로 오므로 이 값만 있으면 된다). 응답 바디 파싱 자체가 필요 없어진다(전에는
  `data.path("taskCode")`를 읽었지만, 이제 실패 여부(HTTP 상태)만 보면 충분하다).

### D4. `TaskController`/`/api/tasks`는 그대로 둔다

대시보드 `TaskPanel.tsx`가 여전히 이 경로로 수동 작업을 만든다(확인 완료,
`modules/pixel-fleet/web/src/api.ts:51`). 이번 범위는 **WMS 쪽 소비자 전환만**이다 —
대시보드까지 옮기는 건 별도 항목(범위 밖, 5절).

---

## 4. 실행 단계

- [ ] fleet: `OrderController`에 `POST /api/orders` 추가(D1), `OrderCodeGenerator` 주입
- [ ] fleet: `OrderResponse`에 `materialId` 추가(D2)
- [ ] WMS: `FleetTaskClient.createTask()` 바디를 M4형 스텝 배열로 교체(D3)
- [ ] 컴파일 확인 (양쪽 모듈)
- [ ] e2e 재확인: WMS 출고지시 → fleet에 `/api/orders`로 주문 생성 → `GET /api/orders`에서
      스텝 2개(forLoad/forUnload)·`materialId` 확인 → MQTT 완료 통지 → 재고 차감까지
      P23과 같은 흐름이 새 엔드포인트로도 그대로 도는지 확인

---

## 5. 이번엔 안 하는 것

- **대시보드 `TaskPanel`을 `/api/orders`로 옮기기** — 수동 작업 생성 UI는 아직 2필드
  모양(출발/도착)이 더 단순해서 유용하다. 다단 스텝 UI로 바꾸는 건 별도 UX 작업.
- **`TaskController` 삭제** — 대시보드가 아직 쓰고 있어 지울 수 없다.
- **`add/update/delete-steps`(미봉인 주문에 스텝 추가)** — WMS는 항상 2스텝 봉인 주문만
  만든다(파렛트 하나를 한 번에 옮긴다, P23 D5). 이 기능이 필요해지는 시나리오가 아직 없다.
- **`ref_uuid` 충돌 검사(fleet 레벨)** — WMS가 이미 자기 DB에서 `orderNo` 유일성을
  보장한다(`existsByOrderNo`). fleet 레벨에서 또 검사하는 건 지금은 중복이다.
