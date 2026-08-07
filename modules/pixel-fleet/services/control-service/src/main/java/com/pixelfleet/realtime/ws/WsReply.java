package com.pixelfleet.realtime.ws;

/**
 * 서버 → 클라이언트 응답 봉투(M4 스타일). {@code action}은 요청 action에 {@code ::Reply}를
 * 붙인 값(예: {@code RobotsPositionOnly::Query} → {@code RobotsPositionOnly::Query::Reply}),
 * {@code replyToId}는 요청의 {@code id}를 그대로 돌려준다 — 클라이언트가 동시에 여러 질의를
 * 보내도 어느 응답이 어느 요청 것인지 이 값으로 맞춘다.
 */
public record WsReply<T>(String action, String replyToId, T content) {

    public static <T> WsReply<T> to(WsRequest request, T content) {
        return new WsReply<>(request.action() + "::Reply", request.id(), content);
    }
}
