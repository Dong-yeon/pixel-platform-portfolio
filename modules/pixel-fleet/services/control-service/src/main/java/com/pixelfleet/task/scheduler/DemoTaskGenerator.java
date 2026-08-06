package com.pixelfleet.task.scheduler;

import com.pixelfleet.order.domain.OrderStatus;
import com.pixelfleet.order.repository.FleetOrderRepository;
import com.pixelfleet.order.service.OrderService;
import com.pixelfleet.order.service.OrderService.StepSpec;
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
     * 실제 공장의 자재 흐름을 따른다:
     * <b>창고동 → 가공(A열) → 조립·검사(B열) → 품질동(전수 검사) → 합격은 창고동 / 불합격은 재작업.</b>
     *
     * <p>처음에는 아무 두 지점이나 이었는데, 그러면 한 작업이 공장을 가로질러 경로가 길어지고
     * 서로 겹쳐 동시 주행이 1대로 묶였다(실측). 흐름대로 만들면 경로가 짧아지고, 특히
     * A열↔B열 이송은 같은 세로 연결로만 쓰므로 열이 다르면 <b>서로 전혀 간섭하지 않는다.</b>
     *
     * <p><b>가공품은 예외 없이 품질동을 거친다.</b> 그래서 B열 → QC-IN 이 네 개 다 있고,
     * 출하장으로 곧장 가는 흐름은 없다 — 검사를 건너뛴 물건이 나가는 그림을 만들지 않는다.
     *
     * <p>창고동 위층(2·3층)이 섞여 있는 흐름은 <b>출발지와 목적지의 층이 다르다.</b>
     * 그건 여기서 신경 쓰지 않는다 — {@code TaskService}가 엘리베이터에서 두 구간으로 끊는다.
     * 여기는 "물건이 어디서 어디로 가야 하는가"만 말한다.
     */
    private record Flow(String origin, String destination) {}

    private static final List<Flow> FLOWS = List.of(
            // 자재 투입: 창고동 피킹존 → 가공 라인
            new Flow("WH-PICK", "PROD-A1"),
            new Flow("WH-PICK", "PROD-A2"),
            new Flow("WH-PICK", "PROD-A3"),
            new Flow("WH-PICK", "PROD-A4"),
            // 공정 이송: 가공 → 바로 아래 조립·검사 (같은 열 = 서로 간섭 없음)
            new Flow("PROD-A1", "PROD-B1"),
            new Flow("PROD-A2", "PROD-B2"),
            new Flow("PROD-A3", "PROD-B3"),
            new Flow("PROD-A4", "PROD-B4"),
            // 검사 입고: 조립·검사가 끝난 물건은 무조건 품질동으로
            new Flow("PROD-B1", "QC-IN"),
            new Flow("PROD-B2", "QC-IN"),
            new Flow("PROD-B3", "QC-IN"),
            new Flow("PROD-B4", "QC-IN"),
            // 판정 후: 합격이면 창고동 입고
            new Flow("QC-OUT", "WH-RECV"),
            new Flow("QC-OUT", "WH-RECV"),
            // 판정 후: 불합격이면 생산동으로 되돌려 재작업
            new Flow("QC-OUT", "PROD-B1"),
            // 출하: 창고에 들어온 완제품을 출하장으로
            new Flow("WH-PICK", "WH-SHIP"),
            // 위층 보관: 1층에 다 못 두는 물량은 2·3층에 올린다.
            // 층이 다르므로 TaskService가 엘리베이터에서 두 구간으로 끊는다 —
            // 앞 구간은 1층 로봇이 승강장까지, 뒷 구간은 그 층 로봇이 이어받는다.
            new Flow("WH-RECV", "WH-2F-P1"),
            new Flow("WH-RECV", "WH-3F-P1"),
            // 위층 재고를 내려 출하
            new Flow("WH-2F-P2", "WH-SHIP"),
            new Flow("WH-3F-P2", "WH-SHIP"),
            // 층 안에서의 재배치 — 위층 로봇이 자기 층에서 하는 일
            new Flow("WH-2F-P1", "WH-2F-P2"),
            new Flow("WH-3F-P1", "WH-3F-P2"),
            // P20-3: 신관(Building-A/B) 편입 확인용 — 판정 후 물건이 신관 물류 적재장까지 간다.
            // 게이트 2개(GATE-WH-A, GATE-A-B)를 거치는 경로가 실제로 계산되는지가 이 흐름의 목적.
            new Flow("QC-OUT", "LOGI-1"));

    /**
     * 대기 작업이 이 수를 넘으면 새로 만들지 않는다.
     * 통로를 둘로 나눠 동시 주행이 늘었으므로, 큐가 마르지 않도록 함께 올렸다.
     *
     * <p>층이 생기면서 한 번 더 올렸다. 배차가 <b>층별로 갈리므로</b> 큐가 한 층 작업으로
     * 차면 다른 층 로봇은 큐가 비어 있는 것과 같아진다 — 큐 하나를 셋이 나눠 쓰는 셈이다.
     * 위층 증차(8대)에 맞춰 대수보다 여유 있게 둔다 — 큐가 로봇보다 얕으면 증차가 헛돈다.
     */
    private static final int MAX_PENDING = 12;

    private final OrderService orderService;
    private final FleetOrderRepository orderRepository;

    public DemoTaskGenerator(OrderService orderService, FleetOrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelayString = "${demo.task-generator.interval-ms:15000}")
    public void generate() {
        if (orderRepository.countByStatus(OrderStatus.TO_BE_ALLOCATED) >= MAX_PENDING) {
            return;
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Flow flow = FLOWS.get(rnd.nextInt(FLOWS.size()));

        String code = "T-" + System.currentTimeMillis() % 1_000_000;
        try {
            // 데모 흐름은 전부 "싣고 → 내리는" 2스텝 주문이다. 층이 다르면
            // OrderService가 승강장에서 체인으로 끊는다 — 여기는 신경 쓰지 않는다.
            orderService.create(code, null,
                    List.of(StepSpec.load(flow.origin()), StepSpec.unload(flow.destination())),
                    randomPriority(rnd), true);
            log.debug("Demo order {} created: {} -> {}", code, flow.origin(), flow.destination());
        } catch (Exception e) {
            log.debug("Skipped demo order creation: {}", e.getMessage());
        }
    }

    /** M4처럼 정수 — 1=NORMAL 2=HIGH 3=URGENT. */
    private int randomPriority(ThreadLocalRandom rnd) {
        int r = rnd.nextInt(100);
        if (r < 10) {
            return 3;
        }
        if (r < 30) {
            return 2;
        }
        return 1;
    }
}
