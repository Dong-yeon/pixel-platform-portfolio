package com.pixelfleet.task.scheduler;

import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.domain.OrderStatus;
import com.pixelfleet.order.repository.FleetOrderRepository;
import com.pixelfleet.order.service.OrderService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 진행이 멈춘 주문을 걷어낸다 — 없으면 <b>함대 전체가 멈춘다.</b>
 *
 * <p>실제로 배포 환경에서 겪은 상황: 배포 중 로봇이 MQTT를 구독하기 전에 GOTO가 발행돼
 * 아무도 받지 못한 주문이 남았다. 그 주문은 ALLOCATED 상태로 굳어 레인 구간을 영원히
 * 점유하고 로봇을 배차에서 계속 제외시켰다 — 로봇 6대가 모두 유휴인데 아무 주문도
 * 배차되지 않았다.
 *
 * <p>두 가지를 감시한다.
 * <ol>
 *   <li><b>배정 ACK 타임아웃</b> — 배차 후 시작 보고가 없는 주문(로봇이 지시를 놓쳤다)</li>
 *   <li><b>진행 타임아웃</b> — 마지막 진행 보고(lastProgressAt: 시작·스텝 완료마다 갱신)가
 *       끊긴 지 오래된 주문. 스텝 단위로 재므로 스텝 많은 주문이 억울하게 걸리지 않는다.</li>
 * </ol>
 * 둘 다 실패 처리한다 — 실패 경로가 점유 해제와 재시도를 담당한다.
 *
 * <p><b>PENDING(미봉인 대기)과 fault(동결)는 쓸지 않는다.</b> 전자는 설계상 기다리는
 * 중이고, 후자는 사람이 retry-failed로 되살릴 때까지 그대로 두는 것이 맞다.
 */
@Component
@ConditionalOnProperty(name = "dispatch.watchdog.enabled", havingValue = "true", matchIfMissing = true)
public class StuckTaskWatchdog {

    private static final Logger log = LoggerFactory.getLogger(StuckTaskWatchdog.class);

    private final OrderService orderService;
    private final FleetOrderRepository orderRepository;

    /** 배차 후 이 시간 안에 시작 보고가 없으면 로봇이 지시를 놓친 것으로 본다. */
    private final long ackTimeoutSeconds;
    /** 진행 보고가 이 시간 이상 끊기면 로봇이 유실된 것으로 본다. */
    private final long runTimeoutSeconds;

    public StuckTaskWatchdog(
            OrderService orderService,
            FleetOrderRepository orderRepository,
            @org.springframework.beans.factory.annotation.Value("${dispatch.watchdog.ack-timeout-seconds:30}")
            long ackTimeoutSeconds,
            @org.springframework.beans.factory.annotation.Value("${dispatch.watchdog.run-timeout-seconds:300}")
            long runTimeoutSeconds
    ) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
        this.runTimeoutSeconds = runTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${dispatch.watchdog.interval-ms:10000}")
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();

        List<FleetOrder> stuck = new ArrayList<>(orderRepository.findByStatusAndFaultFalseAndAssignedAtBefore(
                OrderStatus.ALLOCATED, now.minusSeconds(ackTimeoutSeconds)));
        stuck.forEach(order -> recover(order, "assignment not acknowledged"));

        orderRepository.findByStatusAndFaultFalseAndLastProgressAtBefore(
                        OrderStatus.EXECUTING, now.minusSeconds(runTimeoutSeconds))
                .forEach(order -> recover(order, "no progress reported"));
    }

    private void recover(FleetOrder order, String reason) {
        log.warn("Watchdog: order {} stuck ({}) — releasing robot {} and its lane segments.",
                order.getOrderCode(), reason, order.getAssignedRobotId());
        try {
            // 실패 처리가 점유 해제와 재큐잉(또는 fault 동결)을 함께 수행한다.
            orderService.markFailed(order.getOrderCode(), "watchdog: " + reason);
        } catch (Exception e) {
            log.error("Watchdog could not recover order {}", order.getOrderCode(), e);
        }
    }
}
