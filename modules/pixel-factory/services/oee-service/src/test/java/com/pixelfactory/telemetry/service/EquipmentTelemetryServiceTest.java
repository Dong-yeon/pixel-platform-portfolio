package com.pixelfactory.telemetry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.domain.EquipmentStatus;
import com.pixelfactory.equipment.service.EquipmentService;
import com.pixelfactory.event.service.FactoryEventService;
import com.pixelfactory.quality.QualityEvents;
import com.pixelfactory.quality.QualityProperties;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * MQTT 핸들러와 {@code ScenarioController}(P15-1)가 공유하는 핵심 로직 — 불량임계
 * 검사요청이 정확히 한 번만 발행되는지가 특히 중요하다(중복 발행되면 QMS가 중복
 * 검사를 만들 여지가 생긴다).
 */
class EquipmentTelemetryServiceTest {

    private final EquipmentService equipmentService = mock(EquipmentService.class);
    private final FactoryEventService factoryEventService = mock(FactoryEventService.class);
    private final WorkOrderRepository workOrderRepository = mock(WorkOrderRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final QualityProperties qualityProperties = new QualityProperties(); // 기본 임계 3

    private final EquipmentTelemetryService service = new EquipmentTelemetryService(
            equipmentService, factoryEventService, workOrderRepository, eventPublisher, qualityProperties);

    @Test
    void 불량이_임계에_도달하면_검사요청_이벤트가_정확히_한번만_발행된다() {
        Equipment equipment = mock(Equipment.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipmentService.findByCode("CNC-01")).thenReturn(Optional.of(equipment));

        WorkOrder workOrder = new WorkOrder("WO-1", 1L, 1L, 1L, 1L, "LOT-1", 100,
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        workOrder.start(LocalDateTime.now());
        when(workOrderRepository.findFirstByEquipmentIdAndStatusOrderByIdAsc(1L, WorkOrderStatus.IN_PROGRESS))
                .thenReturn(Optional.of(workOrder));

        // 임계(기본 3) 도달 전까지는 요청 안 함.
        service.applyCycle("CNC-01", true, LocalDateTime.now(), "{}");
        service.applyCycle("CNC-01", true, LocalDateTime.now(), "{}");
        verify(eventPublisher, never()).publishEvent(any(QualityEvents.InspectionRequested.class));

        // 3번째로 임계 도달 — 여기서 딱 한 번 발행.
        service.applyCycle("CNC-01", true, LocalDateTime.now(), "{}");
        verify(eventPublisher, times(1)).publishEvent(any(QualityEvents.InspectionRequested.class));

        // 그 이후로 더 불량이 나도 같은 작업지시에는 다시 요청하지 않는다(멱등).
        service.applyCycle("CNC-01", true, LocalDateTime.now(), "{}");
        verify(eventPublisher, times(1)).publishEvent(any(QualityEvents.InspectionRequested.class));
    }

    @Test
    void 진행중_작업지시가_없으면_실적을_반영하지_않는다() {
        Equipment equipment = mock(Equipment.class);
        when(equipment.getId()).thenReturn(2L);
        when(equipmentService.findByCode("CNC-02")).thenReturn(Optional.of(equipment));
        when(workOrderRepository.findFirstByEquipmentIdAndStatusOrderByIdAsc(2L, WorkOrderStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        boolean recorded = service.applyCycle("CNC-02", true, LocalDateTime.now(), "{}");

        assertThat(recorded).isFalse();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 상태_변경을_설비서비스에_위임한다() {
        Equipment equipment = mock(Equipment.class);
        when(equipment.getId()).thenReturn(3L);
        when(equipmentService.findByCode("CNC-03")).thenReturn(Optional.of(equipment));

        service.applyStatus("CNC-03", EquipmentStatus.DOWN, LocalDateTime.now(), "{}");

        verify(equipmentService).changeStatus(3L, EquipmentStatus.DOWN);
    }
}
