# v2 백로그

1차 범위(가공 라인 OEE)에서 제외한 항목. Phase 0 정리 시 코드에서 제거했으며,
다시 도입할 때 이 문서를 근거로 복원한다.

## 물류 / AGV / 창고

- `FactoryEventType`: `MATERIAL_MOVE_REQUESTED`, `AGV_MOVE_STARTED`, `AGV_ARRIVED`,
  `FINISHED_GOODS_MOVE_STARTED`
- `SourceType`: `MATERIAL_MOVE`, `AGV`, `WAREHOUSE`
- `TargetType`: `AGV`, `WAREHOUSE`
- `WorkOrderStatus`: `MATERIAL_REQUESTED`, `MATERIAL_MOVING`
- `UserRole`: `WAREHOUSE_OPERATOR`

## QMS 확장 프로세스 (MRB 등)

- `FactoryEventType`: `MRB_CREATED`, `QMS_ACTION_STARTED`, `QMS_ACTION_COMPLETED`
- `SourceType`: `MRB`, `QMS`
- `UserRole`: `QMS_MANAGER`

## 게임 플레이버 (재도입 안 함 — 기록용)

- `FactoryEventType`: `PIGEON_TO_QMS`, `PIGEON_TO_OPERATOR`
  → 중립 명칭 `NOTIFICATION_SENT`로 대체됨
- `TargetType`: `QMS_BUILDING`, `FACTORY_MAP` (픽셀 맵 오브젝트 개념)
  → 설비/라인 식별용 `EQUIPMENT`, `LINE`으로 대체됨

게임 메커닉(점수/레벨/보상)은 CLAUDE.md 절대 원칙 3에 따라 재도입하지 않는다.
