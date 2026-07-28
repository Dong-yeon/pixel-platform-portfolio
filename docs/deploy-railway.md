# Railway 배포 가이드

Pixel Platform을 Railway에 올리는 절차. **배포 실행에는 동연님의 Railway 계정 인증이
필요**하므로, 이 문서는 그대로 따라 하면 되도록 설정값을 전부 적어 두었다.

## 서비스 구성 (6개 + 플러그인 2개)

```
[퍼블릭]  gateway (+대시보드 번들)  ← 유일하게 도메인을 여는 서비스
             │
[프라이빗] ├─ pixel-factory
           ├─ pixel-fleet
           ├─ mosquitto
           └─ robot-sim
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
| Root Directory | `modules/pixel-factory/services/oee-service` |

```
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=::
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
MQTT_BROKER_URL=tcp://mosquitto.railway.internal:1883
MQTT_CLIENT_ID=oee-service
JWT_SECRET=<32바이트 이상 랜덤 문자열>
```

### 2-3. pixel-fleet (프라이빗)

| 항목 | 값 |
|---|---|
| Root Directory | `modules/pixel-fleet/services/control-service` |

```
SPRING_PROFILES_ACTIVE=dev
SERVER_ADDRESS=::
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/pixelfleet
SPRING_DATASOURCE_USERNAME=fleet
SPRING_DATASOURCE_PASSWORD=<1단계에서 정한 비밀번호>
REDIS_URL=${{Redis.REDIS_URL}}
MQTT_BROKER_URL=tcp://mosquitto.railway.internal:1883
MQTT_CLIENT_ID=control-service
JWT_SECRET=<32바이트 이상 랜덤 문자열>
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
MODULE_FACTORY_URI=http://pixel-factory.railway.internal:${{pixel-factory.PORT}}
MODULE_FLEET_URI=http://pixel-fleet.railway.internal:${{pixel-fleet.PORT}}
MODULE_FLEET_WS_URI=http://pixel-fleet.railway.internal:${{pixel-fleet.PORT}}
```

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

**5. JWT_SECRET** — 모듈마다 자체 검증이므로 지금은 서로 달라도 되지만, P6에서 게이트웨이
중앙 인증으로 바꾸면 **같은 값**을 공유해야 한다.

## 비용 감각

상시 구동 서비스가 5개(gateway/factory/fleet/mosquitto/robot-sim) + 플러그인 2개다.
Hobby 플랜에서 충분히 돌아가지만, robot-sim이 초당 텔레메트리를 만들어 리소스를 가장
많이 쓴다. 데모하지 않을 때는 robot-sim을 꺼 두면 사용량이 크게 줄어든다.
