# Pixel Platform

> **Spring Cloud Gateway + MQTT 기반 실시간 IoT/로봇 군집 관제(FMS) 마이크로서비스 플랫폼**

Pixel Platform은 제조 현장의 서로 다른 도메인(가공 설비 OEE, AMR 로봇 군집 관제, 창고 재고, 품질 검사)을
**API Gateway + 중앙 인증 아래 4개의 독립 서비스**로 묶어, MQTT·Redis·WebSocket 기반 실시간 데이터를
하나의 대시보드로 통합해서 보여주는 컴포저블 마이크로서비스 플랫폼입니다.

가장 기술적으로 깊은 모듈은 **PixelFleet**(AMR 군집 관제)입니다 — 서버가 소유하는 레인 그래프 기반
경로 계산, 구간(segment) 단위 점유 예약으로 다중 로봇 교통정리, 배터리 인지 배차, Redis Pub/Sub
실시간 상태 브로드캐스트를 직접 구현했습니다. 나머지 세 모듈(Factory/WMS/QMS)은 같은
게이트웨이·이벤트 계약 아래 붙은 제조 도메인 확장이며, 그중 **PixelFactory(OEE)**가 코드 규모로는
가장 크고 오래된 모듈입니다(MES 마스터데이터·품질 홀드·POP 단말 포함).

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

* **실시간 로봇 군집 관제 (PixelFleet) — 이 플랫폼에서 기술적으로 가장 깊은 모듈**
  * 서버가 소유하는 레인 그래프 기반 경로 계산 + **구간(segment) 단위 점유 예약**으로 다중 AMR 교통정리
  * 배터리 잔량 인지 배차 정책, 고아 작업 감시 워치독으로 배차 신뢰성 확보
  * `Redis` Pub/Sub으로 로봇 실시간 상태를 다중 인스턴스에 팬아웃, 트랜잭션 커밋 후 `STOMP`로 대시보드에 push

* **MSA & API Gateway**
  * `Spring Cloud Gateway`를 단일 진입점으로 두어 4개 모듈의 라우팅·CORS·인증을 중앙집중화
  * 게이트웨이 레벨 JWT 검증 후 사용자 신원 헤더 주입, 클라이언트가 보낸 인증 헤더는 게이트웨이가 제거(스푸핑 방지)

* **이벤트 기반 실시간 데이터 파이프라인**
  * `Mosquitto`(MQTT)로 설비/로봇 텔레메트리를 수집해 이벤트로 영속화(Event Sourcing) — OEE·작업지시 실적이 이 이벤트 스트림에서 계산됨
  * 모듈별 `WebSocket(STOMP)`으로 대시보드에 실시간 push

* **컴포저블 도메인 설계**
  * DB per module(모듈별 독립 스키마) 원칙 준수, 모듈 간 직접 코드/DB 참조 금지 — 게이트웨이·REST·MQTT 계약으로만 통신
  * 신규 도메인 추가 시 기존 모듈에 영향을 주지 않는 구조

---

## 🛠️ Tech Stack

* **Backend:** Java 17, Spring Boot 3.3, Spring Cloud Gateway, Spring Data JPA/QueryDSL, Flyway
* **Frontend:** React, Vite (Tailwind 미사용 — CSS 변수 기반 커스텀 스타일)
* **Database & Infrastructure:** PostgreSQL 16(모듈별 독립 DB), Redis 7(PixelFleet 실시간 상태 전용), Eclipse Mosquitto(MQTT Broker), Docker Compose
* **Environment:** Windows / PowerShell, Gradle Wrapper(모듈별 독립 빌드 — 루트 통합 빌드 없음)

---

## 📂 Repository Structure

```text
pixel-platform/
├── platform/
│   ├── gateway/                                # Spring Cloud Gateway (라우팅 및 글로벌 인증)
│   └── dashboard/                               # 통합 React 관제 대시보드
├── modules/
│   ├── pixel-factory/services/oee-service/      # 가공라인 OEE·설비·MES 마스터데이터 (:9001)
│   ├── pixel-fleet/services/control-service/    # AMR 군집 관제·교통정리 (:9002)
│   ├── pixel-wms/services/wms-service/          # 창고·재고 (:9003)
│   └── pixel-qms/services/qms-service/          # 품질·부적합(MRB) (:9004)
├── shared/                                       # 공통 코어 라이브러리 (Auth, User Model 등)
├── infra/                                        # Docker Compose 설정 (Postgres, Mosquitto, Redis)
└── docs/                                         # 로드맵·백로그, 아키텍처 문서
```

---

## 🚀 Quick Start

### 1. 인프라 실행 (Docker)

```bash
cd infra
docker compose up -d
```

### 2. 백엔드 서비스 실행 (PowerShell 전용)

각 모듈은 독립된 포트와 Gradle 래퍼를 사용합니다. 새 창에서 각각 실행해주세요.

```powershell
# 1. API Gateway (:9000)
cd platform\gateway; .\gradlew.bat bootRun

# 2. Pixel Factory — OEE (:9001)
cd modules\pixel-factory\services\oee-service; .\gradlew.bat bootRun

# 3. Pixel Fleet — AMR 군집 관제 (:9002)
cd modules\pixel-fleet\services\control-service; .\gradlew.bat bootRun

# 4. Pixel WMS — 창고 (:9003)
cd modules\pixel-wms\services\wms-service; .\gradlew.bat bootRun

# 5. Pixel QMS — 품질 (:9004)
cd modules\pixel-qms\services\qms-service; .\gradlew.bat bootRun
```

### 3. 프론트엔드 대시보드 실행

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
