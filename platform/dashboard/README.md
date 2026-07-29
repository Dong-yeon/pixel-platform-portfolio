# dashboard — 통합 관제 대시보드 (:9200)

React + TypeScript + Vite. **게이트웨이(9000)만 바라본다** — 모듈(9001/9002)에 직접 붙지 않는다.

## 화면

| 탭 | 내용 |
|---|---|
| **통합 현황** | 두 모듈 KPI 요약 + **통합 이벤트 타임라인**(`[F]` 공장 / `[A]` 물류 시간순 병합). 카드 클릭 시 해당 모듈로 이동 |
| **PixelFactory** | 설비 현황(상태·라인·목표 C/T), 작업지시(진척바·시작 조작), 이벤트 |
| **PixelFleet** | 공장 지도(로봇 실시간 이동), 로봇 패널, 운송 작업 생성/목록, 이벤트 |

## 실행

인프라 + 게이트웨이 + 두 모듈이 떠 있어야 한다(루트 README 참고).

```bash
npm install
npm run dev        # http://localhost:9200
```

dev 서버가 `/api`·`/ws`를 게이트웨이(9000)로 프록시하므로 브라우저에는 단일 오리진으로 보인다.
데모 계정: `admin` / `operator` · 비밀번호 `password`.

```bash
npm run build      # dist/
npm run typecheck
```

## 구조

| 파일 | 역할 |
|---|---|
| `src/api.ts` | 게이트웨이 클라이언트. `/api/factory/**`·`/api/fleet/**` 네임스페이스 분리 |
| `src/Dashboard.tsx` | 초기 로드 + WS 병합, 탭 전환, 작업 이벤트 시 목록 리페치 |
| `src/useFleetSocket.ts` | STOMP/SockJS 구독(`/topic/robots`·`/topic/events`) |
| `src/components/OverviewView.tsx` | 크로스 모듈 KPI + 통합 타임라인 |
| `src/components/factory/*` | 설비·작업지시 패널 (신규) |
| `src/components/fleet/*` | 지도·로봇·작업 패널 (pixel-fleet/web에서 흡수) |
| `src/components/EventTimeline.tsx` | 두 모듈 공용 타임라인 |

## 인증 (임시)

모듈이 각자 JWT를 발급하므로 로그인 시 **두 모듈에 각각 인증**하고 토큰 2개를 보관한다
(`pp_token_factory`, `pp_token_fleet`). 데모 계정은 양쪽에 모두 존재한다.
**P6에서 게이트웨이 중앙 인증으로 바뀌면 토큰 하나로 합쳐진다.**

## 알려진 제약

- 실시간 push는 현재 **pixel-fleet만** 제공한다. factory 지표는 조회 시점 기준이며,
  작업지시를 조작하면 해당 목록만 다시 불러온다.
- `modules/pixel-fleet/web/`은 이 대시보드로 흡수되었다(D3 결정). 모듈 단독 개발용으로
  남아 있으나 플랫폼에서는 이 대시보드를 쓴다.
