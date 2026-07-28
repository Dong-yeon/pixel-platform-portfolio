# simulator

가공 설비 시뮬레이터. LINE-1의 설비 3대(CNC-01, CNC-02, MCT-01)가
사이클 완료/상태 변경 이벤트를 MQTT로 발행한다.

토픽·페이로드 계약: [docs/mqtt-topics.md](../docs/mqtt-topics.md)

## 실행

브로커(Mosquitto)가 떠 있어야 한다: `cd ../infra && docker compose up -d mosquitto`

```powershell
.\gradlew.bat run
```

환경변수:

- `MQTT_URL` — 기본 `tcp://localhost:1883`
- `SIM_SPEED` — 배속, 기본 `10` (30초 사이클을 3초에 발행). 실시간은 `1`.

동작: 설비별 스레드가 사이클타임(이상값의 0.9~1.3배)마다 `cycle` 발행,
불량률 3%, 사이클당 2% 확률로 고장(DOWN 15~45초) 후 복귀.
