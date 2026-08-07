package com.pixelfleet.realtime.ws;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 클라이언트 → 서버 요청 봉투(M4 스타일). STOMP APP 목적지({@code /app/query})로 들어온다.
 *
 * <p>{@code content}는 action마다 모양이 달라 원시 {@link JsonNode}로 받고, 핸들러가
 * action별로 변환한다 — {@code MqttMessageHandler}가 이미 쓰는 "토픽/필드로 스위치, 페이로드는
 * 각자 파싱" 관례를 전송 계층만 바꿔 그대로 따른 것이다.
 */
public record WsRequest(String id, String action, JsonNode content) {
}
