# simulator

가공 설비 시뮬레이터 (Phase 1 예정).

- 설비 상태(RUNNING/IDLE/DOWN), 사이클 완료, 불량 발생 이벤트를 MQTT로 발행한다.
- 토픽 설계: `factory/{line}/{equipment}/...` (Phase 1에서 확정)
- 발행 주기와 이벤트 볼륨은 oee-service의 이벤트 보존 정책과 함께 설계한다.
