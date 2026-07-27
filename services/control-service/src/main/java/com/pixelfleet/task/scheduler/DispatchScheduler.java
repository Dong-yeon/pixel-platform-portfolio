package com.pixelfleet.task.scheduler;

import com.pixelfleet.task.domain.TransportTask;
import com.pixelfleet.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains the pending-task queue by assigning tasks to available robots.
 *
 * <p>Each pass calls {@link TaskService#dispatchOnce()} repeatedly until nothing more can
 * be assigned (no pending tasks, or no free robots). Because {@code TaskService} is a
 * separate bean, every call goes through its {@code @Transactional} proxy — one
 * transaction (and one downlink command) per assignment, no I/O held inside a long tx.
 *
 * <p>Disable with {@code dispatch.enabled=false} (e.g. in tests, or to drive dispatch
 * manually via {@code POST /api/tasks/dispatch}).
 */
@Component
@ConditionalOnProperty(name = "dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class DispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchScheduler.class);

    /** Hard stop so a logic error can never spin forever within a single pass. */
    private static final int MAX_ASSIGNMENTS_PER_PASS = 100;

    private final TaskService taskService;

    public DispatchScheduler(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${dispatch.interval-ms:2000}")
    public void dispatchPending() {
        int assigned = 0;
        while (assigned < MAX_ASSIGNMENTS_PER_PASS) {
            TransportTask task = taskService.dispatchOnce();
            if (task == null) {
                break;
            }
            assigned++;
        }
        if (assigned > 0) {
            log.info("Dispatch pass assigned {} task(s).", assigned);
        }
    }
}
