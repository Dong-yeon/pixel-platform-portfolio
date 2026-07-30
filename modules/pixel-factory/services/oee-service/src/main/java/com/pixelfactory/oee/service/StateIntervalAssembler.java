package com.pixelfactory.oee.service;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.oee.domain.EquipmentStateInterval;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 상태 변경 이벤트(점)를 상태 구간(선)으로 바꾼다.
 *
 * <p><b>캐리인(carry-in)이 이 클래스의 존재 이유다.</b> 조회 구간 안의 이벤트만 쓰면
 * 구간 시작 시점의 상태를 알 수 없어 첫 이벤트까지의 시간이 통째로 빈다. 설비가 조회 구간
 * 이전부터 DOWN이었다면 그 정지가 사라져 <b>A가 부풀려진다</b> — 지표가 실제보다 좋게 나온다.
 * 그래서 호출자는 조회 시작 이전의 마지막 상태 이벤트 1건을 함께 넘겨야 한다.
 *
 * <p>순수 함수라 DB 없이 테스트할 수 있다.
 */
@Component
public class StateIntervalAssembler {

    /** 상태 변경 1건. 이벤트에서 필요한 것만 뽑은 형태다. */
    public record StatusChange(LocalDateTime at, EquipmentStatus status) {}

    /**
     * @param carryIn 조회 시작 이전의 마지막 상태. {@code null}이면 첫 이벤트 전까지는 상태를
     *                모르는 것으로 보고 구간을 만들지 않는다 — 없는 시간을 지어내지 않는다
     *                (그 결과 분모가 줄어 A가 과대평가될 수 있으므로, 호출자는 되도록 캐리인을 넘긴다).
     * @param changes 조회 구간 안의 상태 변경들. 시간 오름차순이어야 한다.
     */
    public List<EquipmentStateInterval> assemble(
            Long equipmentId,
            LocalDateTime from,
            LocalDateTime to,
            EquipmentStatus carryIn,
            List<StatusChange> changes
    ) {
        List<EquipmentStateInterval> intervals = new ArrayList<>();

        LocalDateTime cursor = from;
        EquipmentStatus current = carryIn;

        for (StatusChange change : changes) {
            if (change.at().isBefore(from) || change.at().isAfter(to)) {
                continue;
            }

            if (current != null && change.at().isAfter(cursor)) {
                intervals.add(new EquipmentStateInterval(equipmentId, current, cursor, change.at()));
            }

            cursor = change.at();
            current = change.status();
        }

        // 마지막 상태는 조회 구간 끝까지 이어진다. "이후에 변경이 없었다 = 계속 그 상태였다"는
        // 뜻이지, 데이터가 없다는 뜻이 아니다.
        if (current != null && cursor.isBefore(to)) {
            intervals.add(new EquipmentStateInterval(equipmentId, current, cursor, to));
        }

        return intervals;
    }

    /**
     * 구간들을 시프트 경계로 잘라 시프트 안쪽만 남긴다.
     *
     * <p>OEE는 시프트 단위로 집계하는데 상태 구간은 시프트를 가로지를 수 있다(예: 야간 교대
     * 시작 전부터 DOWN). 자르지 않으면 시프트 밖 시간이 실가동/정지에 섞여 들어간다.
     */
    public List<EquipmentStateInterval> clipAll(
            List<EquipmentStateInterval> intervals,
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<EquipmentStateInterval> clipped = new ArrayList<>();

        for (EquipmentStateInterval interval : intervals) {
            EquipmentStateInterval piece = interval.clipTo(from, to);
            if (piece != null) {
                clipped.add(piece);
            }
        }

        return clipped;
    }
}
