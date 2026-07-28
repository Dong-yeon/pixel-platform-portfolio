package com.pixelfactory.workorder;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 설비마다 진행 중인 작업지시를 하나씩 깔아 둔다.
 *
 * <p>설비 사이클(MQTT)은 <b>진행 중인 작업지시가 있을 때만</b> 생산 실적으로 잡힌다.
 * 이게 없으면 시뮬레이터를 켜도 설비 색만 바뀌고 실적·품질률이 0으로 남아 데모가 비어 보인다.
 * 빈 DB에서만 동작하므로 운영 데이터를 건드리지 않는다.
 */
@Component
@Order(20) // 설비 시드(Flyway) 이후
public class WorkOrderDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderDataInitializer.class);
    private static final int PLANNED_QTY = 500;

    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;

    public WorkOrderDataInitializer(
            WorkOrderRepository workOrderRepository,
            EquipmentRepository equipmentRepository
    ) {
        this.workOrderRepository = workOrderRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @Override
    public void run(String... args) {
        if (workOrderRepository.count() > 0) {
            return;
        }

        List<Equipment> equipments = equipmentRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        int seq = 1;

        for (Equipment equipment : equipments) {
            WorkOrder workOrder = new WorkOrder(
                    String.format("WO-%s-%03d", now.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd")), seq),
                    1L, // itemId — 품목 마스터는 아직 없다(BACKLOG)
                    1L, // processId
                    equipment.getId(),
                    1L, // assignedUserId = admin
                    String.format("LOT-%s-%02d", equipment.getEquipmentCode(), seq),
                    PLANNED_QTY,
                    now,
                    now.plusHours(8)
            );
            workOrder.start(now); // 바로 진행 중으로 — 시뮬레이터 사이클이 실적으로 쌓이도록
            workOrderRepository.save(workOrder);
            seq++;
        }

        log.info("Seeded {} demo work orders (IN_PROGRESS, planned {} each).", seq - 1, PLANNED_QTY);
    }
}
