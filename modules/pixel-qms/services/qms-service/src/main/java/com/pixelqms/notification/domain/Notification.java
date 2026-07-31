package com.pixelqms.notification.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발송된 알림 한 건(발송함).
 *
 * <p>실제 SMTP를 붙이지 않는다 — 배포 환경에서 포트가 막히고, 스팸 처리되고, 데모에서 재현이
 * 안 된다. 대신 여기 쌓아 두고 대시보드가 메일 카드로 보여준다.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    /** 어떤 경로로 나갔는지 — OUTBOX(기본) / SMTP. */
    @Column(nullable = false, length = 20)
    private String channel;

    /** 근거 전표(MRB 번호 등). */
    @Column(length = 50)
    private String referenceNo;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    public Notification(String recipient, String subject, String body, String channel, String referenceNo) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.channel = channel;
        this.referenceNo = referenceNo;
        this.sentAt = LocalDateTime.now();
    }
}
