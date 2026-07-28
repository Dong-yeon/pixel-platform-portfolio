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

    /**
     * 실제 공장의 자재 흐름을 따른다: <b>창고 → 가공(A열) → 조립·검사(B열) → 출하.</b>
     *
     * <p>처음에는 아무 두 지점이나 이었는데, 그러면 한 작업이 공장을 가로질러 경로가 길어지고
     * 서로 겹쳐 동시 주행이 1대로 묶였다(실측). 흐름대로 만들면 경로가 짧아지고, 특히
     * A열↔B열 이송은 같은 세로 연결로만 쓰므로 열이 다르면 <b>서로 전혀 간섭하지 않는다.</b>
     */
    private record Flow(String origin, String destination) {}

    private static final List<Flow> FLOWS = List.of(
            // 자재 투입: 창고 → 가공 라인
            new Flow("WAREHOUSE", "STATION-A1"),
            new Flow("WAREHOUSE", "STATION-A2"),
            new Flow("WAREHOUSE", "STATION-A3"),
            new Flow("WAREHOUSE", "STATION-A4"),
            // 공정 이송: 가공 → 바로 아래 조립·검사 (같은 열 = 서로 간섭 없음)
            new Flow("STATION-A1", "STATION-B1"),
            new Flow("STATION-A2", "STATION-B2"),
            new Flow("STATION-A3", "STATION-B3"),
            new Flow("STATION-A4", "STATION-B4"),
            // 출하: 조립·검사 → 출하장
            new Flow("STATION-B1", "SHIPPING"),
            new Flow("STATION-B2", "SHIPPING"),
            new Flow("STATION-B3", "SHIPPING"),
            new Flow("STATION-B4", "SHIPPING"));

    /**
     * 대기 작업이 이 수를 넘으면 새로 만들지 않는다.
     * 통로를 둘로 나눠 동시 주행이 늘었으므로, 큐가 마르지 않도록 함께 올렸다.
     */
    private static final int MAX_PENDING = 6;

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
        Flow flow = FLOWS.get(rnd.nextInt(FLOWS.size()));

        String code = "T-" + System.currentTimeMillis() % 1_000_000;
        try {
            taskService.create(code, flow.origin(), flow.destination(), randomPriority(rnd));
            log.debug("Demo task {} created: {} -> {}", code, flow.origin(), flow.destination());
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
