package com.pixelfactory.workorder.service;

import com.pixelfactory.common.exception.BusinessException;
import com.pixelfactory.common.exception.ErrorCode;
import com.pixelfactory.event.domain.EventSeverity;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import com.pixelfactory.event.service.FactoryEventService;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.domain.WorkOrderStatus;
import com.pixelfactory.workorder.dto.WorkOrderCompleteProductionRequest;
import com.pixelfactory.workorder.dto.WorkOrderCreateRequest;
import com.pixelfactory.workorder.dto.WorkOrderHoldRequest;
import com.pixelfactory.workorder.dto.WorkOrderResponse;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final FactoryEventService factoryEventService;

    public WorkOrderService(
            WorkOrderRepository workOrderRepository,
            FactoryEventService factoryEventService
    ) {
        this.workOrderRepository = workOrderRepository;
        this.factoryEventService = factoryEventService;
    }

    @Transactional
    public WorkOrderResponse create(WorkOrderCreateRequest request) {
        validatePlanTime(request.plannedStartAt(), request.plannedEndAt());
        validateUniqueWorkOrderNo(request.workOrderNo());

        WorkOrder workOrder = new WorkOrder(
                request.workOrderNo(),
                request.itemId(),
                request.processId(),
                request.equipmentId(),
                request.assignedUserId(),
                request.lotNo(),
                request.plannedQty(),
                request.plannedStartAt(),
                request.plannedEndAt()
        );

        WorkOrder savedWorkOrder = workOrderRepository.save(workOrder);
        recordWorkOrderEvent(
                savedWorkOrder,
                FactoryEventType.WORK_ORDER_ASSIGNED,
                EventSeverity.INFO,
                "Work order assigned: " + savedWorkOrder.getWorkOrderNo(),
                workOrderPayload(savedWorkOrder)
        );
        return WorkOrderResponse.from(savedWorkOrder);
    }

    public List<WorkOrderResponse> search(WorkOrderStatus status, Long assignedUserId, String lotNo) {
        return workOrderRepository.search(status, assignedUserId, lotNo)
                .stream()
                .map(WorkOrderResponse::from)
                .toList();
    }

    public WorkOrderResponse get(Long id) {
        return WorkOrderResponse.from(getWorkOrder(id));
    }

    public List<WorkOrderResponse> getMyWorkOrders(Long assignedUserId) {
        return workOrderRepository.search(null, assignedUserId, null)
                .stream()
                .map(WorkOrderResponse::from)
                .toList();
    }

    @Transactional
    public WorkOrderResponse start(Long id) {
        WorkOrder workOrder = getWorkOrder(id);
        validateTransition(workOrder.getStatus(), WorkOrderStatus.IN_PROGRESS);

        workOrder.start(LocalDateTime.now());
        recordWorkOrderEvent(
                workOrder,
                FactoryEventType.WORK_ORDER_STARTED,
                EventSeverity.INFO,
                "Work order started: " + workOrder.getWorkOrderNo(),
                workOrderPayload(workOrder)
        );
        recordEquipmentEvent(
                workOrder,
                FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                EventSeverity.INFO,
                "Equipment changed to RUNNING for work order: " + workOrder.getWorkOrderNo(),
                "{\"equipmentStatus\":\"RUNNING\"}"
        );
        return WorkOrderResponse.from(workOrder);
    }

    @Transactional
    public WorkOrderResponse completeProduction(Long id, WorkOrderCompleteProductionRequest request) {
        WorkOrder workOrder = getWorkOrder(id);
        validateTransition(workOrder.getStatus(), WorkOrderStatus.INSPECTION_WAITING);
        validateProductionQty(workOrder.getPlannedQty(), request.producedQty(), request.defectQty());

        workOrder.completeProduction(request.producedQty(), request.defectQty());
        recordWorkOrderEvent(
                workOrder,
                FactoryEventType.PRODUCTION_COMPLETED,
                EventSeverity.SUCCESS,
                "Production completed: " + workOrder.getWorkOrderNo(),
                productionPayload(workOrder)
        );
        return WorkOrderResponse.from(workOrder);
    }

    @Transactional
    public WorkOrderResponse hold(Long id, WorkOrderHoldRequest request) {
        WorkOrder workOrder = getWorkOrder(id);
        validateTransition(workOrder.getStatus(), WorkOrderStatus.ON_HOLD);

        workOrder.hold(request.reason());
        recordWorkOrderEvent(
                workOrder,
                FactoryEventType.WORK_ORDER_ON_HOLD,
                EventSeverity.WARNING,
                "Work order on hold: " + workOrder.getWorkOrderNo(),
                holdPayload(workOrder)
        );
        recordEquipmentEvent(
                workOrder,
                FactoryEventType.EQUIPMENT_STATUS_CHANGED,
                EventSeverity.WARNING,
                "Equipment changed to QUALITY_HOLD for work order: " + workOrder.getWorkOrderNo(),
                "{\"equipmentStatus\":\"QUALITY_HOLD\"}"
        );
        return WorkOrderResponse.from(workOrder);
    }

    @Transactional
    public WorkOrderResponse close(Long id) {
        WorkOrder workOrder = getWorkOrder(id);
        validateTransition(workOrder.getStatus(), WorkOrderStatus.COMPLETED);

        workOrder.close(LocalDateTime.now());
        recordWorkOrderEvent(
                workOrder,
                FactoryEventType.WORK_ORDER_COMPLETED,
                EventSeverity.SUCCESS,
                "Work order completed: " + workOrder.getWorkOrderNo(),
                workOrderPayload(workOrder)
        );
        return WorkOrderResponse.from(workOrder);
    }

    private WorkOrder getWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Work order not found."));
    }

    private void validatePlanTime(LocalDateTime plannedStartAt, LocalDateTime plannedEndAt) {
        if (!plannedEndAt.isAfter(plannedStartAt)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "plannedEndAt must be after plannedStartAt.");
        }
    }

    private void validateUniqueWorkOrderNo(String workOrderNo) {
        if (workOrderRepository.existsByWorkOrderNo(workOrderNo)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "workOrderNo already exists.");
        }
    }

    private void validateProductionQty(int plannedQty, int producedQty, int defectQty) {
        if (defectQty > producedQty) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "defectQty cannot be greater than producedQty.");
        }

        if (producedQty > plannedQty) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "producedQty cannot be greater than plannedQty.");
        }
    }

    private void validateTransition(WorkOrderStatus currentStatus, WorkOrderStatus nextStatus) {
        boolean valid = switch (nextStatus) {
            case IN_PROGRESS -> currentStatus == WorkOrderStatus.READY
                    || currentStatus == WorkOrderStatus.ASSIGNED
                    || currentStatus == WorkOrderStatus.ON_HOLD;
            case INSPECTION_WAITING -> currentStatus == WorkOrderStatus.IN_PROGRESS;
            case ON_HOLD -> currentStatus == WorkOrderStatus.ASSIGNED
                    || currentStatus == WorkOrderStatus.READY
                    || currentStatus == WorkOrderStatus.IN_PROGRESS
                    || currentStatus == WorkOrderStatus.INSPECTION_WAITING;
            case COMPLETED -> currentStatus == WorkOrderStatus.INSPECTION_WAITING
                    || currentStatus == WorkOrderStatus.ON_HOLD;
            default -> false;
        };

        if (!valid) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid work order status transition: " + currentStatus + " -> " + nextStatus
            );
        }
    }

    private void recordWorkOrderEvent(
            WorkOrder workOrder,
            FactoryEventType eventType,
            EventSeverity severity,
            String message,
            String payloadJson
    ) {
        factoryEventService.record(
                eventType,
                SourceType.WORK_ORDER,
                workOrder.getId(),
                TargetType.WORK_ORDER,
                workOrder.getId(),
                workOrder.getId(),
                workOrder.getLotNo(),
                severity,
                message,
                payloadJson
        );
    }

    private void recordEquipmentEvent(
            WorkOrder workOrder,
            FactoryEventType eventType,
            EventSeverity severity,
            String message,
            String payloadJson
    ) {
        factoryEventService.record(
                eventType,
                SourceType.WORK_ORDER,
                workOrder.getId(),
                TargetType.EQUIPMENT,
                workOrder.getEquipmentId(),
                workOrder.getId(),
                workOrder.getLotNo(),
                severity,
                message,
                payloadJson
        );
    }

    private String workOrderPayload(WorkOrder workOrder) {
        return "{\"workOrderNo\":\"" + workOrder.getWorkOrderNo()
                + "\",\"status\":\"" + workOrder.getStatus()
                + "\",\"equipmentId\":" + workOrder.getEquipmentId()
                + ",\"assignedUserId\":" + workOrder.getAssignedUserId()
                + "}";
    }

    private String productionPayload(WorkOrder workOrder) {
        return "{\"workOrderNo\":\"" + workOrder.getWorkOrderNo()
                + "\",\"status\":\"" + workOrder.getStatus()
                + "\",\"plannedQty\":" + workOrder.getPlannedQty()
                + ",\"producedQty\":" + workOrder.getProducedQty()
                + ",\"defectQty\":" + workOrder.getDefectQty()
                + "}";
    }

    private String holdPayload(WorkOrder workOrder) {
        return "{\"workOrderNo\":\"" + workOrder.getWorkOrderNo()
                + "\",\"status\":\"" + workOrder.getStatus()
                + "\",\"holdReason\":\"" + sanitizeJsonValue(workOrder.getHoldReason())
                + "\"}";
    }

    private String sanitizeJsonValue(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
