package com.pixelfactory.workorder;

import com.pixelfactory.equipment.domain.Equipment;
import com.pixelfactory.equipment.repository.EquipmentRepository;
import com.pixelfactory.master.domain.Part;
import com.pixelfactory.master.repository.PartRepository;
import com.pixelfactory.workorder.domain.WorkOrder;
import com.pixelfactory.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    /**
     * 설비가 무엇을 만드는가 — 가공 라인은 반제품, 조립 라인은 완제품 어셈블리.
     *
     * <p>V10 이전에는 여기에 {@code 1L}이 박혀 있었다("품목 마스터는 아직 없다"). 기준정보가
     * 생겼으니 실제 품번을 붙인다. 마이그레이션의 설비-품번 매핑과 같은 값이어야 한다.
     */
    private static final Map<String, String> PART_BY_EQUIPMENT = Map.of(
            "CNC-01", "ITEM-1001",
            "CNC-02", "ITEM-1002",
            "CNC-03", "SEMI-1101",
            "MCT-01", "ITEM-1003",
            "ASM-01", "ASSY-2001",
            "ASM-02", "ASSY-2002",
            "INS-01", "ASSY-2101",
            "PKG-01", "ASSY-2001");

    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final PartRepository partRepository;

    public WorkOrderDataInitializer(
            WorkOrderRepository workOrderRepository,
            EquipmentRepository equipmentRepository,
            PartRepository partRepository
    ) {
        this.workOrderRepository = workOrderRepository;
        this.equipmentRepository = equipmentRepository;
        this.partRepository = partRepository;
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
            Part part = resolvePart(equipment);
            if (part == null) {
                // 기준정보가 없으면 작업지시를 만들지 않는다 — part_id가 FK라 억지로 넣으면 기동이 깨진다.
                log.warn("설비 {}에 맞는 품번이 없어 작업지시를 건너뛴다.", equipment.getEquipmentCode());
                continue;
            }

            WorkOrder workOrder = new WorkOrder(
                    String.format("WO-%s-%03d", now.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd")), seq),
                    part.getId(),
                    1L, // processId — 공정 라우팅은 아직 없다(다음 단계)
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

    /** 매핑에 없는 설비는 아무 품번이나 붙이지 않고 건너뛴다 — 틀린 품번보다 없는 게 낫다. */
    private Part resolvePart(Equipment equipment) {
        String partCode = PART_BY_EQUIPMENT.get(equipment.getEquipmentCode());
        if (partCode == null) {
            return null;
        }
        return partRepository.findByPartCode(partCode).orElse(null);
    }
}
