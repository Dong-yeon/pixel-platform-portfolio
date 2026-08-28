package com.pixelfactory.scenario.service;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.telemetry.service.EquipmentTelemetryService;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 데모 이벤트 주입 — 시연 중 버튼으로 설비 고장/불량을 강제한다.
 *
 * <p>설계 근거: {@code docs/pixel-platform-roadmap.md} P15("데모 시나리오 러너" —
 * "시연 중 특정 이벤트를 버튼으로 주입"). 시뮬레이터는 순수 MQTT 발행 전용 프로세스라
 * 외부에서 명령할 방법이 전혀 없다 — {@link EquipmentTelemetryService}(MQTT 핸들러가
 * 쓰는 것과 완전히 같은 로직)를 직접 호출해 우회한다.
 *
 * <p><b>주입된 이벤트는 payload에 표시를 남긴다</b>({@code "injected": true}) — 실제
 * 텔레메트리와 구분해 타임라인을 정직하게 유지한다(지도가 없는 데이터를 지어내지 않는
 * 것과 같은 원칙).
 *
 * <p><b>알려진 한계.</b> 시뮬레이터는 자기 내부 Random 상태와 무관하게 여기서 DB만
 * 직접 바꾸는 걸 모른다 — 시뮬레이터가 스스로 다음 상태를 발행하면 주입한 상태를
 * 덮어쓸 수 있다. 데모 세션(수 분) 동안은 문제없지만 영구 정합성 보장은 아니다.
 */
@Service
public class ScenarioService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);

    private final EquipmentService equipmentService;
    private final EquipmentTelemetryService telemetryService;
    private final WorkOrderRepository workOrderRepository;

    public ScenarioService(
            EquipmentService equipmentService,
            EquipmentTelemetryService telemetryService,
            WorkOrderRepository workOrderRepository
    ) {
        this.equipmentService = equipmentService;
        this.telemetryService = telemetryService;
        this.workOrderRepository = workOrderRepository;
    }

    /** 고장 주입 — 설비를 DOWN으로 강제 전환한다. */
    public void injectBreakdown(String equipmentCode) {
        requireEquipment(equipmentCode);
        telemetryService.applyStatus(equipmentCode, EquipmentStatus.DOWN, now(), injectedStatusPayload("DOWN"));
        log.info("시나리오 주입 — 고장: {}", equipmentCode);
    }

    /** 복구 — 설비를 RUNNING으로 강제 전환한다. */
    public void injectRecover(String equipmentCode) {
        requireEquipment(equipmentCode);
        telemetryService.applyStatus(equipmentCode, EquipmentStatus.RUNNING, now(), injectedStatusPayload("RUNNING"));
        log.info("시나리오 주입 — 복구: {}", equipmentCode);
    }

    /**
     * 불량 폭주 주입 — 진행 중(IN_PROGRESS) 작업지시에 불량 사이클을 {@code count}회
     * 반복 기록한다. 임계(기본 3)를 넘으면 검사요청이 실제로 발행돼 QMS가 NCR/MRB를
     * 만든다.
     *
     * @throws BusinessException 그 설비에 진행 중인 작업지시가 없으면(조용히 무시하지
     *         않는다 — 시연자가 왜 아무 반응이 없는지 알아야 한다)
     */
    public void injectDefectBurst(String equipmentCode, int count) {
        Equipment equipment = requireEquipment(equipmentCode);
        boolean hasInProgress = workOrderRepository
                .findFirstByEquipmentIdAndStatusOrderByIdAsc(equipment.getId(), WorkOrderStatus.IN_PROGRESS)
                .isPresent();
        if (!hasInProgress) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "설비 " + equipmentCode + "에 진행 중인 작업지시가 없습니다. "
                            + "POP에서 작업을 먼저 착수해야 불량 주입이 실적에 반영됩니다.");
        }
        for (int i = 0; i < count; i++) {
            telemetryService.applyCycle(equipmentCode, true, now(), injectedCyclePayload());
        }
        log.info("시나리오 주입 — 불량 {}회: {}", count, equipmentCode);
    }

    private Equipment requireEquipment(String equipmentCode) {
        return equipmentService.findByCode(equipmentCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "설비를 찾을 수 없습니다: " + equipmentCode));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private static String injectedStatusPayload(String status) {
        return "{\"status\":\"" + status + "\",\"reason\":\"SCENARIO_INJECTED\",\"injected\":true,\"ts\":\""
                + Instant.now() + "\"}";
    }

    private static String injectedCyclePayload() {
        return "{\"defect\":true,\"injected\":true,\"ts\":\"" + Instant.now() + "\"}";
    }
}
