# Railway 배포 가이드

Pixel Platform을 Railway에 올리는 절차. **배포 실행에는 동연님의 Railway 계정 인증이
필요**하므로, 이 문서는 그대로 따라 하면 되도록 설정값을 전부 적어 두었다.

## 서비스 구성 (7개 + 플러그인 2개)

```
[퍼블릭]  gateway (+대시보드 번들)  ← 유일하게 도메인을 여는 서비스
             │
[프라이빗] ├─ pixel-factory
           ├─ pixel-fleet
           ├─ mosquitto
           ├─ robot-sim     (AMR 6대)
           └─ factory-sim   (설비 8대)
[플러그인] Postgres · Redis
```

**대시보드는 게이트웨이 이미지에 함께 들어간다.** 퍼블릭 서비스가 하나로 줄고,
화면과 API가 같은 오리진이라 CORS 설정이 필요 없다.

## 0. 준비

GitHub 저장소(`Dong-yeon/pixel-platform`)를 Railway 프로젝트에 연결한다.
서비스마다 **Root Directory**를 다르게 지정해 같은 저장소에서 여러 서비스를 만든다.

## 1. 플러그인 추가

| 플러그인 | 용도 |
|---|---|
| PostgreSQL | 두 모듈의 DB (아래에서 DB 2개로 나눈다) |
| Redis | pixel-fleet 라이브 상태 + Pub/Sub |

### Postgres에 두 번째 DB 만들기 (1회)

로컬과 동일하게 **DB per module**을 유지한다. Railway Postgres 플러그인은 DB를 하나만
주므로, fleet용 DB와 롤을 한 번 만들어 준다. Railway의 Postgres → *Data* 탭이나
`psql`로 접속해 실행:

```sql
create role fleet with login password '<원하는-비밀번호>';
create database pixelfleet owner fleet;
```

factory는 플러그인이 준 기본 DB를 그대로 쓴다.

## 2. 서비스별 설정

각 서비스는 **New Service → GitHub Repo → 같은 저장소** 로 만들고, 아래대로 설정한다.
`railway.json`이 각 디렉터리에 있으므로 빌드 방식(Dockerfile)은 자동으로 잡힌다.

> **두 모듈(pixel-factory·pixel-fleet)은 Root Directory가 `/`다.** 공통 코어 `shared/`를
> Gradle 복합 빌드로 참조하는데, Docker는 컨텍스트 밖(`../`)을 못 읽으므로 이미지 빌드에
> 레포 전체가 필요하다. 두 서비스가 루트를 공유하면 `/railway.json` 하나를 서로 다른
> Dockerfile로 쓸 수 없으므로, 대신 **`RAILWAY_DOCKERFILE_PATH` 환경변수**로 각자
> Dockerfile을 지정한다(UI의 Dockerfile Path 설정도 동일한 역할).
>
> **`railway.json`은 Root Directory 바로 아래에 있어야 한다.** Railway는 설정 파일을
> Root Directory 기준으로 찾는다. 위치가 어긋나면 Dockerfile 빌더인 줄 모르고 자동 감지로
> 넘어가 **2~3초 만에 빌드가 실패**한다(로그도 거의 남지 않는다).
> 그래서 게이트웨이 설정은 `platform/gateway/`가 아니라 **`platform/railway.json`** 에 있고,
> Dockerfile 경로를 `gateway/Dockerfile`로 지정한다.

### 2-1. mosquitto (프라이빗)

| 항목 | 값 |
|---|---|
| Root Directory | `infra/mosquitto` |
| 도메인 | **만들지 않는다** (프라이빗 전용) |

환경변수 없음. 다른 서비스는 `tcp://mosquitto.railway.internal:1883`로 붙는다.

### 2-2. pixel-factory (프라이빗)

| 항목 | 값 |
|---|---|
| Root Directory | `/` ← **레포 루트** (shared/가 빌드에 필요) |
| Dockerfile Path | `modules/pixel-factory/services/oee-service/Dockerfile` |

```
RAILWAY_DOCKERFILE_PATH=modules/pixel-factory/services/oee-service/Dockerfile
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=::
PORT=9001
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
MQTT_BROKER_URL=tcp://mosquitto.railway.internal:1883
MQTT_CLIENT_ID=oee-service
PLATFORM_JWT_SECRET=<32바이트 이상 랜덤 문자열>   ← 세 서비스가 같은 값
```

### 2-3. pixel-fleet (프라이빗)

| 항목 | 값 |
|---|---|
| Root Directory | `/` ← **레포 루트** (shared/가 빌드에 필요) |
| Dockerfile Path | `modules/pixel-fleet/services/control-service/Dockerfile` |

```
RAILWAY_DOCKERFILE_PATH=modules/pixel-fleet/services/control-service/Dockerfile
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=::
PORT=9002
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/pixelfleet
SPRING_DATASOURCE_USERNAME=fleet
SPRING_DATASOURCE_PASSWORD=<1단계에서 정한 비밀번호>
REDIS_URL=${{Redis.REDIS_URL}}
MQTT_BROKER_URL=tcp://mosquitto.railway.internal:1883
MQTT_CLIENT_ID=control-service
PLATFORM_JWT_SECRET=<32바이트 이상 랜덤 문자열>   ← 세 서비스가 같은 값
DISPATCH_ENABLED=true
```

### 2-4. robot-sim (프라이빗)

| 항목 | 값 |
|---|---|
| Root Directory | `modules/pixel-fleet/robot-sim` |
| 도메인 | 없음 (웹 서버가 없는 워커) |

```
MQTT_BROKER_URL=tcp://mosquitto.railway.internal:1883
MQTT_CLIENT_ID=robot-sim
```

> 시뮬레이터는 1초마다 텔레메트리를 발행한다. 사용량이 부담되면 이 서비스만
> 꺼 두면 된다(대시보드는 계속 뜨고, 로봇만 OFFLINE으로 보인다).

### 2-4b. factory-sim (프라이빗)

| 항목 | 값 |
|---|---|
| Root Directory | `modules/pixel-factory/simulator` |
| 도메인 | 없음 (웹 서버가 없는 워커) |

```
MQTT_URL=tcp://mosquitto.railway.internal:1883
SIM_SPEED=10
```

> 설비 8대가 사이클·상태(RUNNING/DOWN)를 발행한다. `SIM_SPEED`는 배속으로,
> 10이면 30초 사이클을 3초에 낸다. 사용량을 줄이려면 값을 낮추거나(느려짐)
> 이 서비스를 꺼 둔다(설비가 IDLE로 남는다).

### 2-5. gateway + 대시보드 (퍼블릭)

| 항목 | 값 |
|---|---|
| Root Directory | `platform` ← **gateway가 아니라 platform** |
| Dockerfile Path | `gateway/Dockerfile` (railway.json에 이미 지정됨) |
| 도메인 | **생성한다** — 이 URL이 서비스 주소가 된다 |

Root Directory가 `platform`인 이유: 이미지 빌드에 `dashboard/`와 `gateway/`가 **둘 다**
필요하기 때문이다(Docker는 컨텍스트 밖의 `../`를 읽지 못한다).

```
SERVER_ADDRESS=::
AUTH_MODULE_URI=http://pixel-factory.railway.internal:9001
MODULE_FACTORY_URI=http://pixel-factory.railway.internal:9001
MODULE_FLEET_URI=http://pixel-fleet.railway.internal:9002
MODULE_FLEET_WS_URI=http://pixel-fleet.railway.internal:9002
PLATFORM_JWT_SECRET=<32바이트 이상 랜덤 문자열>   ← 세 서비스가 같은 값
```

> **포트는 참조하지 말고 고정한다.** `${{pixel-factory.PORT}}` 같은 참조는 **빈 값으로
>풀린다**(PORT는 Railway가 런타임에 주입하는 값이라 다른 서비스에서 참조할 수 없다).
> 그러면 URI가 `http://pixel-factory.railway.internal:` 로 깨져서 모든 라우팅이 **500**이
> 된다. 서비스 그래프에 화살표가 그려지더라도 값이 있다는 뜻은 아니니 속지 말 것.
> 그래서 모듈 쪽에 `PORT=9001`/`PORT=9002`를 명시하고, 여기서는 그 숫자를 그대로 쓴다.

> `MODULE_FLEET_WS_URI`도 **http://** 로 둔다. 게이트웨이가 `Upgrade` 헤더를 보고
> 자동으로 ws로 바꾼다. `ws://`로 두면 SockJS의 `/ws/info`(일반 HTTP)가 400이 된다.
> 자세한 배경은 [platform/gateway/README.md](../platform/gateway/README.md).

## 3. 배포 후 확인

```bash
curl https://<도메인>/api/factory/health
curl https://<도메인>/api/fleet/health
```

브라우저로 `https://<도메인>` 접속 → `admin` / `password` 로 로그인.

## 반드시 알아둘 점

**1. IPv6 바인딩** — Railway 프라이빗 네트워크는 IPv6로 통신한다. Spring Boot는 기본으로
IPv4에만 바인딩하므로 `SERVER_ADDRESS=::`를 **반드시** 넣어야 게이트웨이가 모듈을 찾는다.
Mosquitto도 같은 이유로 배포용 설정(`mosquitto.railway.conf`)에서 bind 주소를 생략했다.

**2. 데모 계정 비밀번호** — 세 계정 모두 `password`다(`UserDataInitializer`). 퍼블릭
도메인을 열면 누구나 로그인할 수 있다. 포트폴리오 공개용이면 그대로 두되, 그 이상이면
시드 비밀번호부터 바꿀 것.

**3. MQTT 인증 없음** — 브로커는 익명 접속을 허용한다. Railway 프라이빗 네트워크 안에서만
접근 가능하다는 전제이므로 **mosquitto에 퍼블릭 도메인을 만들면 안 된다.**

**4. 이벤트 테이블 증가** — `fleet_events`/`factory_events`는 계속 쌓인다. 시뮬레이터를
오래 켜 두면 DB 사용량이 늘어난다. 보존 정책을 넣기 전까지는 필요할 때만 켜는 것을 권한다.

**5. PLATFORM_JWT_SECRET은 세 서비스에 같은 값** — gateway·pixel-factory·pixel-fleet이
같은 키로 서명·검증한다. 하나라도 다르면 로그인은 되는데 이후 모든 조회가 **401**이 되고,
대시보드는 로그인 화면으로 되튕긴다(증상이 "로그인이 안 된다"로 보여 원인을 찾기 어렵다).
P6 이전의 `JWT_SECRET`을 쓰고 있었다면 변수명을 바꾸고 값을 통일해야 한다.

**6. `/ws/**`는 아직 인증 없이 열려 있다** — SockJS 핸드셰이크에 Authorization 헤더를
실을 수 없어서다. 퍼블릭 도메인에서는 누구나 실시간 스트림을 구독할 수 있다(읽기 전용).

## 비용 감각

상시 구동 서비스가 6개(gateway/factory/fleet/mosquitto/robot-sim/factory-sim) + 플러그인 2개다.
리소스를 가장 많이 쓰는 건 **두 시뮬레이터**다 — robot-sim은 초당 텔레메트리를,
factory-sim은 설비 8대의 사이클을 계속 만든다. 데모하지 않을 때는 이 둘을 꺼 두면
사용량과 DB 증가가 크게 줄어든다(대시보드는 계속 뜨고 설비·로봇만 멈춰 보인다).
