package com.pixelwms.fleet;

import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p><b>P24 — M4형 {@code POST /api/orders}(스텝 배열)로 갈아탔다.</b> 예전엔 fleet의
 * 호환 어댑터({@code POST /api/tasks}, 2필드 출발→도착)만 있었다 — 그 어댑터가 내부에서
 * 픽업/하역 2스텝 주문으로 변환해 줬을 뿐이다. 이제 WMS가 그 스텝 2개(pickup/dropoff)를
 * 직접 조립해서 보낸다(설계 근거: docs/p24-m4-order-creation-design.md D3) — fleet 내부
 * 표현(M4 order/step 모델)과 이 클라이언트가 보내는 모양이 같아졌다.
 *
 * <p>반대 방향(완료 통지)은 fleet이 발행하는 MQTT 이벤트를 구독해 받으므로, fleet은 WMS의
 * 존재를 모른다.
 *
 * <p><b>인증:</b> fleet의 `/api/orders`는 인증을 요구하고 플랫폼에 M2M 토큰이 아직 없다.
 * 그래서 서비스 계정으로 로그인해 받은 플랫폼 토큰을 실어 보낸다(모든 모듈이 같은 서명 키).
 * M2M 인증이 생기면 이 클래스만 바꾸면 된다.
 */
@Component
public class FleetTaskClient {

    private static final Logger log = LoggerFactory.getLogger(FleetTaskClient.class);

    private final RestClient restClient;
    private final ServiceTokenProvider tokenProvider;

    public FleetTaskClient(FleetClientProperties properties, ServiceTokenProvider tokenProvider) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
        this.tokenProvider = tokenProvider;
    }

    /**
     * 운송 작업을 만든다 — 픽업(originNode)·하역(destinationNode) 2스텝짜리 M4형 주문(D3).
     *
     * @param materialId 이 운송이 옮기는 물리 단위(P23 D6) — WMS는 파렛트 코드를 싣는다.
     *                   fleet은 이 값을 저장·조회만 한다(배차 로직에 쓰지 않는다).
     * @return 생성된 작업 코드. 완료 통지는 이 값(= 넘긴 taskCode, fleet에는 externalId로
     *         전달됨)으로 온다 — fleet이 내부적으로 어떤 orderCode를 발급했는지는 WMS의
     *         관심사가 아니라 응답 바디를 파싱할 필요가 없다. 실패하면 예외 — 출고지시를
     *         IN_TRANSIT으로 올리기 전에 터져야 "운송 없는 출고"가 생기지 않는다.
     */
    public String createTask(String taskCode, String originNode, String destinationNode, String priority,
                             String materialId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("externalId", taskCode);
            body.put("steps", List.of(
                    Map.of("location", originNode, "forLoad", true, "forUnload", false),
                    Map.of("location", destinationNode, "forLoad", false, "forUnload", true)
            ));
            body.put("priority", priorityValue(priority));
            body.put("stepFixed", true);
            if (materialId != null) {
                body.put("materialId", materialId);
            }

            restClient.post()
                    .uri("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.token())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("fleet 운송 작업 생성: {} ({} → {})", taskCode, originNode, destinationNode);
            return taskCode;
        } catch (Exception exception) {
            log.warn("fleet 운송 작업 생성 실패: {} — {}", taskCode, exception.toString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "운송 작업 생성에 실패했습니다. fleet 상태를 확인하세요.");
        }
    }

    /** LOW/NORMAL/HIGH/URGENT → fleet 관례 정수(0~3, 클수록 높음) — fleet의 CreateTaskRequest와 같은 매핑. */
    private int priorityValue(String priority) {
        return switch (priority) {
            case "LOW" -> 0;
            case "NORMAL" -> 1;
            case "HIGH" -> 2;
            case "URGENT" -> 3;
            default -> 1;
        };
    }
}
