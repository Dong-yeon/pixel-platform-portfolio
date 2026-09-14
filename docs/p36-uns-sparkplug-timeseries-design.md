# P36 설계 문서 — 수집 계층 승격: UNS 토픽 계약 · 생사 인증서 · 시계열 저장

> 상태: **설계 초안 (2026-09-14). 승인 후 WP0부터 착수.** `docs/p20~p35-*.md`와 같은 형식.
>
> 선행 문서: `modules/pixel-factory/docs/mqtt-topics.md`(현 토픽 계약) ·
> `modules/pixel-fleet/docs/mqtt-topics.md` · P16(MQTT ACL, M2M 서비스 토큰) ·
> P33(생산동 구역화 — 가공 A / 조립 B / 품질 Q / 물류 L) · P35(다공정 라우팅).
>
> **왜 지금인가.** 이 플랫폼의 MQTT 백본은 P8 이후로 한 번도 재설계된 적이 없다. 그동안
> 건물이 여러 채로 늘고(P28~P32), 생산동이 4개 구역으로 나뉘고(P33), 모듈이 2개에서
> 4개로 늘었는데 **토픽은 여전히 모듈 이름 하나를 루트로 쓰는 평평한 문자열**이다.
> 그 결과가 이미 코드 주석에 남아 있다 — `MqttTaskEventPublisher`는 "로봇 텔레메트리가
> 3마디라서 `fleet/#`를 구독하는 자기 자신의 핸들러가 자기가 낸 메시지를 다시 먹지 않도록"
> 마디 수를 4로 늘려 회피하고 있다. 네임스페이스에 설계가 없고 관례만 있다는 신호다.
>
> 동시에 저장 쪽은 `factory_events` **단일 테이블 + 매일 새벽 벌크 DELETE**가 전부다.
> 이 문서는 그 둘을 한 번에 다룬다: **토픽에 의미를 붙이고(UNS), 발행자의 생사를
> 계약으로 만들고(birth/death), 이벤트 테이블을 시계열 저장소로 바꾼다.**

---

## 0. 지금 구조가 왜 확장을 못 담는가

| 지금 | 근거 | 문제가 되는 이유 |
|---|---|---|
| 토픽 루트가 **모듈 이름** | `factory/#`, `fleet/#` | 물리 세계에 없는 경계다. 공장은 하나인데 토픽 트리는 소프트웨어 조직도를 따른다. 소비자가 "1동 가공구역의 모든 것"을 구독할 방법이 없다 |
| 모듈마다 **마디 수가 다르다** | factory 4마디 `factory/{line}/{equip}/{kind}`, fleet 3마디 `fleet/{robot}/{kind}`, 그런데 fleet 작업은 4마디 `fleet/tasks/{taskCode}/{event}` | 자기 발행을 자기가 다시 먹는 문제를 **마디 수로 회피**하고 있다(`MqttTaskEventPublisher` 주석). 와일드카드 구독이 우연에 의존한다 |
| 토픽에 **위치 계층이 없다** | factory 토픽의 최상위 구분자가 `lineCode`뿐 | 건물·구역이 P28~P33에서 실제로 생겼는데 토픽은 그걸 모른다. 새 건물이 생기면 토픽 규칙이 아니라 구독자 코드가 바뀐다 |
| 페이로드 규격이 **문서에만 있다** | `mqtt-topics.md`에 JSON 예시, 코드는 `objectMapper.readTree` 후 `path("status").asText()` | 오타 난 필드는 WARN 후 조용히 드롭된다(`MqttMessageHandler.handleStatus`). 스키마도 버전도 없어서 발행자와 구독자가 언제 갈라졌는지 알 방법이 없다 |
| **death는 있고 birth는 없다** | `FactorySimulator.connect`가 LWT로 retained `{"status":"DOWN","reason":"DISCONNECTED"}` | 죽음은 이미 손으로 구현해 뒀다. 그런데 "설비가 다시 붙었다 + 이 설비가 발행할 항목은 이것들이다"를 알리는 개시 선언이 없어서, 구독자는 설비 목록을 **DB 마스터에서 따로** 알아야 한다 |
| **시퀀스 번호가 없다** | 모든 페이로드가 `{값..., ts}` | 몇 건이 빠졌는지 아무도 모른다. OEE의 P가 낮게 나와도 "실제로 느린 것"과 "이벤트를 흘린 것"을 구분할 수 없다 |
| **발행 측에만 유실 방어가 없다** | 구독 측은 `cleanSession=false` + QoS 1로 이미 방어됨(`mqtt-topics.md`). 발행 측은 `MemoryPersistence` + `catch (MqttException) { System.err.println(...) }` | **비대칭이다.** 서버가 죽으면 브로커가 큐에 쌓아 뒀다 밀어 주지만, 브로커가 죽으면 그 구간 사이클은 stderr 한 줄 남기고 영구 소실된다 |
| 이벤트 = **평평한 단일 테이블** | `factory_events`: `bigserial` PK, `payload_json text`, `created_at desc` 인덱스 | 파티션도 압축도 없다. 보존은 `deleteByCreatedAtBefore` 벌크 DELETE — 하이퍼테이블이면 청크 드롭 한 줄이다 |
| 집계가 **매 조회마다 원본 스캔** | `FactoryEventRetentionJob` Javadoc: "OEE는 항상 최근 구간만 조회한다(`OeeService`)" | 사전 집계가 없다. 조회 구간이 넓어지는 순간(주·월 OEE) 비용이 선형으로 늘고, 보존 기간을 줄이면 과거 집계가 같이 사라진다 |

**요약: 토픽에 의미가 없고(①), 발행자의 생사와 연속성이 계약이 아니며(②), 저장이
시계열 저장이 아니다(③).** 수집 자체는 P8에서 이미 돌아간다 — 문제는 늘 그 뒤 셋이다.

---

## 0-A. 먼저 정직하게 — 이 레포는 처리량 문제를 겪고 있지 않다

이 문서가 TimescaleDB를 꺼내는 근거를 "지금 느려서"로 쓰면 거짓이다. 실측값은 이렇다.

| | 값 | 출처 |
|---|---|---|
| 설비 수 | 8대 | `FactorySimulator.EQUIPMENTS` |
| 사이클 주기 | 1.5~4.5초 | 같은 파일, `idealCycleTimeMs` ±10~30% |
| **실제 발행량** | **약 3 events/s** | 위 둘에서 계산 · P8 검증 시 "몇 시간에 72,862건"과 일치 |
| 참고: 100ms 스캔 × 1,000 태그 | 10,000 rows/s | 실제 PLC 수집 현장의 흔한 규모 |

**약 3,400배 차이다.** Postgres 단일 테이블로 초당 3건은 아무 문제가 없고, 앞으로도
시뮬레이터 규모를 유지하는 한 없다. 그러므로 이 설계의 근거는 처리량이 아니라 **셋**이다.

1. **보존 정책이 이미 이 레포의 실제 문제였다.** `docs/BACKLOG.md`에 오래 남아 있던
   항목이고("비용 직결"), 해결책으로 벌크 DELETE 잡을 직접 짰다. 하이퍼테이블이면
   그 잡 전체가 `add_retention_policy` 한 줄로 대체된다 — **이미 짠 코드가 사라지는**
   종류의 개선이라 가치가 측정 가능하다.
2. **집계 캐시를 직접 짜기 직전이다.** 조회 구간이 넓어지면 다음 수순은 `oee_daily`
   같은 요약 테이블과 그걸 채우는 배치다. 연속 집계(continuous aggregate)는 그
   배치를 DDL로 대체한다.
3. **부하가 없을 때 구조를 바꿔야 한다.** 초당 3건에서 마이그레이션하는 비용과 초당
   1만 건에서 하는 비용은 다르다.

> **이 절을 문서에 남기는 이유.** README가 "구현했다가 아니라 이렇게 재현해봤고 이런
> 숫자가 나왔다"를 표방한다. 측정하지 않은 규모를 근거로 기술을 고르는 순간 그 약속이
> 깨진다. 초당 1만 건을 **주장하지 않고**, 지금 값을 적고 그래도 왜 바꾸는지를 적는다.

---

## 1. 목표 구조 — 5계층 매핑

산업 데이터 파이프라인의 표준 5계층에 **이 레포가 실제로 어디에 있는지**를 먼저 박는다.

```
[1] 디바이스          PLC / 인버터 / 센서              ← 이 레포에 없다 (물리 설비 없음)
[2] 엣지 게이트웨이   프로토콜 변환 + 컨텍스트 + S&F   ← simulator / robot-sim 이 겸한다
[3] 플랜트 백본       MQTT Broker = Unified Namespace  ← Mosquitto (P16에서 인증·ACL 완료)
[4] 엔터프라이즈      브리징 / Kafka / OPC UA PubSub   ← 이 레포에 없다 (단일 플랜트)
[5] 소비자            MES · Historian · 대시보드 · AI  ← factory·fleet·wms·qms + 대시보드
```

**[1]과 [4]는 없고, 앞으로도 만들지 않는다**(6절). 이 문서가 건드리는 것은 [2]→[3]의
계약과 [5]의 저장이다. 시뮬레이터가 [2]를 겸한다는 사실은 숨기지 않는다 — 실제 현장에서
KEPServerEX나 Ignition Edge가 앉는 자리이고, 이 레포에서는 그 자리의 **책임**(컨텍스트
부여·store-and-forward·birth/death 발행)만 시뮬레이터가 진다.

### 바뀌는 것

```
지금                                     목표
────────────────────────────────        ────────────────────────────────────────────
factory/LINE-1/CNC-01/cycle             pixel/plant1/prod-a/LINE-1/CNC-01/cycle
factory/LINE-1/CNC-01/status            pixel/plant1/prod-a/LINE-1/CNC-01/status
                                        pixel/plant1/prod-a/LINE-1/CNC-01/birth   ← 신규
fleet/AMR-01/position                   pixel/plant1/logi/_/AMR-01/position
fleet/tasks/T-1234/completed            pixel/plant1/logi/_/_/tasks/T-1234/completed
                                        (↑ D2에서 재검토 — 이동체는 라인이 없다)

{ "defect": false, "ts": "..." }        { "defect": false, "ts": "...", "seq": 4821 }
                                                                          ↑ 신규
factory_events (평평한 테이블)           factory_events (하이퍼테이블)
  + 매일 03:00 벌크 DELETE                 + add_retention_policy
  + 조회마다 원본 스캔                     + continuous aggregate (시프트·일 OEE)
```

---

## 2. 핵심 원칙 재확인

| 원칙 | 출처 | 이 설계가 지키는 방법 |
|---|---|---|
| 이벤트가 단일 진실 공급원 | pixel-factory CLAUDE.md | 토픽·페이로드가 바뀌어도 `factory_events`가 원본이라는 사실은 그대로. 연속 집계는 **파생 캐시**이며 언제든 원본에서 재계산 가능해야 한다 |
| 컴포저블 — 모듈 간 직접 참조 금지 | 루트 CLAUDE.md | UNS는 오히려 이 원칙의 강화판이다. 토픽 루트가 모듈 이름이 아니게 되면 "wms가 fleet 토픽을 안다"는 현재의 미묘한 결합이 "wms가 물류구역 작업 토픽을 구독한다"로 바뀐다 |
| 모듈은 독립 배포 가능 | 루트 CLAUDE.md 원칙 1 | 토픽 전환은 **브리지 기간 동안 양쪽 동시 발행**(D7). 한 모듈만 먼저 넘어가도 깨지지 않아야 한다 |
| 시뮬레이터는 물리 세계를 흉내낸다 | 로드맵 P8 결정 · P11 `NodeMap` 판단 | 시뮬레이터는 서버가 알려주는 대로 자기 위치를 바꾸지 않는다. 그래서 **토픽의 구역 코드도 시뮬레이터가 자기 상수로 갖는다** — 서버에서 받아오면 서버가 죽었을 때 발행이 멈춘다 |
| 신뢰 경계는 토픽 네임스페이스를 따른다 | `infra/mosquitto/acl.conf` | ACL이 현재 토픽 모양에 **직접 묶여 있다**. 토픽을 바꾸면 ACL도 같은 커밋에서 바뀌어야 하고, 바뀌지 않으면 조용히 전부 거부된다(D7의 검증 항목) |
| 측정하지 않은 것을 주장하지 않는다 | README | 0-A절. 그리고 완료 기준(5절)은 전부 관측 가능한 값이다 |

---

## 3. 확정 결정 (D1 ~ D9)

### D1. 토픽을 ISA-95 계층으로 통일한다 — `pixel/{site}/{area}/{line}/{cell}/{kind}`

모듈 이름을 루트에서 **뺀다**. 루트는 `pixel`(엔터프라이즈) 하나다.

| 마디 | 값 예 | 마스터 |
|---|---|---|
| site | `plant1` | 상수(단일 플랜트) |
| area | `prod-a` `prod-b` `qual` `logi` | P33 구역화가 정한 4구역 |
| line | `LINE-1` `LINE-2` `_` | `equipments.line_code`. 라인 개념이 없으면 `_` |
| cell | `CNC-01` `AMR-01` | 설비 코드 / 로봇 코드 |
| kind | `status` `cycle` `position` `battery` `birth` | 기존 값 유지 |

**왜 마디 수를 고정하는가.** 지금 `fleet/#` 구독자가 자기 발행을 다시 먹는 문제를 마디
수로 회피하고 있다. 마디 수가 전부 같아지면 그 회피가 사라지고, 대신 **발행/구독 경계를
`kind`로 명시적으로** 가른다. 이동체처럼 라인이 없는 대상은 `_`를 쓴다 — 마디를 빼면
와일드카드 위치가 어긋난다.

**얻는 것(측정 가능).** "1동 가공구역의 모든 것"이 `pixel/plant1/prod-a/#` 한 줄이 된다.
지금은 그 구독이 불가능하다 — 구역 정보가 토픽에 없기 때문이다.

### D2. 작업 이벤트는 텔레메트리와 **다른 루트**로 분리한다

`fleet/tasks/{taskCode}/{event}`는 물리 위치가 없는 **업무 이벤트**다. 이걸 위치 계층에
억지로 끼우면 `logi/_/_/tasks/...`처럼 `_`가 연달아 붙는다(1절 예시). 위치 계층이 아닌
것을 위치 계층에 넣지 않는다.

```
pixel/plant1/{area}/{line}/{cell}/{kind}   ← 텔레메트리 (물리 대상이 있다)
pixel/_biz/tasks/{taskCode}/{event}        ← 업무 이벤트 (물리 대상이 없다)
pixel/_biz/quality/{event}                 ← factory/quality/inspection-requested 이관
```

`_biz` 접두사가 **와일드카드 충돌을 구조적으로 막는다** — `pixel/plant1/#`는 업무
이벤트를 절대 잡지 않는다. 지금처럼 마디 수에 기대지 않는다.

### D3. Sparkplug B는 **개념만 차용하고 규격은 채택하지 않는다**

이게 이 문서에서 가장 논쟁적인 결정이라 이유를 길게 적는다.

Sparkplug B가 주는 것은 셋이다 — ⓐ Protobuf 페이로드 고정, ⓑ birth/death 인증서,
ⓒ 변화 시에만 발행(RBE). **이 레포에 가치가 있는 것은 ⓑ뿐이다.**

| | 채택? | 이유 |
|---|---|---|
| ⓐ Protobuf 페이로드 | **아니오** | `mqtt-topics.md`의 사람이 읽는 계약과 `mosquitto_sub`로 바로 들여다보는 디버깅 편의를 잃는다. 얻는 것은 대역폭인데, 초당 3건에서 대역폭은 문제가 아니다(0-A). **이 레포에서 Protobuf 전환은 비용만 있고 편익이 없다** |
| ⓑ birth/death 인증서 | **예** | death는 LWT로 이미 있다. birth만 없어서 구독자가 설비 목록을 DB에서 따로 알아야 한다 |
| ⓒ RBE | **부분** | `status`는 이미 변화 시에만 발행한다. `cycle`은 사건 자체라 RBE가 성립하지 않는다. **이미 하고 있으므로 새로 할 일이 없다** |
| ⓓ 시퀀스 번호 | **예** | Sparkplug의 `seq`를 그대로 가져온다. D4 참고 |

**따라서 "Sparkplug B 호환"이라고 말하지 않는다.** Tahu를 붙여 놓고 페이로드만 JSON으로
쓰면 그건 Sparkplug가 아니고, 그렇게 부르는 순간 이 레포가 가장 피하려는 종류의 과장이
된다. 문서에도 코드에도 **"Sparkplug B에서 birth/death와 seq 개념을 가져왔다"**로 쓴다.

### D4. `seq` — 유실을 관측 가능하게 만든다

발행자가 접속 단위로 0부터 증가시키는 번호를 모든 페이로드에 넣는다. 구독자는 대상별
마지막 `seq`를 들고 있다가 **건너뛴 만큼을 Prometheus 카운터로 올린다.**

- `birth`가 `seq=0`을 싣고, 이후 `status`/`cycle`이 1씩 증가시킨다.
- 구독자가 `seq`가 되감긴 것을 보면(예: 4821 → 3) 발행자가 재접속한 것이다 —
  **birth를 못 받았어도 재접속을 추론할 수 있다.**
- 신규 메트릭: `factory_telemetry_gap_total{equipment}`.

**이게 있어야 D5(store-and-forward)의 효과를 측정할 수 있다.** 지금은 브로커를 내렸다
올려도 몇 건이 사라졌는지 셀 방법이 없어서, 고쳐도 고쳐졌다고 말할 근거가 없다.

### D5. 발행 측 store-and-forward — 디스크 영속화 + **명시적 로컬 큐**

두 단계이고, **1단계만으로는 부족하다는 점을 분명히 적는다.**

1. `MemoryPersistence` → `MqttDefaultFilePersistence`. QoS 1 인플라이트 메시지가
   디스크에 남아 프로세스 재시작을 견딘다. **한 줄 교체다.**
2. 그런데 Paho는 **접속이 끊긴 상태에서의 `publish()`를 큐에 쌓지 않는다** —
   `MqttException`을 던지고, 현재 코드는 그걸 `System.err.println` 한 줄로 삼킨다
   (`FactorySimulator.publish`). 즉 1단계는 "붙어 있는 동안의 신뢰성"만 올린다.
   **브로커 단절 구간을 견디려면 발행 실패를 로컬 파일 큐에 적고 재접속 시 흘려보내는
   코드를 직접 써야 한다.** 이게 Transactional Outbox의 엣지판이고, 이 레포에는 이미
   같은 패턴이 있다 — QMS의 `NotificationSender` 뒤 Outbox 구현체.

**용량 한계를 먼저 정한다.** 무한 큐는 디스크를 채우고 프로세스를 죽인다. 상한
(기본 10,000건 / 설비당)에 닿으면 **가장 오래된 것부터 버리고 버린 수를 카운터로
올린다** — 조용히 버리지 않는다.

### D6. 저장 — TimescaleDB 하이퍼테이블 + 연속 집계

근거는 0-A절에 적은 셋(보존 정책 대체 · 집계 캐시 대체 · 부하 없을 때 전환)이다.
처리량이 아니다.

```sql
-- factory_events 를 하이퍼테이블로. PK가 bigserial 이므로 파티션 키를 포함해야 한다.
SELECT create_hypertable('factory_events', 'occurred_at', migrate_data => true);

-- 벌크 DELETE 잡을 대체한다 — FactoryEventRetentionJob 이 통째로 삭제된다.
SELECT add_retention_policy('factory_events', INTERVAL '90 days');

-- 시프트 OEE 입력을 사전 집계. 조회가 원본을 스캔하지 않는다.
CREATE MATERIALIZED VIEW factory_cycle_hourly
WITH (timescaledb.continuous) AS
SELECT target_id AS equipment_id,
       time_bucket('1 hour', occurred_at) AS bucket,
       count(*) AS cycles,
       count(*) FILTER (WHERE severity = 'WARNING') AS defects
FROM factory_events
WHERE event_type = 'CYCLE_COMPLETED'
GROUP BY equipment_id, bucket;
```

**왜 InfluxDB/ClickHouse가 아닌가.** 이 레포는 이미 PostgreSQL 16 + Flyway +
JPA/QueryDSL 위에 서 있다. Timescale은 확장이라 **마이그레이션이 Flyway SQL 한 장이고
JPA 매핑이 한 줄도 안 바뀐다.** 전용 TSDB로 가면 쓰기 처리량은 더 나오지만(그리고
0-A에 따르면 필요 없지만) 별도 커넥션·별도 쿼리 방언·이중 저장소 운영이 따라온다.
**이 레포가 지불할 이유가 없는 비용이다.**

### D7. 전환은 **양쪽 동시 발행** 브리지로 한다

토픽 변경은 시뮬레이터·구독자·ACL·테스트를 동시에 건드린다. 한 커밋에 다 바꾸면
어디서 깨졌는지 모른다.

```
1단계  발행자가 신·구 토픽에 동시 발행. 구독자는 구 토픽만 구독. (동작 변화 0)
2단계  ACL에 신 토픽 경로 추가. 구 경로 유지.
3단계  구독자를 신 토픽으로 전환. 구 토픽 발행은 유지. (롤백 가능 지점)
4단계  구 토픽 발행·ACL·구독 제거.
```

**ACL을 잊으면 조용히 전부 거부된다.** `acl.conf`는 현재 토픽 모양에 직접 묶여 있고
(`topic write factory/#`), 거부는 클라이언트에 에러를 주지 않는다 — 메시지가 그냥
사라진다. 그래서 2단계를 **독립 단계로 분리**하고, 각 단계마다 "이벤트가 계속 적재되는가"를
확인하고 넘어간다.

### D8. `birth` 페이로드는 설비 마스터를 **중복 정의하지 않는다**

birth에 `idealCycleTimeMs` 같은 마스터 값을 실으면 DB 마스터와 두 곳에 진실이 생긴다.
`FactorySimulator`의 상수가 이미 `equipments.ideal_cycle_time_ms`와 **일치해야 한다는
제약**을 지고 있고(코드 주석), 여기에 세 번째 사본을 만들지 않는다.

birth가 싣는 것은 **접속 사실과 연속성 정보뿐**이다.

```json
{ "seq": 0, "ts": "2026-09-14T02:11:03Z", "kinds": ["status", "cycle"] }
```

`kinds`는 "이 발행자가 앞으로 무엇을 보낼 것인가"이고, 이건 마스터 데이터가 아니라
**이 접속의 성질**이라 중복이 아니다.

### D9. 이 전환의 모든 단계는 **이벤트로 남는다**

`TELEMETRY_GAP_DETECTED`(D4가 건너뛴 seq를 발견) 이벤트 타입을 추가한다. 원칙 1
("이벤트가 단일 진실 공급원")이 파이프라인 자체의 건강에도 적용된다 — 유실을 메트릭으로만
남기면 나중에 "그때 몇 건 빠졌더라"를 되짚을 수 없다.

---

## 4. 작업 패키지

| WP | 내용 | 선행 | 코드 변경 범위 |
|---|---|---|---|
| **WP0** | `seq` 도입 + 구독자 gap 카운터 + `TELEMETRY_GAP_DETECTED` | — | 발행자 2곳, 구독자 2곳, 메트릭 |
| **WP1** | `birth` 발행 + 구독자 처리 | WP0 (`seq=0`이 birth) | 발행자 2곳, `MqttMessageHandler` |
| **WP2** | store-and-forward — 파일 영속화 + 로컬 큐 + 드롭 카운터 | WP0 (효과 측정에 필요) | 시뮬레이터 2곳 |
| **WP3** | UNS 토픽 전환 (D7 4단계) | WP0~2 | 발행자·구독자·`acl.conf`·`mqtt-topics.md` 2장·테스트 |
| **WP4** | TimescaleDB — 하이퍼테이블 + 보존 정책 + 연속 집계 | 독립 (WP0~3과 병행 가능) | Flyway 1장, `FactoryEventRetentionJob` **삭제**, `OeeService` 조회 |

**WP4는 WP0~3과 독립이다.** 저장 변경과 토픽 변경은 서로를 모른다 — 한쪽이 막혀도
다른 쪽이 진행된다.

### 미확인 항목 — 착수 전에 반드시 확인

- [ ] **Railway Postgres 플러그인이 `timescaledb` 확장을 지원하는가.** 지원하지 않으면
      WP4는 Postgres 이미지를 직접 띄우는 서비스로 바꾸는 작업이 선행된다 —
      `docs/deploy-railway.md`에 배포 구조가 있고, 플러그인 교체는 데이터 이관을 동반한다.
      **확인 전에는 WP4의 공수를 추정하지 않는다.**
- [ ] 로컬 `infra/docker-compose.yml`의 `postgres:16` → `timescale/timescaledb:*-pg16`
      교체 시 기존 4개 모듈 DB(`postgres-init`)가 그대로 뜨는가.

---

## 5. 완료 기준

각 항목은 **관측 가능**해야 한다. "구현했다"가 아니라 "이렇게 확인했다"로 적는다.

| WP | 완료 기준 |
|---|---|
| WP0 | 브로커를 30초 내렸다 올린 뒤 `factory_telemetry_gap_total`이 **0이 아닌 값**을 가리킨다 — 지금까지 셀 수 없던 유실이 숫자로 나온다 |
| WP1 | `oee-service`만 재기동해도 설비 8대의 birth를 전부 받는다(retained status와 별개로). 로그에 8건 |
| WP2 | 브로커를 30초 내렸다 올린 뒤 **WP0에서 세던 gap이 0이 된다.** 같은 실험, 다른 결과 — 이게 store-and-forward가 동작한다는 증거다 |
| WP3 | `pixel/plant1/prod-a/#` 한 줄 구독으로 가공구역 설비 4대의 모든 메시지가 잡힌다. `pixel/plant1/#`에 업무 이벤트가 **섞이지 않는다**(D2 검증). 각 전환 단계마다 이벤트 적재가 끊기지 않았다 |
| WP4 | `FactoryEventRetentionJob`과 그 테스트가 **삭제된 상태로** 90일 초과 이벤트가 사라진다. 시프트 OEE 조회가 연속 집계를 타고, 원본과 값이 일치한다 |

---

## 6. 안 하는 것 (그리고 그 이유)

| 안 한다 | 이유 |
|---|---|
| **OPC UA (Milo) 서버/클라이언트** | [1] 디바이스 계층이 없다. 시뮬레이터가 OPC UA로 발행하고 게이트웨이가 다시 MQTT로 바꾸면 **가짜 계층이 하나 늘 뿐** 실제로 변환되는 프로토콜이 없다. 물리 PLC나 Prosys 같은 시뮬레이션 서버를 붙이는 날 다시 연다 |
| **Modbus / MC Protocol / S7** | 같은 이유. 게다가 이 셋은 "게이트웨이가 흡수한다"가 정답이라 이 레포에 넣어도 배우는 게 없다 |
| **Sparkplug B 규격 채택 (Tahu)** | D3. 페이로드 Protobuf화의 편익이 이 레포에 없다 |
| **Kafka / 브로커 브리징 ([4] 계층)** | 단일 플랜트다. 브리징할 상대가 없다 |
| **InfluxDB / ClickHouse / QuestDB** | D6. Postgres 자산을 버릴 이유가 없다 |
| **Grafana 별도 스택** | 대시보드가 이미 있고 STOMP로 실시간 push까지 한다. Grafana를 붙이면 화면이 둘로 갈린다 |

---

## 부록 A. 근거 — 왜 이 선택지들인가

### 프로토콜

| | 계층 | 성격 | 한계 | 이 레포 |
|---|---|---|---|---|
| Modbus TCP/RTU | [1] | 레지스터 주소 읽기, 사실상 메모리 덤프 | 인증·암호화·시각동기 전부 없음 | 미사용 (6절) |
| OPC UA Client/Server | [1]~[2] | 타입 있는 정보 모델 + 보안 | 세션 지향이라 1:N 확산에 약함 | 미사용 (6절) |
| OPC UA PubSub | [2]~[4] | 위 확산 문제 해결판 | 구형 설비 미지원 | 해당 없음 |
| **MQTT** | **[3]** | 플랜트 백본, 1:N pub/sub | 브로커를 24/7 인프라로 다뤄야 함 | **채택 (P8부터)** |
| Sparkplug B | [3] | MQTT 위의 페이로드·생사 규격 | Protobuf 강제 | **개념만 (D3)** |

국내 현장에서는 미쓰비시(MC Protocol)·LS산전(XGT)·지멘스(S7)가 각자 독자 프로토콜이라
게이트웨이에서 OPC UA로 통일하고 그 위를 MQTT로 나르는 조합이 흔하다. 이 레포는
**시뮬레이터가 게이트웨이 출력단을 흉내내는 지점**에서 시작한다(1절).

### 시계열 저장소

| | 성격 | 이 레포에 맞는가 |
|---|---|---|
| SQL Server | 컬럼스토어 + 파티션 SWITCH로 버틸 수 있으나 **다운샘플링·자동 보존이 없다** | 해당 없음 (이 레포는 Postgres) |
| **TimescaleDB** | PostgreSQL 확장. SQL·JPA 그대로. 연속 집계가 다운샘플링을 자동 처리 | **채택 (D6)** |
| InfluxDB 3 / QuestDB | 쓰기 처리량 최상위, SQL 방언 학습 필요 | 처리량이 문제가 아니다 (0-A) |
| ClickHouse | 시계열 + 대규모 분석 겸용 | 분석 요구가 아직 없다 |

### Spring 멘탈 모델 대응

이 레포의 기존 구조와 산업 데이터 개념이 어디서 만나는지.

| 산업 개념 | 이 레포의 대응물 |
|---|---|
| MQTT Broker | 프로세스 밖으로 뺀 `ApplicationEventPublisher` — 발행자가 구독자를 모른다 |
| birth/death 인증서 | LWT(death)는 구현됨. birth는 WP1 |
| Report by Exception | `status` 발행이 이미 변화 구동 (D3-ⓒ) |
| Store-and-forward | Transactional Outbox의 엣지판 — QMS `NotificationSender` 뒤 Outbox와 같은 패턴 (D5) |
| 백프레셔 | D5의 큐 상한 + 드롭 카운터 |
| UNS 토픽 계층 | ISA-95 `Enterprise/Plant/Area/Line/Cell` (D1) |

### 흔한 실패 요인 — 이 문서가 어떻게 피하는가

| 실패 요인 | 이 설계의 대응 |
|---|---|
| 네임스페이스 설계 전에 브로커부터 산다 | 브로커는 P8에 이미 있다. 이 문서가 **뒤늦게 네임스페이스를 설계하는 쪽**이다 — 그 대가가 D7의 4단계 브리지다 |
| UNS를 우회하는 point-to-point 배선을 하나씩 허용 | 원칙 2(모듈 간 직접 참조 금지)가 이미 이걸 막고 있고, ACL이 강제한다 |
| 보안을 나중에 붙인다 | P16에서 이미 했다 (`allow_anonymous false` + ACL + M2M 토큰) |
| UNS를 SCADA 대체로 오해한다 | 이 레포에 제어는 없다. `fleet/{robot}/command`가 유일한 하향 명령이고 그건 배차이지 제어가 아니다 |

### 출처

- [MQTT vs OPC UA vs Modbus: A 2026 Architect's Honest Trade-Off Guide — Crius Software](https://www.criussoftware.com/blogsgrid.php?slug=mqtt-vs-opc-ua-vs-modbus-2026-architects-honest-tradeoff-guide)
- [Unified Namespace (UNS) Architecture: The Definitive 2026 Guide — Anexee](https://www.anexee.com/blog/unified-namespace-uns-architecture-industrial-2026)
- [MQTT vs. REST vs. OPC UA: Which fits modern industrial architecture? — Cirrus Link](https://cirrus-link.com/mqtt-vs-rest-vs-opc-ua-which-fits-modern-industrial-architecture/)
- [The Best Time-Series Databases Compared (2026) — Tiger Data](https://www.tigerdata.com/learn/the-best-time-series-databases-compared)
- [The Best Time-Series Databases in 2026 (and How to Choose) — QuestDB](https://questdb.com/blog/best-time-series-databases/)
