package com.pixelfactory.oee.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.equipment.repository.ProductionLineRepository;
import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.repository.FactoryEventRepository;
import com.pixelfactory.oee.domain.EquipmentStateInterval;
import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeResult;
import com.pixelfactory.oee.domain.ShiftCalendar;
import com.pixelfactory.oee.domain.ShiftOccurrence;
import com.pixelfactory.oee.dto.EquipmentOeeResponse;
import com.pixelfactory.oee.dto.LineOeeResponse;
import com.pixelfactory.oee.repository.ShiftCalendarRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OEE 집계 — 이벤트 스트림에서 지표를 파생시킨다. 저장된 OEE 값은 없다.
 *
 * <p>흐름: 상태 이벤트 → {@link StateIntervalAssembler}로 구간화(캐리인 포함) →
 * 교대 달력으로 계획가동시간 산출 → 사이클 이벤트로 생산·불량 집계 →
 * {@link OeeCalculator}로 A×P×Q.
 *
 * <p><b>상태와 불량 여부는 payload JSON에서 읽는다.</b> 둘 다 컬럼이 아니기 때문이다.
 * severity(불량→WARNING)로 대신 세는 방법도 있지만, severity는 표시용에 가까워 매핑이 바뀌면
 * Q가 조용히 틀어진다. 대신 조회 구간의 사이클 이벤트를 실제로 읽어야 하므로 구간이 길면
 * 비용이 커진다 — 규모가 커지면 상태 이력·사이클 집계용 읽기 모델을 따로 두는 게 다음 수다(백로그).
 */
@Service
@Transactional(readOnly = true)
public class OeeService {

    private static final Logger log = LoggerFactory.getLogger(OeeService.class);

    private final EquipmentRepository equipmentRepository;
    private final ProductionLineRepository productionLineRepository;
    private final FactoryEventRepository factoryEventRepository;
    private final ShiftCalendarRepository shiftCalendarRepository;
    private final StateIntervalAssembler intervalAssembler;
    private final ShiftWindowResolver shiftWindowResolver;
    private final OeeCalculator calculator;
    private final IdealCycleTimeProvider idealCycleTimeProvider;
    private final ObjectMapper objectMapper;

    public OeeService(
            EquipmentRepository equipmentRepository,
            ProductionLineRepository productionLineRepository,
            FactoryEventRepository factoryEventRepository,
            ShiftCalendarRepository shiftCalendarRepository,
            StateIntervalAssembler intervalAssembler,
            ShiftWindowResolver shiftWindowResolver,
            OeeCalculator calculator,
            IdealCycleTimeProvider idealCycleTimeProvider,
            ObjectMapper objectMapper
    ) {
        this.equipmentRepository = equipmentRepository;
        this.productionLineRepository = productionLineRepository;
        this.factoryEventRepository = factoryEventRepository;
        this.shiftCalendarRepository = shiftCalendarRepository;
        this.intervalAssembler = intervalAssembler;
        this.shiftWindowResolver = shiftWindowResolver;
        this.calculator = calculator;
        this.idealCycleTimeProvider = idealCycleTimeProvider;
        this.objectMapper = objectMapper;
    }

    public EquipmentOeeResponse ofEquipment(String equipmentCode, LocalDateTime from, LocalDateTime to) {
        Equipment equipment = equipmentRepository.findByEquipmentCode(equipmentCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "설비를 찾을 수 없습니다: " + equipmentCode));

        return EquipmentOeeResponse.of(equipment, from, to, compute(equipment, from, to));
    }

    /**
     * 라인 OEE.
     *
     * <p><b>설비 OEE의 평균이 아니다.</b> 평균은 계획가동시간이 다른 설비를 같은 무게로 다뤄
     * 조금 돌린 설비가 지표를 흔든다. 여기서는 <b>라인 단위 카운트로 다시 계산</b>한다 —
     * 시간과 수량을 모두 합쳐 A = Σ실가동/Σ계획가동, P = Σ(표준CT×생산)/Σ실가동,
     * Q = Σ양품/Σ생산으로 낸다. 설비별 표준CT가 달라도 P의 분자에서 각각 곱해 합하므로 유효하다.
     *
     * <p>(대안은 병목 설비 기준인데, 병목을 식별할 공정 순서 정보가 아직 없다.)
     */
    public LineOeeResponse ofLine(String lineCode, LocalDateTime from, LocalDateTime to) {
        Long lineId = productionLineRepository.findByLineCode(lineCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "라인을 찾을 수 없습니다: " + lineCode))
                .getId();

        List<Equipment> equipments = equipmentRepository.findByLineId(lineId);
        if (equipments.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "라인에 설비가 없습니다: " + lineCode);
        }

        Duration planned = Duration.ZERO;
        Duration operating = Duration.ZERO;
        long produced = 0;
        long defects = 0;
        // P의 분자(표준CT × 생산수)는 설비별로 표준CT가 다르므로 각각 곱해 누적한다.
        long idealOutputMs = 0;
        List<EquipmentOeeResponse> perEquipment = new ArrayList<>();

        for (Equipment equipment : equipments) {
            OeeResult result = compute(equipment, from, to);
            perEquipment.add(EquipmentOeeResponse.of(equipment, from, to, result));

            planned = planned.plus(result.plannedTime());
            operating = operating.plus(result.operatingTime());
            produced += result.producedQty();
            defects += result.defectQty();
            idealOutputMs += idealCycleTimeProvider.idealCycleTimeMs(equipment.getId(), null) * result.producedQty();
        }

        double availability = planned.isZero() ? 0 : (double) operating.getSeconds() / planned.getSeconds();
        double performance = operating.isZero() ? 0 : (double) idealOutputMs / operating.toMillis();
        double quality = produced == 0 ? 0 : (double) (produced - defects) / produced;

        OeeResult lineResult = new OeeResult(
                availability, performance, quality,
                availability * performance * quality,
                performance > 1.0,
                planned, operating, produced, defects
        );

        return new LineOeeResponse(lineCode, from, to, lineResult, perEquipment);
    }

    /** 현재 교대 기준 전 설비 요약. 대시보드가 쓴다. */
    public List<EquipmentOeeResponse> current(LocalDateTime now) {
        List<Equipment> equipments = equipmentRepository.findAll();
        List<EquipmentOeeResponse> responses = new ArrayList<>();

        for (Equipment equipment : equipments) {
            ShiftOccurrence shift = shiftWindowResolver.currentShift(
                    shiftCalendarRepository.findByLineId(equipment.getLineId()), now);

            // 교대 밖이면 계획가동시간이 없다 = 평가 대상이 아니다. 0을 내보내되 구간은 밝힌다.
            LocalDateTime from = shift == null ? now : shift.start();
            LocalDateTime to = shift == null ? now : (now.isBefore(shift.end()) ? now : shift.end());

            responses.add(EquipmentOeeResponse.of(equipment, from, to, compute(equipment, from, to)));
        }

        return responses;
    }

    // ---- 내부 ----

    private OeeResult compute(Equipment equipment, LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to)) {
            return OeeResult.notApplicable(Duration.ZERO, Duration.ZERO);
        }

        List<EquipmentStateInterval> intervals = stateIntervals(equipment.getId(), from, to);

        List<ShiftCalendar> calendars = shiftCalendarRepository.findByLineId(equipment.getLineId());
        List<ShiftOccurrence> shifts = shiftWindowResolver.resolve(calendars, from, to);

        // 계획가동시간: 교대 ∩ 조회구간 − 휴식(비례) − PLANNED_STOP(교대 안쪽만)
        Duration planned = shiftWindowResolver.plannedTime(shifts, from, to)
                .minus(durationWithinShifts(intervals, shifts, EquipmentStatus.PLANNED_STOP));
        if (planned.isNegative()) {
            planned = Duration.ZERO;
        }

        // 실가동시간: RUNNING ∩ 교대. 교대 밖 가동은 계획에 없던 생산이라 A에 넣지 않는다.
        Duration operating = durationWithinShifts(intervals, shifts, EquipmentStatus.RUNNING);

        CycleCount cycles = countCycles(equipment.getId(), from, to);

        return calculator.calculate(new OeeInput(
                planned,
                operating,
                idealCycleTimeProvider.idealCycleTimeMs(equipment.getId(), null),
                cycles.produced(),
                cycles.defects()
        ));
    }

    private List<EquipmentStateInterval> stateIntervals(Long equipmentId, LocalDateTime from, LocalDateTime to) {
        // 캐리인 — 조회 시작 이전의 마지막 상태. 없으면 첫 이벤트까지 구간을 만들지 않는다.
        EquipmentStatus carryIn = factoryEventRepository
                .findFirstByEventTypeAndTargetTypeAndTargetIdAndOccurredAtLessThanOrderByOccurredAtDesc(
                        FactoryEventType.EQUIPMENT_STATUS_CHANGED, TargetType.EQUIPMENT, equipmentId, from)
                .map(this::readStatus)
                .orElse(null);

        List<StateIntervalAssembler.StatusChange> changes = factoryEventRepository
                .findByEventTypeAndTargetTypeAndTargetIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        FactoryEventType.EQUIPMENT_STATUS_CHANGED, TargetType.EQUIPMENT, equipmentId, from, to)
                .stream()
                .map(event -> {
                    EquipmentStatus status = readStatus(event);
                    return status == null ? null : new StateIntervalAssembler.StatusChange(event.getOccurredAt(), status);
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return intervalAssembler.assemble(equipmentId, from, to, carryIn, changes);
    }

    /**
     * 해당 상태 구간들 중 <b>생산 창(교대 − 휴식) 안쪽만</b> 합산한다.
     *
     * <p>계획가동시간도 같은 창에서 재므로 실가동이 계획을 넘을 수 없다. 교대 전체를 기준으로
     * 재면 휴식 중 가동이 분자에만 들어가 A가 100%를 넘는다(실제로 109%가 나왔다).
     */
    private Duration durationWithinShifts(
            List<EquipmentStateInterval> intervals,
            List<ShiftOccurrence> shifts,
            EquipmentStatus status
    ) {
        Duration total = Duration.ZERO;

        for (EquipmentStateInterval interval : intervals) {
            if (interval.status() != status) {
                continue;
            }
            for (ShiftOccurrence shift : shifts) {
                for (ShiftOccurrence.Window window : shift.productionWindows()) {
                    EquipmentStateInterval piece = interval.clipTo(window.from(), window.to());
                    if (piece != null) {
                        total = total.plus(piece.duration());
                    }
                }
            }
        }

        return total;
    }

    private record CycleCount(long produced, long defects) {}

    private CycleCount countCycles(Long equipmentId, LocalDateTime from, LocalDateTime to) {
        List<FactoryEvent> events = factoryEventRepository
                .findByEventTypeAndTargetTypeAndTargetIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        FactoryEventType.CYCLE_COMPLETED, TargetType.EQUIPMENT, equipmentId, from, to);

        long produced = events.size();
        long defects = events.stream().filter(this::isDefect).count();

        return new CycleCount(produced, defects);
    }

    private boolean isDefect(FactoryEvent event) {
        JsonNode payload = readPayload(event);
        return payload != null && payload.path("defect").asBoolean(false);
    }

    private EquipmentStatus readStatus(FactoryEvent event) {
        JsonNode payload = readPayload(event);
        if (payload == null) {
            return null;
        }

        String status = payload.path("status").asText(null);
        if (status == null) {
            return null;
        }

        try {
            return EquipmentStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            // 계약에 없는 상태값. 구간을 지어내지 않고 건너뛴다.
            log.warn("Unknown status '{}' in event {} — skipping for OEE", status, event.getId());
            return null;
        }
    }

    private JsonNode readPayload(FactoryEvent event) {
        String json = event.getPayloadJson();
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Unparseable payload in event {} — skipping for OEE", event.getId());
            return null;
        }
    }

}
