# gateway — API Gateway (:9000)

플랫폼의 **단일 진입점**. 대시보드와 외부 클라이언트는 이 게이트웨이만 바라보고,
게이트웨이가 경로 접두사로 모듈을 골라 전달한다.

Spring Cloud Gateway(WebFlux 기반). 라우트는 `application.yml`에 선언한다.

## 라우팅

| 요청 | 전달 | 변환 |
|---|---|---|
| `/api/factory/**` | pixel-factory `:9001` | `/api/factory/robots` → `/api/robots` |
| `/api/fleet/**` | pixel-fleet `:9002` | `/api/fleet/robots` → `/api/robots` |
| `/ws/**` | pixel-fleet `:9002` | 변환 없음 (STOMP/SockJS) |

모듈은 플랫폼 접두사를 모르고 자기 경로(`/api/robots`)만 안다. 접두사 제거는
`RewritePath` 필터가 담당한다.

환경변수로 대상 주소를 바꿀 수 있다: `MODULE_FACTORY_URI`, `MODULE_FLEET_URI`,
`MODULE_FLEET_WS_URI` (배포 시 사용).

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
- JWT 중앙 검증(P6): 지금은 각 모듈이 검증하고, 게이트웨이는 라우팅만 한다
