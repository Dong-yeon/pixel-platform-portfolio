package com.pixelqms.notification.controller;

import com.pixelqms.notification.domain.Notification;
import com.pixelqms.notification.repository.NotificationRepository;
import com.pixelplatform.core.common.response.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발송함 — 대시보드가 메일 카드로 그린다.
 *
 * <p>실제 SMTP를 붙이지 않은 이유는 {@code Notification} 주석 참고.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final int LIMIT = 50;

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getAll() {
        return ApiResponse.ok(repository.findByOrderBySentAtDesc(PageRequest.of(0, LIMIT))
                .stream().map(NotificationResponse::from).toList());
    }

    public record NotificationResponse(
            Long id, String recipient, String subject, String body,
            String channel, String referenceNo, LocalDateTime sentAt
    ) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getRecipient(), n.getSubject(), n.getBody(),
                    n.getChannel(), n.getReferenceNo(), n.getSentAt());
        }
    }
}
