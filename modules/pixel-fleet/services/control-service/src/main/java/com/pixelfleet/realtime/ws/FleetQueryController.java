package com.pixelfleet.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfleet.realtime.ws.query.RobotPositionOnly;
import com.pixelfleet.realtime.ws.query.RobotsPositionOnlyQuery;
import com.pixelfleet.robot.service.RobotService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WS 요청-응답(P19 나머지 작업) — 지금까지 {@code /ws/fleet}은 서버→클라이언트 단방향
 * push뿐이었다({@link com.pixelfleet.realtime.WebSocketConfig} 참고). 이 컨트롤러가
 * 그린필드로 여는 첫 클라이언트→서버 질의 경로다.
 *
 * <p>표준 {@code @MessageMapping} 경로를 쓴 이유: STOMP CONNECT 인증(P16, 아직 미착수)이
 * 나중에 {@code ChannelInterceptor}로 붙으면 {@code clientInboundChannel}을 지나는 이
 * 메시지도 자동으로 그 게이트를 통과한다 — 이 컨트롤러에 P16 대응 코드를 넣을 필요가 없다.
 *
 * <p><b>응답은 세션 타겟이 아니라 {@code /topic/query-reply} 브로드캐스트다.</b>
 * {@code convertAndSendToUser} + 익명 세션 트릭도 검토했으나, 이 코드베이스 어디에도 선례가
 * 없는 비직관적 배관이다 — 단일 테넌트 데모에서 로봇 위치는 민감정보가 아니므로 단순
 * 브로드캐스트로 시작하고, 클라이언트가 {@code replyToId}로 자기 요청의 응답만 골라낸다.
 * P16이 진짜 {@code Principal}을 주면 세션 타겟으로 올리는 걸 후속 과제로 남긴다.
 */
@Controller
public class FleetQueryController {

    private static final Logger log = LoggerFactory.getLogger(FleetQueryController.class);
    private static final String ROBOTS_POSITION_ONLY = "RobotsPositionOnly::Query";
    private static final String REPLY_DESTINATION = "/topic/query-reply";

    private final RobotService robotService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public FleetQueryController(
            RobotService robotService, SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.robotService = robotService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @MessageMapping("/query")
    public void query(WsRequest request) {
        try {
            switch (request.action()) {
                case ROBOTS_POSITION_ONLY -> handleRobotsPositionOnly(request);
                default -> {
                    log.debug("Unsupported WS query action '{}'", request.action());
                    replyError(request, "unsupported action: " + request.action());
                }
            }
        } catch (Exception e) {
            // 미지원 action처럼 조용히 버리지 않는다 — 요청자가 잘못된 content를 보냈다는
            // 걸 알아야 한다(에러 배지 없이 응답만 안 오면 "요청이 씹혔나?"로 헷갈린다).
            log.warn("WS query '{}' (id={}) failed", request.action(), request.id(), e);
            replyError(request, "query failed: " + e.getMessage());
        }
    }

    private void handleRobotsPositionOnly(WsRequest request) {
        RobotsPositionOnlyQuery query = request.content() == null || request.content().isNull()
                ? new RobotsPositionOnlyQuery(null)
                : objectMapper.convertValue(request.content(), RobotsPositionOnlyQuery.class);

        List<RobotPositionOnly> robots = robotService.findAll().stream()
                .filter(r -> query.floorNo() == null || r.floorNo() == query.floorNo())
                .map(RobotPositionOnly::from)
                .toList();

        messagingTemplate.convertAndSend(REPLY_DESTINATION, WsReply.to(request, robots));
    }

    private void replyError(WsRequest request, String message) {
        messagingTemplate.convertAndSend(REPLY_DESTINATION, WsReply.to(request, Map.of("error", message)));
    }
}
