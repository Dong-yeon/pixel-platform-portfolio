package com.pixelfleet.sim.obstacle;

import com.pixelfleet.sim.mqtt.SimMqttClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 통로가 막히는 상황을 만든다 (P20-4) — {@code fleet/layout/{buildingCode}/obstacle}로
 * 발행하고, control-service의 {@code ObstacleService}가 받아 그래프 엣지를 막는다.
 *
 * <p><b>어떤 엣지를 막을지는 여기서 하드코딩된 목록 중 고른다.</b> robot-sim은 fleet의
 * 그래프(factory {@code layout_edges})를 알지 못한다(컴포저블 원칙 — robot-sim은 fleet의
 * DB/그래프에 런타임 의존하지 않는다). {@link com.pixelfleet.task.scheduler.DemoTaskGenerator}
 * 의 {@code FLOWS}가 "실제로 존재하는 흐름"을 손으로 든 목록이듯, 여기도 "실제로 존재하는
 * 엣지"를 손으로 든 목록이다 — 새 엣지가 생겨도(P20-3 신관 등) 이 목록엔 손으로 추가해야
 * 하고, 그건 회귀가 아니다(P20-1에서 이미 정리한 원칙과 같다).
 *
 * <p><b>한 번에 하나만 막는다.</b> 여러 개가 동시에 막히면 데모를 눈으로 따라가기 어렵다.
 */
@Component
@ConditionalOnProperty(name = "sim.obstacle.enabled", havingValue = "true", matchIfMissing = true)
public class ObstacleSimulator {

    private static final Logger log = LoggerFactory.getLogger(ObstacleSimulator.class);

    private record BlockableEdge(String from, String to, String buildingCode) {}

    private static final List<BlockableEdge> BLOCKABLE_EDGES = List.of(
            // P22: JCT-14↔JCT-27 직결 엣지는 게이트 경유 두 구간으로 바뀌었다 — 둘 다 막을 수 있다.
            new BlockableEdge("JCT-14-U", "WH-GATE-U", "WH"),
            new BlockableEdge("WH-GATE-U", "JCT-27-U", "PROD"),
            new BlockableEdge("JCT-27-U", "JCT-34-U", "PROD"),
            new BlockableEdge("JCT-34-U", "JCT-41-U", "PROD"),
            new BlockableEdge("JCT-41-U", "JCT-48-U", "PROD"),
            new BlockableEdge("JCT-4-U", "JCT-4-L", "WH"),
            new BlockableEdge("JCT-14-L", "WH-GATE-L", "WH"),
            new BlockableEdge("WH-GATE-L", "JCT-27-L", "PROD"),
            // P20-3 신관 — 새 건물의 엣지도 똑같이 막을 수 있는지 데모로 보여준다.
            new BlockableEdge("GATE-WH-A", "MACH-1", "BLDG-A"),
            new BlockableEdge("MACH-1", "MACH-2", "BLDG-A"));

    private static final String[] REASONS = {"지게차 통행", "낙하물", "청소 작업", "임시 적재"};

    private final SimMqttClient mqtt;
    private final double spawnProbability;
    private final Duration minDuration;
    private final Duration maxDuration;

    private BlockableEdge active;
    private Instant clearAt;

    public ObstacleSimulator(
            SimMqttClient mqtt,
            @org.springframework.beans.factory.annotation.Value("${sim.obstacle.spawn-probability:0.3}")
            double spawnProbability,
            @org.springframework.beans.factory.annotation.Value("${sim.obstacle.min-duration-seconds:20}")
            long minDurationSeconds,
            @org.springframework.beans.factory.annotation.Value("${sim.obstacle.max-duration-seconds:40}")
            long maxDurationSeconds
    ) {
        this.mqtt = mqtt;
        this.spawnProbability = spawnProbability;
        this.minDuration = Duration.ofSeconds(minDurationSeconds);
        this.maxDuration = Duration.ofSeconds(maxDurationSeconds);
    }

    @Scheduled(fixedDelayString = "${sim.obstacle.check-interval-ms:10000}")
    public void tick() {
        Instant now = Instant.now();

        if (active != null) {
            if (!now.isBefore(clearAt)) {
                publish("OBSTACLE_CLEARED", active, "정리 완료", null);
                active = null;
            }
            return; // 하나가 이미 진행 중이면 새로 만들지 않는다.
        }

        if (ThreadLocalRandom.current().nextDouble() >= spawnProbability) {
            return;
        }

        BlockableEdge edge = BLOCKABLE_EDGES.get(ThreadLocalRandom.current().nextInt(BLOCKABLE_EDGES.size()));
        String reason = REASONS[ThreadLocalRandom.current().nextInt(REASONS.length)];
        long seconds = ThreadLocalRandom.current()
                .nextLong(minDuration.toSeconds(), maxDuration.toSeconds() + 1);
        Instant until = now.plusSeconds(seconds);

        active = edge;
        clearAt = until;
        publish("OBSTACLE_ADDED", edge, reason, until.toString());
    }

    private void publish(String kind, BlockableEdge edge, String reason, String validUntil) {
        String topic = "fleet/layout/" + edge.buildingCode() + "/obstacle";
        Map<String, Object> payload = validUntil == null
                ? Map.of("kind", kind, "fromNode", edge.from(), "toNode", edge.to(), "reason", reason)
                : Map.of("kind", kind, "fromNode", edge.from(), "toNode", edge.to(),
                        "reason", reason, "validUntil", validUntil);
        mqtt.publish(topic, payload);
        log.info("Obstacle: {} {} <-> {} ({})", kind, edge.from(), edge.to(), reason);
    }
}
