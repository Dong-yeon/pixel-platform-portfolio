# 인증 경계 — 뭐가 왜 열려 있는가

P16(인증 경계 정리)의 마지막 조각. "열어 둔 것을 의식적으로 열었다"는 기록이 방어의
절반이라는 그 문서의 원칙 그대로 — 아래 표에 없는 경로는 전부 인증이 필요하다는 뜻이다.

## 서비스별 permitAll 경로

| 서비스 | 열린 경로 | 이유 |
|---|---|---|
| gateway | `/api/auth/**`, `/actuator/**`, `OPTIONS` 전체, `/ws`·`/ws/**`, `/api/`로 시작 안 하는 모든 경로(대시보드 정적 자원) | 로그인 자체는 인증 전 단계라 열어야 한다. WS는 SockJS 핸드셰이크가 `Authorization` 헤더를 못 실어서 게이트웨이 레벨에선 통과시키고, 실제 검증은 모듈이 STOMP CONNECT 프레임에서 한다(아래). |
| oee-service (factory) | `POST /api/auth/login`, `/api/health`, `/actuator/**`, swagger 3종, `/ws/**` | 로그인 창구가 이 모듈(플랫폼 유일). actuator는 Railway에서 퍼블릭 도메인이 없어(게이트웨이만 연다) 프라이빗 네트워크 전제로 열었다. **`GET /api/layout`은 P16 WP2로 닫혔다** — 예전엔 fleet이 M2M 인증 없이 읽어야 해서 열려 있었다. |
| control-service (fleet) | `POST /api/auth/login`, `/api/health`, `/actuator/**`, swagger 3종, `/ws/**`, `/ws-test.html`(개발용 수동 테스트 페이지) | oee-service와 같은 이유. |
| wms-service | `/api/health`, `/actuator/**`, swagger 3종, **`GET /api/items/**`** | actuator 이유 동일. `GET /api/items/**`는 factory OEE 계산기가 표준CT를 읽을 걸 전제로 열어 뒀다(D6) — **다만 2026-09-02 현재 factory가 이 경로를 아직 호출하지 않는다**, 즉 지금은 쓰이지 않는 채로 열려만 있는 상태. D6 작업 시 이 줄을 다시 본다. |
| qms-service | `/api/health`, `/actuator/**`, swagger 3종 | QMS는 로그인 창구가 아니다 — 토큰을 발급하지 않고 검증만 한다(같은 `PLATFORM_JWT_SECRET`). |

나머지 모든 경로는 `anyRequest().authenticated()`로 떨어진다 — 유효한 플랫폼 JWT(사용자
토큰이든 서비스 토큰이든, 아래 참고)만 있으면 통과하고, role별 세부 제어는 각 컨트롤러의
`@PreAuthorize`가 담당한다.

## `/ws/**` — 왜 두 단계인가

SockJS 핸드셰이크(HTTP 폴링/업그레이드)에는 `Authorization` 헤더를 실을 방법이 없다.
그래서 HTTP 레벨(게이트웨이·모듈 `SecurityConfig` 둘 다)에서는 `/ws/**`를 permitAll로
열어 두고, 실제 인증은 그 위 STOMP 프로토콜의 **CONNECT 프레임**에서 한다 —
`StompAuthChannelInterceptor`(factory·fleet 각자 신설, `shared`를 게이트웨이가
의존할 수 없고 wms/qms엔 WebSocket 자체가 없어서 모듈별로 둠)가 CONNECT 프레임의
`Authorization` 네이티브 헤더를 읽어 검증하고, 없거나 무효하면 연결 자체를 끊는다.
대시보드 `usePlatformSocket.ts`가 CONNECT에 `connectHeaders`로 토큰을 싣는다.

## M2M(서비스 간) 인증 — 4개 방향

전부 같은 패턴이다: 호출하는 쪽 모듈에 `ServiceTokenProvider`를 두고, 플랫폼이 공유하는
`PLATFORM_JWT_SECRET`으로 `svc-{모듈명}` 주체 + `role=ADMIN`의 토큰을 셀프 발급해
`Authorization: Bearer`로 실어 보낸다(만료 10분 전 자동 갱신, 캐시). 받는 쪽은 이 토큰을
일반 로그인 토큰과 **구분하지 않는다** — 서명이 유효한 플랫폼 JWT면 그냥 인증된 요청으로
취급한다("서비스" 전용 role/claim은 없다). 그래서 별도 시크릿·발급자·클레임 체계가
따로 없고, `PLATFORM_JWT_SECRET` 하나가 어긋나면 네 방향 모두 한꺼번에 401을 낸다.

| 방향 | 호출자의 `ServiceTokenProvider` | 대상 | 시점 |
|---|---|---|---|
| QMS → factory | `com.pixelqms.factory.ServiceTokenProvider`(`svc-qms`) | 설비 상태 전환(MRB 홀드/릴리즈) | P14 |
| WMS → fleet | `com.pixelwms.fleet.ServiceTokenProvider`(`svc-wms`) | 운송 작업 생성(`POST /api/orders`) | P13/P24 |
| fleet → factory | `com.pixelfleet.location.ServiceTokenProvider`(`svc-fleet`) | 평면도 조회(`GET /api/layout`) | P16 WP2 |
| factory → WMS | 없음 | 표준CT 조회 예정(D6) | 미착수 |

게이트웨이는 이 네 방향 어디도 경유하지 않는다 — 전부 Railway 프라이빗 네트워크 안에서
모듈이 서로 직접 붙는다(`*.railway.internal`). 게이트웨이를 거치려면 모듈→모듈 라우트를
새로 뚫어야 하는데, 지금까지의 세 사례가 전부 직접 호출 방식이라 그 관례를 그대로
따랐다 — 프라이빗 네트워크 격리가 이미 1차 방어선이라는 전제 위에서다.

**"진짜" M2M이 아니다.** 모든 `ServiceTokenProvider`의 Javadoc이 같은 문구를 남긴다 —
"진짜 M2M 인증이 생기면 이 클래스만 갈아끼운다." 지금은 각 모듈이 공유 서명 키로
자기 자신을 셀프 로그인시키는 방식이라, `PLATFORM_JWT_SECRET`을 손에 넣으면 누구든
아무 모듈 행세를 할 수 있다. 별도 서비스 계정·상호 TLS·OAuth client-credentials 같은
진짜 서비스 아이덴티티는 범위 밖으로 남겨 뒀다 — 배포 환경 규모(단일 Railway 프로젝트,
프라이빗 네트워크 격리가 사실상의 신뢰 경계) 대비 과한 투자라고 판단했다.

## MQTT

`allow_anonymous false` + `password_file`(컨테이너 기동마다 `MQTT_USERS` 환경변수로
생성 — 이미지·git에 평문 없음) + `acl_file`. ACL은 기존 토픽 네임스페이스 관례를 그대로
경계로 쓴다 — `wms-service`/`qms-service`는 자기 도메인만 **읽기 전용**(`fleet/tasks/#`,
`factory/quality/#`), 나머지(`factory-sim`/`oee-service`/`control-service`/`robot-sim`)는
자기 도메인 안에서 읽기·쓰기 전부. 프라이빗 네트워크 격리가 1차 방어선, 계정 인증이
2차 방어선이다(1차만 믿었다가 `railway domain` 오조작으로 한 번 뚫릴 뻔한 적이 있다 —
자세한 경위는 `docs/deploy-railway.md` "반드시 알아둘 점" 3번).

## CORS

게이트웨이 `globalcors`, factory·fleet의 `WebSocketConfig`(SockJS 핸드셰이크는
globalcors를 안 거친다) 세 곳 전부 같은 `DASHBOARD_ORIGIN` 환경변수로 대시보드 오리진만
허용 — 세 곳 중 하나라도 값이 다르거나 빠지면 오리진 제한이 조용히 뚫린다.

## 알려진 구멍 (의식적으로 열어 둔 것)

- **WMS `GET /api/items/**`가 지금 아무도 안 부르는데 열려 있다.** D6(factory→WMS 표준CT
  연결) 작업 시 이 줄을 다시 본다 — M2M으로 닫을지, 품목 마스터는 민감정보가 아니니
  그대로 둘지는 그때 결정한다.
- **로그인 창구가 하나(factory)뿐이다.** factory가 죽으면 아무도 새로 로그인 못 한다
  (이미 로그인된 세션은 게이트웨이·모듈이 독립적으로 검증하므로 영향 없음).
