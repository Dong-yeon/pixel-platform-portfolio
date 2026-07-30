package com.pixelfactory.oee.domain;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 설비가 한 상태로 머문 구간. 상태 <b>이벤트</b>(점)를 <b>구간</b>(선)으로 바꾼 결과다.
 *
 * <p>OEE는 "몇 번 고장났나"가 아니라 "얼마나 돌았나"로 계산하므로, 이벤트 스트림을 반드시
 * 구간으로 바꿔야 한다. 그 변환을 {@link com.pixelfactory.oee.service.StateIntervalAssembler}가 한다.
 */
public record EquipmentStateInterval(
        Long equipmentId,
        EquipmentStatus status,
        LocalDateTime from,
        LocalDateTime to
) {

    public EquipmentStateInterval {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("구간의 끝이 시작보다 앞설 수 없다: " + from + " ~ " + to);
        }
    }

    public Duration duration() {
        return Duration.between(from, to);
    }

    /** 이 구간을 [clipFrom, clipTo] 안으로 자른다. 겹치지 않으면 빈 값. */
    public EquipmentStateInterval clipTo(LocalDateTime clipFrom, LocalDateTime clipTo) {
        LocalDateTime start = from.isAfter(clipFrom) ? from : clipFrom;
        LocalDateTime end = to.isBefore(clipTo) ? to : clipTo;

        if (!start.isBefore(end)) {
            return null;
        }

        return new EquipmentStateInterval(equipmentId, status, start, end);
    }
}
