package com.pixelfactory.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * "이벤트가 단일 진실 공급원"(원칙 1)이 진짜로 도는지 — 단위 테스트가 증명 못 하는 부분.
 *
 * <p>실제 Postgres·Mosquitto 컨테이너를 띄우고, 진짜 MQTT 메시지를 발행해서
 * {@link MqttEventSubscriber}(Paho 클라이언트) → {@link MqttMessageHandler} →
 * {@link com.pixelfactory.event.service.FactoryEventService}(트랜잭션) →
 * Flyway로 만든 실제 스키마까지, 이 경로 전체가 실제로 동작하는지 확인한다.
 *
 * <p>단위 테스트들은 이 경로의 각 조각(파서·계산기)이 맞다는 것만 보여준다 — 조각들이
 * 실제로 이어져 있는지는 이런 종단 간 테스트 없이는 "코드상 그래 보인다"에 머문다.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MqttToFactoryEventIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pixelfactory")
            .withUsername("pixel")
            .withPassword("pixel");

    @Container
    static GenericContainer<?> mosquitto = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("testcontainers/mosquitto-test.conf"),
                    "/mosquitto/config/mosquitto.conf");

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("mqtt.enabled", () -> "true");
        registry.add("mqtt.broker-url", () -> "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883));
        // 고정 clientId(oee-service)로 붙으면 앱과 테스트 프로세스가 세션을 다툴 일이
        // 없다 — 앱이 구독자, 여기 test-publisher는 별도 clientId로 발행만 한다.
        registry.add("mqtt.client-id", () -> "oee-service-it-" + System.nanoTime());
    }

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private FactoryEventRepository factoryEventRepository;

    private MqttClient publisher;

    @AfterEach
    void disconnectPublisher() throws Exception {
        if (publisher != null && publisher.isConnected()) {
            publisher.disconnect();
        }
    }

    @Test
    void MQTT_발행부터_이벤트_적재_설비_상태_반영까지_실제_파이프라인으로_돈다() throws Exception {
        // V2 시드 데이터 — CNC-01은 LINE-1 소속, 초기 상태 IDLE.
        Equipment before = equipmentRepository.findByEquipmentCode("CNC-01").orElseThrow();
        assertThat(before.getStatus()).isEqualTo(EquipmentStatus.IDLE);

        Instant statusTs = Instant.now();
        publish("factory/LINE-1/CNC-01/status",
                "{\"status\":\"RUNNING\",\"ts\":\"" + statusTs + "\"}");

        // 1) 상태 이벤트가 실제로 적재되고, 설비 상태가 바뀐다.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Equipment after = equipmentRepository.findByEquipmentCode("CNC-01").orElseThrow();
            assertThat(after.getStatus()).isEqualTo(EquipmentStatus.RUNNING);
        });

        Instant cycleTs = Instant.now();
        publish("factory/LINE-1/CNC-01/cycle",
                "{\"defect\":false,\"ts\":\"" + cycleTs + "\"}");

        // 2) 사이클 이벤트도 적재되고, occurred_at이 payload의 ts와 일치한다
        //    (createdAt이 아니라 occurredAt으로 정렬한다는 FactoryEventRepository의 계약 그대로).
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            boolean cycleRecorded = factoryEventRepository.findByOrderByOccurredAtDesc(
                            org.springframework.data.domain.PageRequest.of(0, 20)).stream()
                    .anyMatch(e -> e.getEventType() == FactoryEventType.CYCLE_COMPLETED);
            assertThat(cycleRecorded).as("CYCLE_COMPLETED 이벤트가 적재돼야 한다").isTrue();
        });

        FactoryEvent cycleEvent = factoryEventRepository
                .findByOrderByOccurredAtDesc(org.springframework.data.domain.PageRequest.of(0, 20)).stream()
                .filter(e -> e.getEventType() == FactoryEventType.CYCLE_COMPLETED)
                .findFirst()
                .orElseThrow();

        // MqttMessageHandler#resolveOccurredAt이 UTC를 시스템 시간대로 변환해 저장한다 —
        // 초 단위까지 일치하는지만 본다(직렬화·파싱 왕복의 밀리초 오차는 여기서 안 본다).
        assertThat(cycleEvent.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant())
                .isCloseTo(cycleTs, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));

        // 3) 상태 이벤트도 EQUIPMENT_STATUS_CHANGED로 남아 있어야 한다 — 폴링 없이 최종 상태만
        // 확인했던 위 1)과 별개로, "상태 변화 자체가 이벤트로 기록됐는가"(원칙 1)를 직접 확인.
        boolean statusRecorded = factoryEventRepository.findByOrderByOccurredAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, 20)).stream()
                .anyMatch(e -> e.getEventType() == FactoryEventType.EQUIPMENT_STATUS_CHANGED);
        assertThat(statusRecorded).isTrue();
    }

    private void publish(String topic, String payload) throws Exception {
        if (publisher == null) {
            publisher = new MqttClient(
                    "tcp://" + mosquitto.getHost() + ":" + mosquitto.getMappedPort(1883),
                    "test-publisher-" + System.nanoTime(),
                    new MemoryPersistence());
            publisher.connect();
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        publisher.publish(topic, message);
    }
}
