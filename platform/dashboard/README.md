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

## 인증

**로그인 한 번, 토큰 하나**(`pp_token`). `/api/auth/login`으로 받은 토큰이 모든 모듈에서
통한다 — 게이트웨이가 모듈 앞에서 이 토큰을 검증하고, 모듈들이 같은 서명 키를 쓴다.
자세한 구조는 `platform/gateway/README.md` 참고.

401이 오면 토큰을 지우고 로그인 화면으로 되돌린다. 그러지 않으면 토큰 만료(2시간) 시
모든 조회가 조용히 실패해 화면이 텅 빈 채로 남는다.

## 알려진 제약

- 실시간은 **모듈마다 별개 연결**이다(`/ws/fleet`, `/ws/factory`). 한쪽이 재기동돼도
  다른 쪽은 살아 있고, 상단 표시가 `일부 연결 (물류만)`처럼 어느 쪽이 끊겼는지 알려준다.
  둘 다 붙어야 `실시간 연결됨`이다 — 절반이 멈춘 걸 모른 채 보는 게 더 나쁘다.
- **OEE는 서버가 계산한 값을 그대로 쓴다.** 대시보드에서 다시 계산하지 않는다.
  설비 상태·이벤트는 즉시 push, OEE는 5초 주기 push(구간 전체를 재집계해야 나오는 값이라
  이벤트마다 계산하면 DB만 두드린다).
- `modules/pixel-fleet/web/`은 이 대시보드로 흡수되었다(D3 결정). 모듈 단독 개발용으로
  남아 있으나 플랫폼에서는 이 대시보드를 쓴다.
