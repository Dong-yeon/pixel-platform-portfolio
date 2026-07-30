package com.pixelfactory.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
        publishStatus(client, spec, "RUNNING", null);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                int cycleTimeMs = (int) (spec.idealCycleTimeMs() * (0.9 + random.nextDouble() * 0.4));
                Thread.sleep((long) (cycleTimeMs / speed));

                boolean defect = random.nextDouble() < DEFECT_RATE;
                publishCycle(client, spec, cycleTimeMs, defect);

                if (random.nextDouble() < BREAKDOWN_RATE) {
                    publishStatus(client, spec, "DOWN", "BREAKDOWN");
                    Thread.sleep((long) ((BREAKDOWN_MIN_MS + random.nextInt(BREAKDOWN_SPREAD_MS)) / speed));
                    publishStatus(client, spec, "RUNNING", null);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
