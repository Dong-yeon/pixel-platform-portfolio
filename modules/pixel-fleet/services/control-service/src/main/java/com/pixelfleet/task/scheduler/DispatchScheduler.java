package com.pixelfleet.task.scheduler;

import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically drains the pending-order queue by assigning orders to available robots.
 *
 * <p>Each pass calls {@link OrderService#dispatchOnce()} repeatedly until nothing more can
 * be assigned (no dispatchable orders, or no free robots). Because {@code OrderService} is a
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

    private final OrderService orderService;

    public DispatchScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${dispatch.interval-ms:2000}")
    public void dispatchPending() {
        // 스텝 경계에서 다음 레그를 기다리는 로봇들을 먼저 풀어 준다 — 이미 절반을 온
        // 주문이 새 주문보다 우선이고, 구간을 빨리 비워 줘야 뒤차도 움직인다.
        orderService.grantPendingNextLegs();

        int assigned = 0;
        while (assigned < MAX_ASSIGNMENTS_PER_PASS) {
            FleetOrder order = orderService.dispatchOnce();
            if (order == null) {
                break;
            }
            assigned++;
        }
        if (assigned > 0) {
            log.info("Dispatch pass assigned {} order(s).", assigned);
        }
    }
}
