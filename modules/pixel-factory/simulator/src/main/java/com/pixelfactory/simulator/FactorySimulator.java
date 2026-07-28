package com.pixelfactory.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * 환경변수:
 *   MQTT_URL   기본 tcp://localhost:1883
 *   SIM_SPEED  배속 (기본 10 — 30초 사이클을 3초에 발행)
 */
public final class FactorySimulator {

    private record EquipmentSpec(String lineCode, String code, int idealCycleTimeMs) {}

    // 설비 마스터(V2·V3 마이그레이션)와 코드·사이클타임이 일치해야 한다.
    private static final List<EquipmentSpec> EQUIPMENTS = List.of(
            // LINE-1 가공
            new EquipmentSpec("LINE-1", "CNC-01", 30000),
            new EquipmentSpec("LINE-1", "CNC-02", 30000),
            new EquipmentSpec("LINE-1", "CNC-03", 30000),
            new EquipmentSpec("LINE-1", "MCT-01", 45000),
            // LINE-2 조립·검사
            new EquipmentSpec("LINE-2", "ASM-01", 25000),
            new EquipmentSpec("LINE-2", "ASM-02", 25000),
            new EquipmentSpec("LINE-2", "INS-01", 20000),
            new EquipmentSpec("LINE-2", "PKG-01", 15000)
    );

    private static final double DEFECT_RATE = 0.03;
    private static final double BREAKDOWN_RATE = 0.02;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String brokerUrl = env("MQTT_URL", "tcp://localhost:1883");
        double speed = Double.parseDouble(env("SIM_SPEED", "10"));

        MqttClient client = new MqttClient(
                brokerUrl,
                "simulator-" + System.currentTimeMillis(),
                new MemoryPersistence()
        );
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        client.connect(options);
        System.out.printf("Connected to %s (speed x%.1f, %d equipments)%n", brokerUrl, speed, EQUIPMENTS.size());

        ExecutorService pool = Executors.newFixedThreadPool(EQUIPMENTS.size());
        for (EquipmentSpec spec : EQUIPMENTS) {
            pool.submit(() -> runEquipment(client, spec, speed));
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pool.shutdownNow();
            try {
                client.disconnect();
                client.close();
            } catch (MqttException ignored) {
                // Shutting down anyway.
            }
        }));
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
                    Thread.sleep((long) ((15000 + random.nextInt(30000)) / speed));
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
        publish(client, topic(spec, "status"), payload);
        System.out.printf("[%s] %s%s%n", spec.code(), status, reason == null ? "" : " (" + reason + ")");
    }

    private static void publishCycle(MqttClient client, EquipmentSpec spec, int cycleTimeMs, boolean defect) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("cycleTimeMs", cycleTimeMs);
        payload.put("defect", defect);
        payload.put("ts", Instant.now().toString());
        publish(client, topic(spec, "cycle"), payload);
    }

    private static String topic(EquipmentSpec spec, String kind) {
        return "factory/" + spec.lineCode() + "/" + spec.code() + "/" + kind;
    }

    private static void publish(MqttClient client, String topic, ObjectNode payload) {
        try {
            MqttMessage message = new MqttMessage(payload.toString().getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
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
