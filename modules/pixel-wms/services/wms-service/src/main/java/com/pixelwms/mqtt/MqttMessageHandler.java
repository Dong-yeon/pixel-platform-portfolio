package com.pixelwms.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelwms.order.service.OrderService;
import com.pixelwms.order.service.ReplenishmentService;
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
 *
 * <p><b>P26 — 완료는 출고와 보충 양쪽에 다 넘긴다.</b> taskCode는 둘 중 한쪽 소유이지
 * 둘 다일 수 없으므로(각 서비스가 자기 taskCode가 아니면 조용히 무시하는 기존 관례,
 * {@code OrderService.handleTransportCompleted}의 "우리 소관이 아니다" 그대로), 순서는
 * 상관없다.
 */
@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final OrderService orderService;
    private final ReplenishmentService replenishmentService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(OrderService orderService, ReplenishmentService replenishmentService,
                              ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.replenishmentService = replenishmentService;
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
            case "completed" -> {
                orderService.handleTransportCompleted(taskCode);
                replenishmentService.handleTransportCompleted(taskCode);
            }
            // 최종 실패는 아직 전표를 되돌리지 않는다 — 재고를 건드리지 않았으므로 남겨 두고
            // 운영자가 판단한다(재발행/취소). 로그로만 남긴다.
            case "failed" -> log.warn("운송 작업 최종 실패: {} — {}", taskCode, json.path("reason").asText(""));
            default -> log.debug("Ignoring unsupported task event '{}' on {}", event, topic);
        }
    }
}
