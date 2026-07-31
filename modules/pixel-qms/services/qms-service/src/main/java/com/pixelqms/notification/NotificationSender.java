package com.pixelqms.notification;

/**
 * 알림 발송 경로.
 *
 * <p><b>확장점을 인터페이스로 열어 두는 것이 실제 SMTP 연결보다 나은 설계다.</b> 기본 구현
 * {@link OutboxSender}는 DB에 쌓아 대시보드가 메일 카드로 보여주고, 운영에서 진짜 메일이
 * 필요해지면 {@code SmtpSender}를 만들어 프로필/설정으로 갈아끼우면 된다 —
 * 호출부(MRB 서비스)는 바뀌지 않는다.
 */
public interface NotificationSender {

    void send(String recipient, String subject, String body, String referenceNo);
}
