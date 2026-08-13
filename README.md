# Pixel Platform

[![CI](https://github.com/Dong-yeon/pixel-platform-portfolio/actions/workflows/ci.yml/badge.svg)](https://github.com/Dong-yeon/pixel-platform-portfolio/actions/workflows/ci.yml)

> **Spring Cloud Gateway + MQTT 기반 실시간 IoT/로봇 군집 관제(FMS) 마이크로서비스 플랫폼**

## 🚀 라이브 데모

**[gateway-production-4f47.up.railway.app](https://gateway-production-4f47.up.railway.app)**
— `admin` / `password`로 로그인 (데모 계정)

> 지금 배포된 건 **PixelFactory(OEE) + PixelFleet(AMR) 2모듈**입니다. WMS·QMS는 아직
> 배포 설정(Dockerfile/railway.json)이 없어 로컬에서만 동작합니다 — 자세한 건
> [`docs/deploy-railway.md`](docs/deploy-railway.md) 참고.

Pixel Platform은 제조 현장의 서로 다른 도메인(가공 설비 OEE, AMR 로봇 군집 관제, 창고 재고, 품질 검사)을
**API Gateway + 중앙 인증 아래 4개의 독립 서비스**로 묶어, MQTT·Redis·WebSocket 기반 실시간 데이터를
하나의 대시보드로 통합해서 보여주는 컴포저블 마이크로서비스 플랫폼입니다.

가장 기술적으로 깊은 모듈은 **PixelFleet**(AMR 군집 관제)입니다 — 처음엔 통로·연결로 좌표를
컴파일타임 상수로 고정한 규칙 기반 라우터였지만, 건물이 여러 채로 늘어나며 **노드-엣지 그래프 +
A*/Dijkstra 경로탐색**으로 전면 교체했습니다. 그래프 엣지 비용을 동적으로 바꿀 수 있어 장애물
발생 시 진행 중인 로봇이 실시간으로 우회하고, 건물 사이는 게이트 노드(엘리베이터)로만 연결되는
컴포저블 구조입니다. 그 위에 구간(segment) 단위 점유 예약으로 다중 로봇 교통정리, 배터리 인지
배차, Redis Pub/Sub 실시간 상태 브로드캐스트를 직접 구현했습니다. 나머지 세 모듈(Factory/WMS/QMS)은
같은 게이트웨이·이벤트 계약 아래 붙은 제조 도메인 확장이며, 그중 **PixelFactory(OEE)**가 코드
규모로는 가장 크고 오래된 모듈입니다(MES 마스터데이터·품질 홀드·POP 단말 포함).

---

## 🏗️ System Architecture

```
                    ┌────────────────────────────────────┐
                    │           Unified Dashboard          │
                    │  (React · Overview/Factory/Fleet/    │
                    │       WMS/QMS 탭 전환, :9200)        │
                    └────────────────┬─────────────────────┘
                                     │ HTTP / WebSocket(STOMP)
                    ┌────────────────┴─────────────────────┐
                    │          API Gateway (:9000)          │
                    │    - Spring Cloud Gateway 라우팅      │
                    │    - 중앙 집중형 JWT 인증 필터        │
                    └────────────────┬─────────────────────┘
                                     │
     ┌───────────────┬───────────────┼───────────────┬───────────────┐
     ▼               ▼               ▼               ▼
 PixelFactory     PixelFleet       PixelWMS        PixelQMS
 OEE·설비 관제    AMR 군집 관제    창고·재고        품질·부적합(MRB)
 :9001            :9002            :9003            :9004
 MQTT Event       MQTT + Redis     (창고 마스터/    (검사·홀드/
 Sourcing         + WS 기반        재고)            릴리즈)
                  교통정리
```

---

## 💡 Key Engineering Features

* **동적 그래프 기반 경로계산 + 다중 건물 컴포저블 레이아웃 (PixelFleet)**
  * 통로·연결로 좌표를 컴파일타임 상수로 고정한 4단 규칙 라우터를 **노드-엣지 그래프 + A*/Dijkstra
    경로탐색**으로 교체 — 건물이 늘어나도 코드 변경 없이 노드·엣지 삽입만으로 확장
  * 장애물 발생/해제를 이벤트로 다뤄 엣지 비용을 동적으로 바꾸고, 진행 중인 로봇이 실시간으로 재경로
  * 서버가 소유하는 경로 위에 **구간(segment) 단위 점유 예약**으로 다중 AMR 교통정리, 배터리 인지
    배차 정책, 고아 작업 감시 워치독으로 배차 신뢰성 확보
  * `Redis` Pub/Sub으로 로봇 실시간 상태를 다중 인스턴스에 팬아웃, 트랜잭션 커밋 후 `STOMP`로 대시보드에 push

* **품질 홀드 · MRB 심의 · Outbox 알림 (PixelQMS)**
  * 불량 임계 초과 시 factory가 발행한 이벤트를 받아 검사를 생성하고, MRB(자재검토위원회)가 열리면
    별개 서비스·별개 DB인 factory의 설비를 `QUALITY_HOLD`로 전환 — REST 계약만으로 두 도메인이 연동됨
  * 실제 SMTP 대신 `NotificationSender` 인터페이스 뒤에 Outbox 구현체를 둬 발송 이력을 화면에서
    확인 가능하게 하고, 실제 메일 연동은 구현체 교체만으로 가능하도록 확장점을 설계

* **MSA & API Gateway**
  * `Spring Cloud Gateway`를 단일 진입점으로 두어 4개 모듈의 라우팅·CORS·인증을 중앙집중화
  * 게이트웨이 레벨 JWT 검증 후 사용자 신원 헤더 주입, 클라이언트가 보낸 인증 헤더는 게이트웨이가 제거(스푸핑 방지)

* **관측성 (Prometheus)**
  * BACKLOG에 수기로 적혀 있던 관측("다운타임 중 최대 94초 지연", "동시 주행 1~2대")을
    `/actuator/prometheus` 커스텀 메트릭으로 상시 노출 — 이벤트 적재 지연(p50/p95/p99),
    미배차 대기 주문 수, 레인 구간 점유 수
  * 이벤트 테이블(`factory_events`/`fleet_events`) 무한 증가 문제는 매일 새벽 벌크 DELETE로
    해결(기본 90일 보존, 파생 쿼리 대신 `@Modifying @Query`로 엔티티 단위 삭제 부하를 피함)

* **이벤트 기반 실시간 데이터 파이프라인**
  * `Mosquitto`(MQTT)로 설비/로봇 텔레메트리를 수집해 이벤트로 영속화(Event Sourcing) — OEE·작업지시 실적이 이 이벤트 스트림에서 계산됨
  * 모듈별 `WebSocket(STOMP)`으로 대시보드에 실시간 push

* **컴포저블 도메인 설계**
  * DB per module(모듈별 독립 스키마) 원칙 준수, 모듈 간 직접 코드/DB 참조 금지 — 게이트웨이·REST·MQTT 계약으로만 통신
  * 신규 도메인 추가 시 기존 모듈에 영향을 주지 않는 구조 — 각 모듈을 내려도 나머지는 정상 동작(컴포저블 검증)

---

## 🛠️ Tech Stack

* **Backend:** Java 17, Spring Boot 3.3, Spring Cloud Gateway, Spring Data JPA/QueryDSL, Flyway
* **Frontend:** React, Vite (Tailwind 미사용 — CSS 변수 기반 커스텀 스타일)
* **Database & Infrastructure:** PostgreSQL 16(모듈별 독립 DB), Redis 7(PixelFleet 실시간 상태 전용), Eclipse Mosquitto(MQTT Broker), Docker Compose
* **Environment:** Windows / PowerShell 기준으로 작성됐지만, `./gradlew`(bash)도 동일하게
  동작합니다(CI가 Linux에서 이 방식으로 빌드). Gradle Wrapper — 모듈별 독립 빌드, 루트 통합 빌드 없음

---

## 📂 Repository Structure

```text
pixel-platform/
├── platform/
│   ├── gateway/                                # Spring Cloud Gateway (라우팅 및 글로벌 인증)
│   └── dashboard/                               # 통합 React 관제 대시보드
├── modules/
│   ├── pixel-factory/
│   │   ├── services/oee-service/                # 가공라인 OEE·설비·MES 마스터데이터 (:9001)
│   │   └── simulator/                           # 설비 텔레메트리 발행기 (MQTT)
│   ├── pixel-fleet/
│   │   ├── services/control-service/            # AMR 군집 관제·교통정리 (:9002)
│   │   └── robot-sim/                           # 로봇 텔레메트리 발행기 (MQTT)
│   ├── pixel-wms/services/wms-service/          # 창고·재고 (:9003)
│   └── pixel-qms/services/qms-service/          # 품질·부적합(MRB) (:9004)
├── shared/                                       # 공통 코어 라이브러리 (Auth, User Model 등)
├── infra/                                        # Docker Compose 설정 (Postgres, Mosquitto, Redis)
├── scripts/                                      # 로컬 스택 기동/정지 (dev-up.ps1 / dev-down.ps1)
└── docs/                                         # 로드맵·백로그, 설계 문서
```

---

## 🚀 Quick Start

### 방법 A — 스택 스크립트 (권장, Windows/PowerShell)

`scripts/dev-up.ps1`이 Docker 인프라 기동 → 필요한 모듈만 jar 빌드 → 의존 순서대로 기동
(factory → robot-sim → fleet → …)까지 한 번에 처리합니다. **시뮬레이터(설비/로봇 텔레메트리
발행기)까지 함께 띄우므로, 이 스크립트 없이 모듈만 개별로 켜면 대시보드에 흐르는 데이터가 없습니다.**

```powershell
# 화면까지 전부 보고 싶을 때 (gateway + 대시보드 + 데모 작업 생성기 포함)
.\scripts\dev-up.ps1 -Stack full -Demo

# AMR 배차·주행 엔진만 볼 때 (docker + factory + robot-sim + fleet)
.\scripts\dev-up.ps1

# 출고지시 → 운송 → 재고 차감 흐름까지 볼 때 (+ wms)
.\scripts\dev-up.ps1 -Stack e2e

# 종료
.\scripts\dev-down.ps1
```

로그는 `logs/<서비스명>.log`에 쌓입니다. `-Stack full`이면 대시보드가 `http://localhost:9200`에서 뜹니다.

### 방법 B — 모듈별 수동 기동

각 모듈을 직접 제어하고 싶을 때(디버깅, IDE 실행 등) 씁니다. 새 창에서 각각 실행하세요.
**설비/로봇 데이터가 필요하면 `pixel-factory/simulator`와 `pixel-fleet/robot-sim`도 반드시 함께 띄워야 합니다.**

```bash
cd infra && docker compose up -d   # Postgres · Redis · Mosquitto
```

```powershell
# API Gateway (:9000)
cd platform\gateway; .\gradlew.bat bootRun

# Pixel Factory — OEE (:9001)
cd modules\pixel-factory\services\oee-service; .\gradlew.bat bootRun

# Pixel Factory 설비 시뮬레이터 (MQTT로 사이클/상태/불량 이벤트 발행)
cd modules\pixel-factory\simulator; .\gradlew.bat run

# Pixel Fleet — AMR 군집 관제 (:9002)
cd modules\pixel-fleet\services\control-service; .\gradlew.bat bootRun

# Pixel Fleet 로봇 시뮬레이터 (위치/배터리/작업 이벤트 발행)
cd modules\pixel-fleet\robot-sim; .\gradlew.bat bootRun

# Pixel WMS — 창고 (:9003)
cd modules\pixel-wms\services\wms-service; .\gradlew.bat bootRun

# Pixel QMS — 품질 (:9004)
cd modules\pixel-qms\services\qms-service; .\gradlew.bat bootRun
```

```bash
cd platform/dashboard
npm install
npm run dev   # http://localhost:9200
```

---

## 🔌 API & Gateway Routing

모든 외부 요청과 대시보드는 9000번 포트(API Gateway)를 통과합니다.

* `http://localhost:9000/api/factory/**` → PixelFactory (:9001)로 라우팅
* `http://localhost:9000/api/fleet/**` → PixelFleet (:9002)로 라우팅
* `http://localhost:9000/api/wms/**` → PixelWMS (:9003)로 라우팅
* `http://localhost:9000/api/qms/**` → PixelQMS (:9004)로 라우팅
* `ws://localhost:9000/ws/factory/**`, `ws://localhost:9000/ws/fleet/**` → 모듈별 실시간 push (STOMP over SockJS)

### 인증 테스트 예시

```bash
# 1. 토큰 발급
TOKEN=$(curl -s -X POST http://localhost:9000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"password"}' | jq -r .data.accessToken)

# 2. 게이트웨이를 통한 보호된 리소스(Fleet) 접근
curl -H "Authorization: Bearer $TOKEN" http://localhost:9000/api/fleet/robots
```

---

## 📚 설계 결정과 트러블슈팅

이 프로젝트의 상세한 설계 근거와 실제로 겪은 장애·교착·수정 과정은 `docs/`에 있습니다.
"구현했다"가 아니라 "이렇게 재현해봤고 이런 숫자가 나왔다"는 검증 기록 위주입니다.

* [`docs/pixel-platform-roadmap.md`](docs/pixel-platform-roadmap.md) — 단계별 로드맵(P8~P15)과
  각 단계에서 원안이 실제로 갈라진 지점(예: OEE 휴식시간을 "총 분"이 아니라 "시각"으로 바꾼 이유,
  MQTT 재접속 시 콜백 교착을 스레드 덤프로 잡은 과정)
* [`docs/BACKLOG.md`](docs/BACKLOG.md) — 범위 밖 아이디어와 실제로 터진 장애(배터리 20~24%
  사각지대로 함대 전체가 멈춘 사고, leg 예약 도입 중 겪은 hold-and-wait 교착)
* [`docs/p20-layout-routing-design.md`](docs/p20-layout-routing-design.md) — 정적 규칙 라우터를
  그래프 탐색으로 교체한 설계 결정과 검증 이력
* [`docs/deploy-railway.md`](docs/deploy-railway.md) — Railway 배포 가이드와 실제로 겪은 배포 함정
