package com.pixelfleet.metrics;

import com.pixelfleet.order.domain.OrderStatus;
import com.pixelfleet.order.repository.FleetOrderRepository;
import com.pixelfleet.traffic.TrafficController;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * {@code docs/BACKLOG.md}에 수기로 적혀 있던 관측을 상시 메트릭으로 옮긴다 — "동시 주행이
 * 여전히 1~2대다"·"함대 전체가 멈췄다" 같은 문장은 그때그때 로그를 뒤져 확인한 것이었다.
 *
 * <p>둘 다 <b>Gauge</b>다(Counter가 아니다) — 지금 이 순간의 수준을 보는 지표이지 누적량이
 * 아니다. Micrometer Gauge는 스크레이핑 시점에 supplier를 호출하는 pull 방식이라, 값이
 * 바뀔 때마다 여기서 갱신할 필요가 없다 — 리포지토리·TrafficController가 원본 데이터를
 * 그대로 갖고 있고 이 클래스는 조회 방법만 등록한다.
 */
@Component
public class FleetMetrics {

    public FleetMetrics(MeterRegistry registry, FleetOrderRepository orderRepository,
                         TrafficController trafficController) {

        // 미배차 대기 주문 수 — 늘어나면 로봇 공급이 부족하거나 레인이 막혀 있다는 신호다.
        Gauge.builder("fleet.orders.pending", orderRepository,
                        repo -> repo.countByStatus(OrderStatus.TO_BE_ALLOCATED))
                .description("배차를 기다리는 주문 수 (OrderStatus.TO_BE_ALLOCATED)")
                .register(registry);

        // 현재 점유된 레인 구간 수 — "동시 주행이 1~2대" 같은 관측을 숫자로 상시 볼 수 있게 한다.
        // 총 구간 수 대비 비율은 아직 안 낸다: LaneGraph가 총 구간 수를 공개 API로 노출하지
        // 않는다(그래프 내부 상태). 필요해지면 LaneGraph 쪽에 먼저 접근자를 추가할 것.
        Gauge.builder("fleet.traffic.segments.occupied", trafficController,
                        tc -> tc.snapshot().size())
                .description("지금 이 순간 로봇이 점유하고 있는 레인 구간 수")
                .register(registry);
    }
}
