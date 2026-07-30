package com.pixelfactory.realtime;

import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import com.pixelfactory.oee.service.OeeService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 현재 교대 기준 OEE를 주기적으로 밀어 준다.
 *
 * <p><b>왜 이벤트마다가 아니라 주기인가.</b> OEE는 이벤트 하나로 정해지는 값이 아니라 구간
 * 전체를 다시 집계해야 나온다 — 설비 1대당 상태 이벤트 + 사이클 이벤트를 조회 구간만큼 읽는다.
 * 사이클은 초당 여러 건 들어오므로 이벤트마다 8대를 재계산하면 DB만 두드리고 화면은 사람 눈에
 * 똑같다. 그래서 몇 초 간격으로 한 번씩 계산해 보낸다.
 *
 * <p>설비 상태와 이벤트 타임라인은 반대로 <b>이벤트 시점에</b> 즉시 밀어 준다
 * ({@link RealtimeBroadcaster}) — 고장이 화면에 늦게 뜨면 관제의 의미가 없다.
 */
@Component
@ConditionalOnProperty(name = "realtime.oee-push.enabled", matchIfMissing = true)
public class OeePushScheduler {

    private static final Logger log = LoggerFactory.getLogger(OeePushScheduler.class);

    private final OeeService oeeService;
    private final SimpMessagingTemplate messagingTemplate;

    public OeePushScheduler(OeeService oeeService, SimpMessagingTemplate messagingTemplate) {
        this.oeeService = oeeService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedDelayString = "${realtime.oee-push.interval-ms:5000}")
    public void push() {
        try {
            List<EquipmentOeeResponse> snapshot = oeeService.current(LocalDateTime.now());
            messagingTemplate.convertAndSend(WebSocketConfig.TOPIC_OEE, snapshot);
        } catch (Exception e) {
            // 여기서 예외가 새면 스케줄러가 멈춰 이후 push가 전부 사라진다.
            log.error("Failed to push OEE snapshot", e);
        }
    }
}
