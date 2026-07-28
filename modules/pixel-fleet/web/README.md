# web — 실시간 관제 대시보드

React + TypeScript + Vite. 관제 서버(control-service)와 **REST + STOMP/WebSocket**으로만
통신한다(컴포저블). JWT 로그인 후 공장 지도·로봇 상태·작업 조작·이벤트 타임라인을
실시간으로 보여준다.

## 화면

- **공장 지도** — SVG(32×24)에 노드와 로봇 마커. 상태별 색, 배터리 표시, 위치는 WS로 실시간 이동.
- **로봇 패널** — 상태 배지 + 배터리 바 + 좌표.
- **작업** — 생성 폼(코드·출발/도착 노드·우선순위) + 작업 목록(상태·배정 로봇·재시도).
  작업 이벤트가 오면 목록을 자동 갱신 → "조작하면 즉시 반영".
- **이벤트 타임라인** — `/topic/events` 실시간 스트림.

## 실행 (개발)

관제 서버(:9002) + 시뮬레이터 + 인프라가 떠 있는 상태에서:

```bash
cd web
npm install
npm run dev        # http://localhost:9100
```

Vite dev 서버가 `/api`·`/ws`를 :9002로 프록시하므로 CORS 설정이 필요 없다.
데모 계정: `admin` / `dispatcher` / `operator` · 비밀번호 `password`.

## 빌드

```bash
npm run build      # dist/  (typecheck는 npm run typecheck)
```

프로덕션에서는 `dist/`를 control-service가 같은 오리진으로 서빙할 예정(Railway 배포 단계).

## 구조

| 파일 | 역할 |
|---|---|
| `src/api.ts` | REST 클라이언트(JWT), `ApiError` |
| `src/useFleetSocket.ts` | STOMP/SockJS 구독 훅(`/topic/robots`·`/topic/events`) |
| `src/components/Dashboard.tsx` | 초기 로드 + WS 병합, 작업 이벤트 시 목록 리페치 |
| `src/components/FactoryMap.tsx` | SVG 지도·로봇 마커 |
| `src/components/{RobotPanel,TaskPanel,EventTimeline,LoginView}.tsx` | 각 패널 |
| `src/types.ts` | 도메인 타입 + 노드 좌표(서버 LocationRegistry와 일치) |
