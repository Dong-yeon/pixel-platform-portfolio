package com.pixelqms.notification;

import com.pixelqms.notification.domain.Notification;
import com.pixelqms.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 발송 경로 — 실제로 보내지 않고 <b>발송함</b>에 쌓는다.
 *
 * <p>{@code qms.notification.sender=smtp} 로 바꾸면 다른 구현이 뜨도록 조건을 걸어 둔다
 * (SmtpSender는 아직 없다 — 확장점만 열어 둔 상태).
 */
@Component
@ConditionalOnProperty(name = "qms.notification.sender", havingValue = "outbox", matchIfMissing = true)
public class OutboxSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);
    private static final String CHANNEL = "OUTBOX";

    private final NotificationRepository notificationRepository;

    public OutboxSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void send(String recipient, String subject, String body, String referenceNo) {
        notificationRepository.save(new Notification(recipient, subject, body, CHANNEL, referenceNo));
        log.info("발송함에 적재: {} → {}", subject, recipient);
    }
}
