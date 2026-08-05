package com.pixelfleet.order.repository;

import com.pixelfleet.order.domain.FleetOrder;
import com.pixelfleet.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FleetOrderRepository extends JpaRepository<FleetOrder, Long> {

    /**
     * 스텝을 함께 읽는다. 주문을 다루는 코드는 거의 항상 스텝을 보므로 N+1을 미리 없애고,
     * <b>조회 응답을 만드는 쪽이 트랜잭션 밖이어도 안전</b>하게 한다(지연 로딩 폭발 방지).
     */
    @EntityGraph(attributePaths = "steps")
    Optional<FleetOrder> findByOrderCode(String orderCode);

    @EntityGraph(attributePaths = "steps")
    Optional<FleetOrder> findWithStepsById(Long id);

    /** 배차 스캔용 — 우선순위 내림차순, 같은 우선순위는 FIFO. */
    @EntityGraph(attributePaths = "steps")
    List<FleetOrder> findByStatusOrderByPriorityDescIdAsc(OrderStatus status);

    /**
     * 최근 주문 목록(화면용). <b>전량을 주지 않는다</b> — 데모 생성기가 계속 돌아 주문이
     * 수천 건씩 쌓이는데, 화면은 최근 것만 본다. 예전 작업 목록은 전량을 실어 보냈다.
     */
    @EntityGraph(attributePaths = "steps")
    List<FleetOrder> findTop200ByOrderByIdDesc();

    long countByStatus(OrderStatus status);

    /**
     * 이 로봇이 이미 주문을 쥐고 있는가. <b>PENDING을 포함해야 한다</b> —
     * 미봉인 주문은 스텝을 소진해도 로봇을 쥔 채 기다린다(짐을 실은 채일 수 있다).
     */
    boolean existsByAssignedRobotIdAndStatusIn(Long assignedRobotId, List<OrderStatus> statuses);

    /** 배차됐는데 시작 보고가 없는 채 오래된 주문(로봇이 지시를 놓쳤다). fault는 제외. */
    List<FleetOrder> findByStatusAndFaultFalseAndAssignedAtBefore(OrderStatus status, LocalDateTime cutoff);

    /** 진행 보고가 끊긴 지 오래된 주문(로봇 유실). 스텝마다 갱신되는 lastProgressAt 기준. */
    List<FleetOrder> findByStatusAndFaultFalseAndLastProgressAtBefore(OrderStatus status, LocalDateTime cutoff);
}
