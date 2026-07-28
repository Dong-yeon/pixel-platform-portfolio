package com.pixelfleet.task.scheduler;

import com.pixelfleet.task.domain.TaskStatus;
import com.pixelfleet.task.domain.TransportTask;
import com.pixelfleet.task.repository.TransportTaskRepository;
import com.pixelfleet.task.service.TaskService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 진행이 멈춘 작업을 걷어낸다 — 없으면 <b>함대 전체가 멈춘다.</b>
 *
 * <p>실제로 배포 환경에서 겪은 상황: 배포 중 로봇이 MQTT를 구독하기 전에 GOTO가 발행돼
 * 아무도 받지 못한 작업이 남았다. 그 작업은 ASSIGNED 상태로 굳어
 * <ul>
 *   <li>지나갈 <b>레인 구간을 영원히 점유</b>하고 (다른 로봇이 그 통로를 못 씀)</li>
 *   <li>배정된 로봇을 배차 대상에서 계속 제외시킨다</li>
 * </ul>
 * 결과적으로 로봇 6대가 모두 유휴인데 아무 작업도 배차되지 않았다.
 *
 * <p>두 가지를 감시한다.
 * <ol>
 *   <li><b>배정 ACK 타임아웃</b> — 배차 후 시작 보고가 없는 작업(로봇이 지시를 놓쳤다)</li>
 *   <li><b>주행 타임아웃</b> — 시작은 했는데 끝나지 않는 작업(로봇이 죽었거나 유실됐다)</li>
 * </ol>
 * 둘 다 실패로 처리한다. 실패 경로가 이미 점유 해제와 재시도를 담당하므로,
 * 작업은 재시도 예산 안에서 다시 큐에 들어가고 구간과 로봇은 풀려난다.
 */
@Component
@ConditionalOnProperty(name = "dispatch.watchdog.enabled", havingValue = "true", matchIfMissing = true)
public class StuckTaskWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StuckTaskWatchdog.class);

    private final TaskService taskService;
    private final TransportTaskRepository taskRepository;

    /** 배차 후 이 시간 안에 시작 보고가 없으면 로봇이 지시를 놓친 것으로 본다. */
    private final long ackTimeoutSeconds;
    /** 주행이 이 시간을 넘기면 로봇이 유실된 것으로 본다. */
    private final long runTimeoutSeconds;

    public StuckTaskWatchdog(
            TaskService taskService,
            TransportTaskRepository taskRepository,
            @org.springframework.beans.factory.annotation.Value("${dispatch.watchdog.ack-timeout-seconds:30}")
            long ackTimeoutSeconds,
            @org.springframework.beans.factory.annotation.Value("${dispatch.watchdog.run-timeout-seconds:300}")
            long runTimeoutSeconds
    ) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
        this.runTimeoutSeconds = runTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${dispatch.watchdog.interval-ms:10000}")
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();

        List<TransportTask> stuck = new ArrayList<>(taskRepository.findByStatusAndAssignedAtBefore(
                TaskStatus.ASSIGNED, now.minusSeconds(ackTimeoutSeconds)));
        stuck.forEach(task -> recover(task, "assignment not acknowledged"));

        taskRepository.findByStatusAndStartedAtBefore(
                        TaskStatus.IN_PROGRESS, now.minusSeconds(runTimeoutSeconds))
                .forEach(task -> recover(task, "execution timed out"));
    }

    private void recover(TransportTask task, String reason) {
        log.warn("Watchdog: task {} stuck ({}) — releasing robot {} and its lane segments.",
                task.getTaskCode(), reason, task.getAssignedRobotId());
        try {
            // 실패 처리가 점유 해제와 재큐잉을 함께 수행한다.
            taskService.markFailed(task.getTaskCode(), "watchdog: " + reason);
        } catch (Exception e) {
            log.error("Watchdog could not recover task {}", task.getTaskCode(), e);
        }
    }
}
