package com.pixelwms.fleet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * fleet에 운송 작업을 요청한다 — WMS가 fleet을 아는 <b>유일한</b> 지점.
 *
 * <p>fleet의 REST 계약(`POST /api/tasks`)만 안다. 반대 방향(완료 통지)은 fleet이 발행하는
 * MQTT 이벤트를 구독해 받으므로, fleet은 WMS의 존재를 모른다.
 *
 * <p><b>인증:</b> fleet의 `/api/tasks`는 인증을 요구하고 플랫폼에 M2M 토큰이 아직 없다.
 * 그래서 서비스 계정으로 로그인해 받은 플랫폼 토큰을 실어 보낸다(모든 모듈이 같은 서명 키).
 * M2M 인증이 생기면 이 클래스만 바꾸면 된다.
 */
@Component
public class FleetTaskClient {

    private static final Logger log = LoggerFactory.getLogger(FleetTaskClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ServiceTokenProvider tokenProvider;

    public FleetTaskClient(FleetClientProperties properties, ObjectMapper objectMapper,
                           ServiceTokenProvider tokenProvider) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
        this.objectMapper = objectMapper;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 운송 작업을 만든다.
     *
     * @return 생성된 작업 코드(= 넘긴 taskCode). 실패하면 예외 — 출고지시를 IN_TRANSIT으로
     *         올리기 전에 터져야 "운송 없는 출고"가 생기지 않는다.
     */
    public String createTask(String taskCode, String originNode, String destinationNode, String priority) {
        try {
            String body = restClient.post()
                    .uri("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token())
                    .body(Map.of(
                            "taskCode", taskCode,
                            "originNode", originNode,
                            "destinationNode", destinationNode,
                            "priority", priority
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode data = objectMapper.readTree(body).path("data");
            String created = data.path("taskCode").asText(taskCode);
            log.info("fleet 운송 작업 생성: {} ({} → {})", created, originNode, destinationNode);
            return created;
        } catch (Exception exception) {
            log.warn("fleet 운송 작업 생성 실패: {} — {}", taskCode, exception.toString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "운송 작업 생성에 실패했습니다. fleet 상태를 확인하세요.");
        }
    }
}
