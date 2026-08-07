package com.pixelfleet.realtime.ws.query;

/** {@code RobotsPositionOnly::Query}의 content — floorNo를 주면 그 층만, null이면 전체. */
public record RobotsPositionOnlyQuery(Short floorNo) {
}
