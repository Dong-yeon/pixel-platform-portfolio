# gateway — API Gateway (:9000)

플랫폼의 **단일 진입점이자 인증 관문**. 대시보드와 외부 클라이언트는 이 게이트웨이만
바라보고, 게이트웨이가 인증을 확인한 뒤 경로 접두사로 모듈을 골라 전달한다.

Spring Cloud Gateway(WebFlux 기반). 라우트는 `application.yml`에 선언한다.

## 라우팅

| 요청 | 전달 | 변환 |
|---|---|---|
| `/api/auth/**` | 인증 담당 모듈 (기본 `:9001`) | 변환 없음 |
| `/api/factory/**` | pixel-factory `:9001` | `/api/factory/robots` → `/api/robots` |
| `/api/fleet/**` | pixel-fleet `:9002` | `/api/fleet/robots` → `/api/robots` |
| `/ws/fleet/**` | pixel-fleet `:9002` | 변환 없음 (STOMP/SockJS) |
| `/ws/factory/**` | pixel-factory `:9001` | 변환 없음 (STOMP/SockJS) |

모듈은 플랫폼 접두사를 모르고 자기 경로(`/api/robots`)만 안다. 접두사 제거는
`RewritePath` 필터가 담당한다.

환경변수로 대상 주소를 바꿀 수 있다: `AUTH_MODULE_URI`, `MODULE_FACTORY_URI`,
`MODULE_FLEET_URI`, `MODULE_FLEET_WS_URI`, `MODULE_FACTORY_WS_URI` (배포 시 사용).

> **WebSocket 경로에 모듈명이 들어간다.** 예전엔 `/ws/**`를 통째로 fleet으로 보냈는데,
> 그러면 factory가 WebSocket을 열 자리가 없다(모듈이 늘어나면 더 막힌다).
> 서버 쪽 STOMP 엔드포인트도 같은 경로다 — 한쪽만 바꾸면 실시간이 **조용히** 끊긴다.

## 중앙 인증 (P6)

**발급은 모듈이, 검증은 게이트웨이가.** 게이트웨이는 사용자 저장소를 갖지 않으므로
토큰을 발급할 수 없다. 그래서 로그인(`/api/auth/login`)은 신원 담당 모듈로 넘기고
(`AUTH_MODULE_URI`, 현재 pixel-factory가 `users` 마스터를 갖고 있다),
게이트웨이는 이후 모든 요청의 토큰을 **관문에서 검증**한다.

`AuthenticationGlobalFilter`가 하는 일:

1. **검증** — `/api/**`의 Bearer 토큰을 확인하고, 없거나 유효하지 않으면 여기서 401을 낸다.
   미인증 트래픽이 모듈에 닿지 않고, 인증 정책이 한 곳에 모인다.
2. **신원 전달** — 통과한 요청에 `X-Auth-User` / `X-Auth-Role`을 붙인다.
3. **스푸핑 차단** — 클라이언트가 보낸 `X-Auth-*`는 **항상 지운다**.

인증 없이 통과하는 것: `/api/auth/**`, `/ws/**`, `/actuator/**`, `OPTIONS`(CORS preflight),
그리고 `/api/`로 시작하지 않는 경로(대시보드 정적 자원).

```bash
curl -i http://localhost:9000/api/fleet/robots                       # 401 (토큰 없음)
curl -i -H "X-Auth-Role: ADMIN" http://localhost:9000/api/fleet/robots  # 401 (헤더 위조 무효)
```

**모듈도 자체 JWT 필터를 그대로 유지한다(방어 심층).** 게이트웨이를 우회해 9001/9002에
직접 붙어도 인증이 필요하다 — 헤더만 믿으면 모듈이 무방비가 된다.

### 서명 키

게이트웨이·pixel-factory·pixel-fleet이 **같은 `PLATFORM_JWT_SECRET`**을 써야 한다.
하나라도 다르면 게이트웨이가 통과시킨 토큰을 모듈이 거부해 전 화면이 401이 된다.
(P6 이전에는 모듈마다 다른 키를 써서, 대시보드가 모듈마다 따로 로그인하고
토큰을 2개 보관하고 있었다.)

## 실행

```powershell
.\gradlew.bat bootRun     # :9000
```

인프라와 두 모듈이 떠 있어야 라우팅이 의미가 있다(루트 README 참고).

```bash
curl http://localhost:9000/actuator/health          # 게이트웨이 자체
curl http://localhost:9000/api/factory/health       # → :9001
curl http://localhost:9000/api/fleet/health         # → :9002
```

## 주의해서 볼 두 가지 (실제로 겪은 함정)

**1. WebSocket 라우트의 uri는 `http://`로 둔다.**
`ws://`로 두면 업그레이드 요청만 받아서, SockJS가 먼저 호출하는 `/ws/info`(일반 HTTP
GET)와 XHR 폴백이 **400**으로 거부된다. `http://`로 두면 게이트웨이가 `Upgrade` 헤더를
보고 자동으로 ws로 전환하면서 일반 HTTP도 통과시킨다.

**2. CORS 헤더 중복을 제거한다.**
하위 모듈도 CORS 헤더를 붙이는 경우가 있어(fleet의 `WebSocketConfig`) 게이트웨이 것과
합쳐지면 `Access-Control-Allow-Origin`이 2개가 된다. 브라우저는 이 경우 요청을 **거부**한다
(curl은 CORS를 검사하지 않아 통과하므로 curl 테스트만으로는 못 잡는다).
`default-filters`의 `DedupeResponseHeader ... RETAIN_FIRST`로 하나만 남긴다.

## TODO

- CORS `allowedOriginPatterns: "*"` → 배포 전 실제 대시보드 오리진으로 제한
- `/ws/**`는 아직 인증 없이 통과한다. SockJS 핸드셰이크에 Authorization 헤더를 실을 수
  없어서인데, 토큰을 STOMP CONNECT 프레임에 담아 검증하면 막을 수 있다.
- 권한(Role) 기반 접근 제어는 아직 모듈 몫이다. 게이트웨이는 "인증됐는가"만 본다.
- 사용자 저장소가 모듈마다 따로 있다(`users` 테이블 2벌). 전용 인증 서비스로 분리하면
  `AUTH_MODULE_URI`만 그쪽으로 돌리면 된다 — 지금 구조가 그 갈아끼움을 전제로 한다.
