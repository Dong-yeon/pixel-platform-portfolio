# 플랫폼 백로그

범위 밖 아이디어를 여기에 모은다. 구현 전에 먼저 기록한다.
(모듈별 백로그는 각 모듈의 `docs/BACKLOG.md`)

## 통합 공장 지도 ✅ 1차 구현 완료 (대시보드 매핑 방식)

**통합 현황 탭에서 PixelFactory와 PixelFleet을 하나의 공장 평면도로 보여준다.**

지금은 두 모듈이 KPI 카드와 이벤트 타임라인으로만 합쳐져 있고, 지도는 PixelFleet
탭에만 있다. 같은 평면도 위에 설비와 AMR을 함께 그리면 "하나의 공장을 두 시스템이
관제한다"는 플랫폼의 요지가 한 화면에 드러난다.

- **설비** — 사각형, 상태별 색(RUNNING 초록 / IDLE 회색 / DOWN 빨강 / QUALITY_HOLD 주황)
- **AMR** — 원형, 실시간 이동 (지금 구현 그대로)
- **작업 흐름** — 진행 중인 운송 작업의 출발→도착을 선으로 연결하면 물류 흐름이 보인다

### 필요한 것

1. **설비에 좌표 부여** — 현재 `Equipment`에는 위치가 없다. 선택지:
   - (a) `equipments` 테이블에 `pos_x`, `pos_y` 컬럼 추가 (마이그레이션 필요)
   - (b) 대시보드/게이트웨이 쪽 레지스트리로만 매핑 (DB 변경 없음, 빠름)
2. **좌표계 통일** — fleet의 32×24 평면도를 플랫폼 표준으로 삼는다. 지금 노드 좌표가
   `control-service/LocationRegistry`, `robot-sim/NodeMap`, `dashboard/types.ts` 세 곳에
   중복돼 있는데(아래 항목), 통합 지도를 만들면 단일화 필요성이 더 커진다.
3. **설비와 노드의 관계 정의** — STATION-A가 곧 CNC-01이 있는 자리인지, 인접한 적재
   지점인지. 서사를 정하면 좌표 배치가 자연스러워진다.

> **(b)로 1차 구현 완료** — `platform/dashboard/src/components/UnifiedMap.tsx`,
> 좌표는 `types.ts`의 `EQUIPMENT_POSITIONS`. 남은 것은 (a) 정식화:
> `equipments`에 `pos_x`/`pos_y`를 추가하고 서버가 내려주도록 바꾸면 설비를 늘려도
> 대시보드를 고칠 필요가 없어진다. 지금은 설비를 추가하면 좌표 매핑도 함께 넣어야 한다.

## 좌표 정의 단일화

노드 좌표가 세 곳에 중복돼 있어 반드시 함께 고쳐야 한다.
- `modules/pixel-fleet/services/control-service/.../location/LocationRegistry.java`
- `modules/pixel-fleet/robot-sim/.../map/NodeMap.java`
- `platform/dashboard/src/types.ts`

서버가 소유하고 API로 내려주거나(모듈 공용 계약), `shared/`로 올리는 방안.

## 그 외

- **PixelFactory 실시간 push** — 현재 실시간은 fleet만 제공. factory도 WebSocket을 태우면
  통합 현황이 양쪽 다 살아 움직인다.
- **OEE 계산 엔진** — 가동률×성능×품질. PixelFactory 로드맵 Phase 2가 미완이라 대시보드에
  품질률만 나온다.
- **이벤트 보존 정책** — `fleet_events`/`factory_events`가 무한 증가. 배포 환경 비용 직결.
- **CI** — GitHub Actions로 모듈별 빌드/타입체크 (경로 필터로 변경된 모듈만).
- **P6 게이트웨이 중앙 인증** — 지금은 모듈이 각자 JWT 검증, 대시보드가 토큰 2개 보관.
