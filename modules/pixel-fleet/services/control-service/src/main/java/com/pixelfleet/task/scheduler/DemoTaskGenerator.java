package com.pixelfleet.task.scheduler;

import com.pixelfleet.task.domain.TaskPriority;
import com.pixelfleet.task.domain.TaskStatus;
import com.pixelfleet.task.repository.TransportTaskRepository;
import com.pixelfleet.task.service.TaskService;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 데모용 운송 작업 생성기 — 공장에 자재가 계속 흐르는 상황을 만든다.
 *
 * <p>교통 통제를 도입하면서 로봇의 자율 순찰(roam)을 껐다. 모든 이동이 서버가 경로와
 * 구간 점유를 통제하는 작업이어야 하기 때문이다. 그러면 작업이 없을 때 로봇이 멈춰 있으므로,
 * 대신 서버가 주기적으로 작업을 만들어 흐름을 유지한다.
 *
 * <p>대기 중인 작업이 쌓여 있으면 만들지 않는다 — 배차가 막혀 있을 때(구간 경합 등)
 * 큐만 무한히 부풀리지 않도록.
 */
@Component
@ConditionalOnProperty(name = "demo.task-generator.enabled", havingValue = "true", matchIfMissing = true)
public class DemoTaskGenerator {

    private static final Logger log = LoggerFactory.getLogger(DemoTaskGenerator.class);

    /** 자재를 주고받는 지점들. 창고·출하와 각 라인의 하역 지점. */
    private static final List<String> NODES = List.of(
            "WAREHOUSE", "SHIPPING",
            "STATION-A1", "STATION-A2", "STATION-A3", "STATION-A4",
            "STATION-B1", "STATION-B2", "STATION-B3", "STATION-B4");

    /** 대기 작업이 이 수를 넘으면 새로 만들지 않는다. */
    private static final int MAX_PENDING = 3;

    private final TaskService taskService;
    private final TransportTaskRepository taskRepository;

    public DemoTaskGenerator(TaskService taskService, TransportTaskRepository taskRepository) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
    }

    @Scheduled(fixedDelayString = "${demo.task-generator.interval-ms:15000}")
    public void generate() {
        if (taskRepository.findByStatusOrderByIdAsc(TaskStatus.PENDING).size() >= MAX_PENDING) {
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String origin = NODES.get(rnd.nextInt(NODES.size()));
        String destination;
        do {
            destination = NODES.get(rnd.nextInt(NODES.size()));
        } while (destination.equals(origin));

        String code = "T-" + System.currentTimeMillis() % 1_000_000;
        try {
            taskService.create(code, origin, destination, randomPriority(rnd));
            log.debug("Demo task {} created: {} -> {}", code, origin, destination);
        } catch (Exception e) {
            log.debug("Skipped demo task creation: {}", e.getMessage());
        }
    }

    private TaskPriority randomPriority(ThreadLocalRandom rnd) {
        int r = rnd.nextInt(100);
        if (r < 10) {
            return TaskPriority.URGENT;
        }
        if (r < 30) {
            return TaskPriority.HIGH;
        }
        return TaskPriority.NORMAL;
    }
}
