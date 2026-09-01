package com.pixelfactory.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pixelfactory.simulator.ShiftClock.ShiftPhase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * 가공 설비 시뮬레이터.
 *
 * 설비별 스레드가 사이클 완료(cycle)와 상태 변경(status) 이벤트를
 * factory/{lineCode}/{equipmentCode}/{kind} 토픽으로 발행한다.
 * 토픽/페이로드 계약은 docs/mqtt-topics.md 참고.
 *
 * <b>시간 기준이 하나여야 한다.</b> 예전에는 "30초 사이클"이라고 발행하면서 SIM_SPEED=10 으로
 * 3초마다 냈다. OEE는 실시간(occurred_at)으로 계산하므로 실시간 기준으로는 표준CT가 허용하는
 * 양의 10배를 낸 셈이 되어 P가 250~650% 로 나왔다. 지금은 <b>데모 공장을 "빠른 공장"으로
 * 정의</b>하고(사이클 1.5~4.5초) 배속을 쓰지 않는다 — 발행 주기는 전과 같고 P ≈ 1 이 된다.
 *
 * <p><b>P15-2 — {@link ShiftClock}이 정하는 교대 단계도 이 하나의 시간 기준을 그대로
 * 쓴다.</b> factory {@code V5__shift_calendars.sql}의 실제 벽시계 교대(DAY 08:00~17:00,
 * NIGHT 20:00~05:00)와 같은 시각에 SETUP·정기점검(PLANNED_STOP)을 발행한다 — 예전엔
 * 이 두 상태를 아예 발행한 적이 없어 가동률(A)이 노이즈 수준으로만 흔들리고 OEE가
 * 86% 근처에 평평하게 붙었다.
 *
 * 환경변수:
 *   MQTT_URL   기본 tcp://localhost:1883
 *   SIM_SPEED  배속 (기본 1 = 압축하지 않음). 올리면 OEE의 P가 그 배수만큼 부풀려진다.
 */
public final class FactorySimulator {

    private record EquipmentSpec(String lineCode, String code, int idealCycleTimeMs) {}

    /**
     * 설비 마스터({@code equipments.ideal_cycle_time_ms})와 <b>코드·사이클타임이 반드시
     * 일치해야 한다</b> — 어긋나면 OEE의 P가 그 비율만큼 틀어진다.
     * 현재 값은 V6 마이그레이션에서 10배 압축한 것과 같다(데모 시계).
     */
    private static final List<EquipmentSpec> EQUIPMENTS = List.of(
            // LINE-1 가공
            new EquipmentSpec("LINE-1", "CNC-01", 3000),
            new EquipmentSpec("LINE-1", "CNC-02", 3000),
            new EquipmentSpec("LINE-1", "CNC-03", 3000),
            new EquipmentSpec("LINE-1", "MCT-01", 4500),
            // LINE-2 조립·검사
            new EquipmentSpec("LINE-2", "ASM-01", 2500),
            new EquipmentSpec("LINE-2", "ASM-02", 2500),
            new EquipmentSpec("LINE-2", "INS-01", 2000),
            new EquipmentSpec("LINE-2", "PKG-01", 1500)
    );

    private static final double DEFECT_RATE = 0.03;
    private static final double BREAKDOWN_RATE = 0.02;

    /**
     * 고장 지속시간 범위(ms). 사이클타임과 같은 시계에 있어야 한다 —
     * 사이클이 3초인데 고장이 30초면 A가 비현실적으로 떨어진다.
     */
    private static final int BREAKDOWN_MIN_MS = 1500;
    private static final int BREAKDOWN_SPREAD_MS = 3000;

    /** SETUP·PLANNED_STOP 구간에서 "아직 안 끝났나"를 다시 확인하는 주기(P15-2). */
    private static final long PHASE_POLL_MS = 1000;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String brokerUrl = env("MQTT_URL", "tcp://localhost:1883");
        double speed = Double.parseDouble(env("SIM_SPEED", "1"));

        // 설비마다 **별개 접속**을 쓴다. LWT(유언)는 접속당 하나뿐이라, 접속을 공유하면
        // 8대 중 한 대의 status 토픽에만 유언을 걸 수 있다. 실제 현장에서도 설비마다
        // 자기 장치가 브로커에 붙으므로 이쪽이 도메인에도 맞다.
        List<MqttClient> clients = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(EQUIPMENTS.size());

        for (EquipmentSpec spec : EQUIPMENTS) {
            MqttClient client = connect(brokerUrl, spec);
            clients.add(client);
            pool.submit(() -> runEquipment(client, spec, speed));
        }
        System.out.printf("Connected to %s (speed x%.1f, %d equipments)%n", brokerUrl, speed, EQUIPMENTS.size());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pool.shutdownNow();
            for (int i = 0; i < clients.size(); i++) {
                MqttClient client = clients.get(i);
                try {
                    // 정상 종료다 — disconnect() 하면 브로커가 유언을 발행하지 않는다.
                    // 그대로 두면 설비가 마지막 RUNNING 으로 남으니 IDLE 을 남겨
                    // "고장난 게 아니라 멈춘 것"으로 구분되게 한다.
                    publishStatus(client, EQUIPMENTS.get(i), "IDLE", "SIMULATOR_STOPPED");
                    client.disconnect();
                    client.close();
                } catch (MqttException ignored) {
                    // Shutting down anyway.
                }
            }
        }));
    }

    /** 설비 하나 몫의 접속을 만든다. 자기 status 토픽에 유언을 걸어 둔다. */
    private static MqttClient connect(String brokerUrl, EquipmentSpec spec) throws MqttException {
        MqttClient client = new MqttClient(
                brokerUrl,
                "simulator-" + spec.code(),
                new MemoryPersistence()
        );

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        // 유언(LWT) — 비정상 종료(프로세스 강제 종료·네트워크 단절)면 브로커가 대신 발행한다.
        // 없으면 설비가 마지막 RUNNING 상태로 영원히 남아 Availability 가 부풀려진다.
        // retained=true 라 나중에 붙는 서버도 "이 설비는 죽어 있다"를 즉시 알 수 있다.
        // ts 는 넣지 않는다 — 유언은 접속 시점에 브로커에 맡겨 두는 고정 문구라서,
        // 지금 시각을 박으면 실제 죽은 시각과 무관한 값이 된다. 서버가 수신 시각으로 폴백한다.
        options.setWill(
                topic(spec, "status"),
                "{\"status\":\"DOWN\",\"reason\":\"DISCONNECTED\"}".getBytes(StandardCharsets.UTF_8),
                1,
                true
        );

        client.connect(options);
        return client;
    }

    private static void runEquipment(MqttClient client, EquipmentSpec spec, double speed) {
        Random random = new Random();
        // 시작하자마자 지금이 어느 단계인지부터 맞게 잡는다 — 예전엔 언제 켜든 무조건
        // RUNNING으로 발행해, 마침 SETUP·정기점검 창 한복판에 켰으면 첫 몇 초가 거짓으로
        // RUNNING이었다.
        ShiftPhase phase = ShiftClock.currentPhase(LocalTime.now());
        publishPhaseStatus(client, spec, phase);
        ShiftPhase lastPhase = phase;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                ShiftPhase current = ShiftClock.currentPhase(LocalTime.now());
                if (current != lastPhase) {
                    publishPhaseStatus(client, spec, current);
                    lastPhase = current;
                }

                if (current != ShiftPhase.RUNNING) {
                    // SETUP·정기점검 중엔 생산하지 않는다 — 구간이 끝날 때까지 짧게 쉬며
                    // 다시 확인한다(사이클을 돌리면 그 시간에도 생산이 잡혀 A를 깎는
                    // 의도 자체가 무의미해진다).
                    Thread.sleep((long) (PHASE_POLL_MS / speed));
                    continue;
                }

                int cycleTimeMs = (int) (spec.idealCycleTimeMs() * (0.9 + random.nextDouble() * 0.4));
                Thread.sleep((long) (cycleTimeMs / speed));

                boolean defect = random.nextDouble() < DEFECT_RATE;
                publishCycle(client, spec, cycleTimeMs, defect);

                if (random.nextDouble() < BREAKDOWN_RATE) {
                    publishStatus(client, spec, "DOWN", "BREAKDOWN");
                    Thread.sleep((long) ((BREAKDOWN_MIN_MS + random.nextInt(BREAKDOWN_SPREAD_MS)) / speed));
                    // 고장 지속시간(1.5~4.5초)이 우연히 교대 경계와 겹칠 수 있으니, 복귀할
                    // 때도 무조건 RUNNING이 아니라 지금 단계를 다시 물어 발행한다.
                    ShiftPhase afterBreakdown = ShiftClock.currentPhase(LocalTime.now());
                    lastPhase = afterBreakdown;
                    publishPhaseStatus(client, spec, afterBreakdown);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void publishPhaseStatus(MqttClient client, EquipmentSpec spec, ShiftPhase phase) {
        switch (phase) {
            case SETUP -> publishStatus(client, spec, "SETUP", "SHIFT_CHANGEOVER");
            case PLANNED_STOP -> publishStatus(client, spec, "PLANNED_STOP", "SCHEDULED_MAINTENANCE");
            case RUNNING -> publishStatus(client, spec, "RUNNING", null);
        }
    }

    private static void publishStatus(MqttClient client, EquipmentSpec spec, String status, String reason) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", status);
        if (reason != null) {
            payload.put("reason", reason);
        }
        payload.put("ts", Instant.now().toString());
        // status 는 retained — "현재 상태"라서 나중에 붙는 구독자도 즉시 알아야 한다.
        // oee-service 만 재기동해도 브로커가 마지막 상태를 다시 밀어 주므로 상태가 복원된다.
        publish(client, topic(spec, "status"), payload, true);
        System.out.printf("[%s] %s%s%n", spec.code(), status, reason == null ? "" : " (" + reason + ")");
    }

    private static void publishCycle(MqttClient client, EquipmentSpec spec, int cycleTimeMs, boolean defect) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("cycleTimeMs", cycleTimeMs);
        payload.put("defect", defect);
        payload.put("ts", Instant.now().toString());
        // cycle 은 retained 금지 — 지나간 사건이다. retained 로 두면 구독자가 붙을 때마다
        // 마지막 사이클이 한 번 더 배달돼 생산수가 유령으로 늘어난다.
        publish(client, topic(spec, "cycle"), payload, false);
    }

    private static String topic(EquipmentSpec spec, String kind) {
        return "factory/" + spec.lineCode() + "/" + spec.code() + "/" + kind;
    }

    private static void publish(MqttClient client, String topic, ObjectNode payload, boolean retained) {
        try {
            MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            message.setRetained(retained);
            client.publish(topic, message);
        } catch (MqttException e) {
            System.err.println("Failed to publish to " + topic + ": " + e.getMessage());
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
