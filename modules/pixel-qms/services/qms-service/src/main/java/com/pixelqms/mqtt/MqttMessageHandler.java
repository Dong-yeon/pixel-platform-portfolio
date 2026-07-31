package com.pixelqms.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelqms.inspection.service.InspectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * factory 품질 신호를 도메인으로 넘긴다.
 *
 * <p>토픽: {@code factory/quality/inspection-requested}
 * 페이로드: {@code {"equipmentCode","workOrderNo","lotNo","defectQty","ts"}}
 */
@Service
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final InspectionService inspectionService;
    private final ObjectMapper objectMapper;

    public MqttMessageHandler(InspectionService inspectionService, ObjectMapper objectMapper) {
        this.inspectionService = inspectionService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String payload) throws Exception {
        if (!topic.endsWith("/inspection-requested")) {
            log.debug("Ignoring unsupported quality topic: {}", topic);
            return;
        }

        JsonNode json = objectMapper.readTree(payload);
        String workOrderNo = json.path("workOrderNo").asText(null);
        if (workOrderNo == null) {
            log.debug("Ignoring malformed inspection request: {}", payload);
            return;
        }

        inspectionService.createFromFactorySignal(
                json.path("equipmentCode").asText(null),
                workOrderNo,
                json.path("lotNo").asText(null),
                json.path("defectQty").asInt(0)
        );
    }
}
