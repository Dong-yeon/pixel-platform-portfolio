package com.pixelfactory.scenario.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.telemetry.service.EquipmentTelemetryService;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import com.pixelplatform.core.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScenarioServiceTest {

    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private final EquipmentTelemetryService telemetryService = mock(EquipmentTelemetryService.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);

    private final ScenarioService service =
            new ScenarioService(equipmentService, telemetryService, workOrderRepository);

    @Test
    void 존재하지_않는_설비를_주입하면_예외() {
        when(equipmentService.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.injectBreakdown("NOPE"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 진행중_작업지시가_없으면_불량주입을_조용히_넘기지_않고_거절한다() {
        Equipment equipment = mock(Equipment.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipmentService.findByCode("CNC-01")).thenReturn(Optional.of(equipment));
        when(workOrderRepository.findFirstByEquipmentIdAndStatusOrderByIdAsc(1L, WorkOrderStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.injectDefectBurst("CNC-01", 3))
                .isInstanceOf(BusinessException.class);
        verify(telemetryService, never()).applyCycle(any(), anyBoolean(), any(), any());
    }

    @Test
    void 정상_흐름이면_count만큼_불량사이클을_주입한다() {
        Equipment equipment = mock(Equipment.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipmentService.findByCode("CNC-01")).thenReturn(Optional.of(equipment));
        when(workOrderRepository.findFirstByEquipmentIdAndStatusOrderByIdAsc(1L, WorkOrderStatus.IN_PROGRESS))
                .thenReturn(Optional.of(mock(WorkOrder.class)));

        service.injectDefectBurst("CNC-01", 3);

        verify(telemetryService, times(3)).applyCycle(eq("CNC-01"), eq(true), any(), any());
    }
}
