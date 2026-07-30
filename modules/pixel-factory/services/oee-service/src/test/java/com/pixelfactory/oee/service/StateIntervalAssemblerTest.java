package com.pixelfactory.oee.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.oee.domain.EquipmentStateInterval;
import com.pixelfactory.oee.service.StateIntervalAssembler.StatusChange;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StateIntervalAssemblerTest {

    private final StateIntervalAssembler assembler = new StateIntervalAssembler();

    private static final Long EQUIPMENT_ID = 1L;
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 7, 30, 8, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 7, 30, 17, 0);

    @Test
    @DisplayName("캐리인: 조회 구간 이전에 시작된 DOWN 이 첫 구간으로 잡힌다 — 빼먹으면 A가 부풀려진다")
    void carryInCoversTheStartOfTheWindow() {
        // 07:30 에 DOWN 이 되어 조회 시작(08:00) 시점에도 DOWN 이었고, 09:00 에 복구됐다.
        List<EquipmentStateInterval> intervals = assembler.assemble(
                EQUIPMENT_ID, FROM, TO,
                EquipmentStatus.DOWN,
                List.of(new StatusChange(LocalDateTime.of(2026, 7, 30, 9, 0), EquipmentStatus.RUNNING))
        );

        assertThat(intervals).hasSize(2);

        EquipmentStateInterval first = intervals.get(0);
        assertThat(first.status()).isEqualTo(EquipmentStatus.DOWN);
        assertThat(first.from()).isEqualTo(FROM);
        assertThat(first.to()).isEqualTo(LocalDateTime.of(2026, 7, 30, 9, 0));
        assertThat(first.duration()).isEqualTo(Duration.ofHours(1));

        EquipmentStateInterval second = intervals.get(1);
        assertThat(second.status()).isEqualTo(EquipmentStatus.RUNNING);
        assertThat(second.to()).isEqualTo(TO);
        assertThat(second.duration()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("캐리인이 없으면 첫 이벤트 전 구간을 만들지 않는다 — 없는 시간을 지어내지 않는다")
    void withoutCarryInTheLeadingGapStaysEmpty() {
        List<EquipmentStateInterval> intervals = assembler.assemble(
                EQUIPMENT_ID, FROM, TO,
                null,
                List.of(new StatusChange(LocalDateTime.of(2026, 7, 30, 9, 0), EquipmentStatus.RUNNING))
        );

        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).from()).isEqualTo(LocalDateTime.of(2026, 7, 30, 9, 0));
    }

    @Test
    @DisplayName("마지막 상태는 조회 구간 끝까지 이어진다 — 변경이 없다는 건 계속 그 상태였다는 뜻")
    void lastStatusExtendsToWindowEnd() {
        List<EquipmentStateInterval> intervals = assembler.assemble(
                EQUIPMENT_ID, FROM, TO,
                EquipmentStatus.RUNNING,
                List.of()
        );

        assertThat(intervals).hasSize(1);
        assertThat(intervals.get(0).duration()).isEqualTo(Duration.ofHours(9));
        assertThat(intervals.get(0).to()).isEqualTo(TO);
    }

    @Test
    @DisplayName("시프트 경계로 자른다: 교대를 가로지르는 구간이 교대 안쪽만 남는다")
    void clipsIntervalsToShiftBoundary() {
        // 06:00~12:00 RUNNING 인데 교대는 08:00~17:00 → 08:00~12:00 만 남아야 한다.
        EquipmentStateInterval crossing = new EquipmentStateInterval(
                EQUIPMENT_ID, EquipmentStatus.RUNNING,
                LocalDateTime.of(2026, 7, 30, 6, 0),
                LocalDateTime.of(2026, 7, 30, 12, 0)
        );

        List<EquipmentStateInterval> clipped = assembler.clipAll(List.of(crossing), FROM, TO);

        assertThat(clipped).hasSize(1);
        assertThat(clipped.get(0).from()).isEqualTo(FROM);
        assertThat(clipped.get(0).duration()).isEqualTo(Duration.ofHours(4));
    }

    @Test
    @DisplayName("교대와 전혀 겹치지 않는 구간은 사라진다")
    void dropsIntervalsOutsideTheWindow() {
        EquipmentStateInterval outside = new EquipmentStateInterval(
                EQUIPMENT_ID, EquipmentStatus.RUNNING,
                LocalDateTime.of(2026, 7, 30, 3, 0),
                LocalDateTime.of(2026, 7, 30, 5, 0)
        );

        assertThat(assembler.clipAll(List.of(outside), FROM, TO)).isEmpty();
    }
}
