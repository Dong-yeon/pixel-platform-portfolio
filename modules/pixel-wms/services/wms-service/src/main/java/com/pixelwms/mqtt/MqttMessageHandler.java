package com.pixelwms.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelwms.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * fleet 운송 작업 통지를 도메인으로 넘긴다.
 *
 * <p>토픽: {@code fleet/tasks/{taskCode}/{event}} — event ∈ {completed, failed}.
 * 페이로드: {@code {"taskCode":…,"event":…,"reason":…,"ts":…}}
 *
 * <p>토픽에도 페이로드에도 taskCode가 있지만 <b>페이로드를 신뢰</b>한다 — 토픽 세그먼트는
 * 작업 코드에 {@code /}가 섞이면 쪼개지기 때문이다.
 */
@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String payload) throws Exception {
        JsonNode json = objectMapper.readTree(payload);
        String taskCode = json.path("taskCode").asText(null);
        String event = json.path("event").asText(null);

        if (taskCode == null || event == null) {
            log.debug("Ignoring malformed task notification on {}: {}", topic, payload);
            return;
        }

        switch (event) {
            case "completed" -> orderService.handleTransportCompleted(taskCode);
            // 최종 실패는 아직 전표를 되돌리지 않는다 — 재고를 건드리지 않았으므로 남겨 두고
            // 운영자가 판단한다(재발행/취소). 로그로만 남긴다.
            case "failed" -> log.warn("운송 작업 최종 실패: {} — {}", taskCode, json.path("reason").asText(""));
            default -> log.debug("Ignoring unsupported task event '{}' on {}", event, topic);
        }
    }
}
