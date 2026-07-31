package com.pixelqms.factory;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * factory에 품질 홀드/해제를 요청한다 — QMS가 factory를 아는 유일한 지점.
 *
 * <p>factory의 REST 계약(`/api/quality/**`)만 안다. 반대 방향(검사 요청)은 factory가 발행하는
 * MQTT 신호를 구독해 받으므로, <b>factory는 QMS의 존재를 모른다</b>.
 *
 * <p><b>실패해도 심의는 진행된다.</b> factory가 내려가 있다고 MRB를 못 여는 것은 말이 안 된다 —
 * 홀드는 "요청했지만 반영 안 됨"으로 남기고 로그를 띄운다(컴포저블: 상대의 부재를 견딘다).
 */
@Component
public class FactoryQualityClient {

    private static final Logger log = LoggerFactory.getLogger(FactoryQualityClient.class);

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public FactoryQualityClient(FactoryClientProperties properties, ServiceTokenProvider tokenProvider) {
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
        this.tokenProvider = tokenProvider;
    }

    /** @return 실제로 반영됐으면 true. factory가 없거나 거부하면 false. */
    public boolean hold(String equipmentCode, String workOrderNo, String reason, String referenceNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("equipmentCode", equipmentCode);
        body.put("workOrderNo", workOrderNo);
        body.put("reason", reason);
        body.put("referenceNo", referenceNo);
        return post("/api/quality/hold", body, "품질 홀드");
    }

    public boolean release(String equipmentCode, String workOrderNo, String decision, String referenceNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("equipmentCode", equipmentCode);
        body.put("workOrderNo", workOrderNo);
        body.put("decision", decision);
        body.put("referenceNo", referenceNo);
        return post("/api/quality/release", body, "품질 홀드 해제");
    }

    public void inspectionStarted(String equipmentCode, String workOrderNo, String lotNo, String inspectionNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("equipmentCode", equipmentCode);
        body.put("workOrderNo", workOrderNo);
        body.put("lotNo", lotNo);
        body.put("inspectionNo", inspectionNo);
        post("/api/quality/inspection-started", body, "검사 시작 통지");
    }

    public void inspectionResult(String equipmentCode, String workOrderNo, String lotNo,
                                 String inspectionNo, boolean passed) {
        Map<String, Object> body = new HashMap<>();
        body.put("equipmentCode", equipmentCode);
        body.put("workOrderNo", workOrderNo);
        body.put("lotNo", lotNo);
        body.put("inspectionNo", inspectionNo);
        body.put("passed", passed);
        post("/api/quality/inspection-result", body, "검사 판정 통지");
    }

    private boolean post(String path, Map<String, Object> body, String what) {
        try {
            restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception exception) {
            // factory가 없어도 QMS는 계속 돈다 — 반영되지 않았다는 사실만 남긴다.
            log.warn("{} 실패 ({}): {}", what, path, exception.toString());
            return false;
        }
    }
}
