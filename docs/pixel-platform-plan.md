# Pixel Platform — 재구성 계획서 (실행 전 승인용)

> 목표: PixelFactory(OEE)와 PixelFleet(AMR)을 **게이트웨이 + 통합 대시보드** 아래 묶는
> 하나의 모노레포 `pixel-platform`을 만든다. 이후 PixelVision/Quality/Energy/AI로 확장.
> **이 문서는 계획이며, 승인 전에는 파일을 옮기지 않는다.** 원본 두 레포는 검증 완료까지 건드리지 않는다.

---

## 1. 목표 구조

> **현재 상태(확인됨):** GitHub `Dong-yeon/pixel-platform` 생성 완료, 로컬 clone 완료
> (`D:\happyeon\99.Happyeon\pixel-platform`, `d960566 Initial commit`, README.md만 존재).
> → 계획의 P0(디렉터리·git init)과 P5(원격 생성)는 **이미 해결**. 아래 단계는 이 clone 위에서 진행한다.

```
pixel-platform/                     (레포 1개 · GitHub: Dong-yeon/pixel-platform)
                                    (로컬: D:\happyeon\99.Happyeon\pixel-platform — clone 완료)
├── platform/
│   ├── gateway/         Spring Cloud Gateway — /api/factory/** → 9001, /api/fleet/** → 9002
│   └── dashboard/       통합 React (OEE + AMR + KPI), 게이트웨이(9000)만 바라봄
├── modules/
│   ├── pixel-factory/   OEE  (기존 GitHub 레포 → subtree 이식, 히스토리 보존)
│   └── pixel-fleet/     AMR  (현재 로컬 레포 → subtree 이식, 히스토리 보존)
├── shared/              (지금은 비움 — 공통 코어 추출은 후속. 아래 5번)
├── infra/               docker-compose: postgres, mosquitto, redis, gateway, 두 모듈
├── docs/                이 계획서 + 플랫폼 아키텍처 문서
├── scripts/             기동/빌드 편의 스크립트
├── README.md
└── .gitignore
```

포트: gateway **9000** · factory **9001** · fleet **9002** · dashboard(dev) **9100**(prod는 게이트웨이가 서빙).

---

## 2. 핵심 원칙 (지금 두 모듈의 원칙을 플랫폼으로 승격)

1. **모노레포 ≠ 모놀리스.** 각 모듈은 자체 빌드·DB·포트·Dockerfile로 **독립 배포 가능**.
2. **컴포저블.** 모듈 간 직접 코드/DB 참조 금지. 통신은 게이트웨이·REST·MQTT 계약으로만.
3. **DB per module.** factory/fleet 각자 스키마(같은 Postgres 인스턴스, DB 분리). fleet은 Redis 추가.
4. **게이트웨이가 단일 진입점.** 대시보드·외부는 게이트웨이 한 곳만. CORS·라우팅 집약.

---

## 3. 확정 결정 (내가 정함 — 이유 포함)

| 항목 | 결정 | 이유 |
|---|---|---|
| 레포 형태 | 모노레포 1개 (`pixel-platform`) | 플랫폼(게이트웨이+통합대시보드)이면 공유·크로스변경·단일 CI가 폴리레포보다 유리 |
| Gradle | **모듈별 독립 빌드 유지** (루트 통합 빌드 안 함) | 멀티프로젝트 마이그레이션 리스크 회피, 독립 배포 유지. 오케스트레이션은 docker-compose/scripts |
| 히스토리 | **git subtree로 보존** | 두 모듈의 커밋 히스토리(전 단계 작업)를 살림 |
| 패키지명 | 그대로 (`com.pixelfactory`, `com.pixelfleet`) | 리네임 불필요, 코드 변경 0 |
| 게이트웨이 | **Spring Cloud Gateway** | Java 스택 일관, "API Gateway" 패턴 명시적. (대안: Nginx — 더 가벼우나 스킬 시그널 약함) |
| DB | 단일 Postgres + DB 2개(pixelfactory, pixelfleet) | 로컬 단순. 운영은 분리 가능 |
| 원본 레포 | 검증 전까지 **그대로 둠**, subtree는 읽기만 | 안전·롤백 용이 |

---

## 4. 승인된 결정 ✅

- **D1. 히스토리** → **subtree로 보존.** 두 모듈의 커밋 히스토리를 살려서 이식.
- **D2. 기존 PixelFactory GitHub 레포** → **pixel-platform이 새 정본.** 앞으로 platform에서만 작업,
  기존 레포는 나중에 아카이브(삭제하지 않음).
- **D3. pixel-fleet/web** → **통합 `platform/dashboard`로 흡수.** 단일 대시보드에서 OEE·AMR·KPI.
- **D4. 공통 코어** → **`shared/`로 추출.** common(응답·예외·BaseEntity)·auth/JWT·user를 두 모듈이 공유.
- **D5. 게이트웨이 인증** → **중앙 검증.** JWT를 게이트웨이에서 검증하고 모듈은 신뢰.

> D4·D5는 범위가 큰 편이라 **뒤 단계(P5·P6)로 배치**한다. 모듈이 먼저 정상 동작하는 걸 확인한 뒤
> 손대야 중간에 깨져도 롤백이 쉽다.

---

## 5. 실행 단계 (각 단계 검증 후 커밋)

- ~~**P0. 골격**~~ — ✅ 완료(레포 생성·clone 완료). 남은 것: `.gitignore`/`docs`/`infra`/`scripts` 스켈레톤 추가
- **P1. 모듈 이식** — subtree add로 pixel-fleet, pixel-factory 이식 → **각 모듈 개별 빌드 통과 확인**
- **P2. 통합 인프라** — 루트 docker-compose(postgres 2DB + mosquitto + redis) → 두 모듈 기동 검증
- **P3. 게이트웨이** — Spring Cloud Gateway + 라우트(`/api/factory/**`→9001, `/api/fleet/**`→9002) → 라우팅 검증
- **P4. 통합 대시보드** — `platform/dashboard` React(모듈 선택 → OEE/AMR 뷰), pixel-fleet/web 흡수(D3) → 게이트웨이 경유 실측
- **P5. 공통 코어 추출 (D4)** — common/auth/user를 `shared/`로 이동, 두 모듈이 의존 → 양쪽 빌드·런타임 재검증
- **P6. 게이트웨이 중앙 인증 (D5)** — JWT 검증을 게이트웨이로, 모듈은 신뢰 헤더 기반 → 인증 흐름 재검증
- **P7. Railway 배포** — 게이트웨이+모듈+DB+Redis+MQTT

각 단계 끝에 빌드/런타임 검증 후 커밋 + push (원격은 이미 연결됨).

**PixelFleet 미push 커밋 주의:** 현재 PixelFleet 로컬에만 있는 커밋 7개(Phase 0~3 + Redis)가
subtree 이식 대상이다. 이식 후 원본 PixelFleet은 그대로 두되, 이후 작업은 platform 쪽에서 한다.

각 단계 끝에 빌드/런타임 검증(지금까지의 방식대로) 후 커밋.

---

## 6. 리스크 & 롤백

- **원본 불변** — PixelFactory/PixelFleet 원본은 P0~P4 동안 그대로. 문제 생기면 `PixelPlatform`만 삭제하고 재시도.
- **이 세션 제약** — 세션이 PixelFactory 워크트리 안에서 돌므로, PixelPlatform은 **별도 위치**에 생성하고 PixelFactory 원본은 이동/삭제하지 않는다.
- **subtree 되돌리기 어려움** — 새 레포에서만 하므로 영향 국소. 원본에는 손대지 않음.
- **PixelFactory 워크트리 잔재** — subtree는 커밋된 소스만 가져오므로 build/·worktrees 등 미추적 파일은 안 들어옴.
- **Railway 구성 재작업** — PixelFleet 단독 배포 구성을 미뤄서 플랫폼 단위로 한 번만 작성(중복 방지).

---

## 7. 이번에 안 하는 것 (범위 밖 → 후속)

- PixelVision/Quality/Energy/AI 실제 구현 (구조만 확장 가능하게 열어둠)
- 원본 두 레포의 아카이브/삭제 (검증·push 후 사용자 확인하에)
- 루트 Gradle 멀티프로젝트 통합 (모듈별 독립 빌드 유지)

## 8. 예상 난이도

| 단계 | 난이도 | 비고 |
|---|---|---|
| P1 이식 | 낮음 | subtree add, 코드 변경 없음 |
| P2 인프라 | 낮음 | 기존 compose 통합 |
| P3 게이트웨이 | 중간 | 새 모듈, 라우팅·WebSocket 프록시 주의 |
| P4 대시보드 | 중간 | fleet/web 재사용 + factory 뷰 추가 |
| P5 shared 추출 | **높음** | 두 모듈 패키지 의존 변경, 빌드 재구성. 되돌리기 번거로움 |
| P6 중앙 인증 | **높음** | 인증 흐름 변경 — 잘못하면 전 API가 막힘 |
| P7 Railway | 중간 | 서비스 5개(gateway/factory/fleet/mosquitto + DB·Redis) |

P5·P6은 각각 시작 전에 한 번 더 확인받고 진행한다.
```
