# web (예정)

React 실시간 관제 대시보드. Phase 3 에서 구현한다.

## 범위 (Phase 3)

- 공장 지도(2D canvas/SVG) 위에 로봇 마커 실시간 이동
- 로봇별 상태·배터리 패널
- 운송 작업 목록 / 작업 생성·조작 UI (조작 → 대시보드 즉시 반영)
- fleet 이벤트 타임라인 (장애 로그 포함)

## 연결

- REST: `http://localhost:8082/api/**` (JWT 로그인 후)
- 실시간: WebSocket (Phase 2 에서 서버측 push 구현 후 연결)
