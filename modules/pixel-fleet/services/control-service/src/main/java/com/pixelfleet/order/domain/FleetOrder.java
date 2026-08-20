package com.pixelfleet.order.domain;

import com.pixelfleet.robot.domain.RobotType;
import com.pixelplatform.core.common.entity.BaseEntity;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운송 주문 — 스텝 리스트를 순서대로 실행한다 (M4의 주문 모델).
 *
 * <p>예전 TransportTask(출발→도착 단일 이송)의 일반화다. 픽업/하역은 forLoad/forUnload
 * 스텝일 뿐이고, 봉인하지 않은 주문(stepFixed=false)은 스텝을 소진해도 닫히지 않고
 * PENDING으로 다음 스텝을 기다린다.
 *
 * <p><b>실패는 상태가 아니라 {@code fault} 플래그다.</b> 자동 재시도 예산이 남으면
 * {@link #resetForRetry}로 처음부터 다시 달리고, 소진되면 {@link #faultOut}으로 얼린다.
 */
@Getter
@Entity
@Table(name = "fleet_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FleetOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderCode;

    /**
     * 상류(WMS 등)의 전표 번호. 완료/실패 통지의 열쇠다 — 층간 체인으로 주문이 쪼개져도
     * 체인 전체가 이 값을 물려받아, 상류는 자기가 낸 번호 하나로 결과를 받는다.
     */
    @Column(length = 50)
    private String externalId;

    /** M4처럼 정수 — 클수록 높다(0=LOW 1=NORMAL 2=HIGH 3=URGENT 관례). */
    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    private Long assignedRobotId;

    @Column(nullable = false)
    private short floorNo;

    /**
     * 이 주문을 실행해야 하는 로봇 종류(P21) — 기본 AMR. 렉 기원 주문(창고동 렉 취출)의
     * 앞 레그만 {@code AGV}(옛 이름: 랙 피더)다. 배차 후보 필터가 이 값과 로봇의 종류를 맞춘다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RobotType robotType = RobotType.AMR;

    /**
     * AGV 주문 전용 — 담당 존(피킹존 노드 코드). {@code robotType == AGV}일 때만
     * 값이 있다. 배차 후보를 그 존의 로봇으로 좁힌다.
     */
    @Column(length = 30)
    private String zoneCode;

    /** -1 = 시작 전. 그 외엔 지금 향하고 있거나 막 끝낸 스텝. */
    @Column(nullable = false)
    private int currentStepIndex;

    /** 적재 여부 — forLoad/forUnload 스텝 완료의 누적 결과. */
    @Column(nullable = false)
    private boolean loaded;

    /** 봉인. false면 스텝 소진 후 PENDING에서 add-steps를 기다린다. */
    @Column(nullable = false)
    private boolean stepFixed;

    @Column(nullable = false)
    private boolean suspended;

    /** 자동 재시도 소진 — 상태를 얼린 채 사람의 retry-failed를 기다린다. */
    @Column(nullable = false)
    private boolean fault;

    @Column(nullable = false)
    private int failureNum;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 30)
    private String handoffDestination;

    @Column(length = 50)
    private String handoffOf;

    private LocalDateTime availableAt;
    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    /** 워치독 기준 — 시작·스텝 완료 때마다 갱신된다(스텝 많은 주문의 억울한 타임아웃 방지). */
    private LocalDateTime lastProgressAt;
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepIndex asc")
    private List<OrderStep> steps = new ArrayList<>();

    public FleetOrder(String orderCode, String externalId, int priority, boolean stepFixed, short floorNo) {
        this.orderCode = orderCode;
        this.externalId = externalId;
        this.priority = priority;
        this.stepFixed = stepFixed;
        this.floorNo = floorNo;
        this.robotType = RobotType.AMR;
        this.status = OrderStatus.TO_BE_ALLOCATED;
        this.currentStepIndex = -1;
    }

    /** 로봇 풀(P21)을 명시하는 생성자 — AGV 주문처럼 AMR이 아닌 풀을 요구할 때 쓴다. */
    public FleetOrder(String orderCode, String externalId, int priority, boolean stepFixed, short floorNo,
                      RobotType robotType, String zoneCode) {
        this(orderCode, externalId, priority, stepFixed, floorNo);
        this.robotType = robotType;
        this.zoneCode = zoneCode;
    }

    public OrderStep addStep(String locationNode, boolean forLoad, boolean forUnload) {
        if (forLoad && forUnload) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "한 스텝에서 forLoad와 forUnload가 동시에 참일 수 없습니다.");
        }
        OrderStep step = new OrderStep(this, steps.size(), locationNode, forLoad, forUnload);
        steps.add(step);
        return step;
    }

    public OrderStep stepAt(int index) {
        if (index < 0 || index >= steps.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "주문 " + orderCode + "에 " + index + "번 스텝이 없습니다.");
        }
        return steps.get(index);
    }

    public boolean isLastStep(int index) {
        return index == steps.size() - 1;
    }

    /** 층간 체인 앞 구간 — 이 주문이 끝나면 화물이 엘리베이터를 타고 여기로 간다. */
    public void handOffTo(String finalDestination) {
        this.handoffDestination = finalDestination;
    }

    /** 층간 체인 뒷 구간 — 엘리베이터가 도착할 때까지 배차하지 않는다. */
    public void continues(String previousOrderCode, LocalDateTime elevatorArrivesAt) {
        this.handoffOf = previousOrderCode;
        this.availableAt = elevatorArrivesAt;
    }

    public boolean isDispatchable(LocalDateTime now) {
        return availableAt == null || !availableAt.isAfter(now);
    }

    /** 배차 — 접근 레그 예약까지 성공한 뒤에 부른다. step0이 실행에 들어간다. */
    public void assignTo(Long robotId) {
        transitionTo(OrderStatus.ALLOCATED);
        this.assignedRobotId = robotId;
        this.assignedAt = LocalDateTime.now();
        this.currentStepIndex = 0;
        stepAt(0).markExecuting();
    }

    /** 로봇의 시작 보고. MQTT 중복(QoS1)에 대비해 멱등이다. */
    public void start() {
        if (status == OrderStatus.EXECUTING) {
            return;
        }
        transitionTo(OrderStatus.EXECUTING);
        this.startedAt = LocalDateTime.now();
        this.lastProgressAt = this.startedAt;
    }

    /** 현재 스텝 완료 — 적재 상태를 갱신하고 진행 시각을 남긴다. */
    public void stepDone() {
        OrderStep step = stepAt(currentStepIndex);
        step.markDone();
        if (step.isForLoad()) {
            this.loaded = true;
        }
        if (step.isForUnload()) {
            this.loaded = false;
        }
        this.lastProgressAt = LocalDateTime.now();
    }

    /** 다음 스텝으로 넘어간다(레그 예약은 서비스가 한다). */
    public void advanceToStep(int index) {
        this.currentStepIndex = index;
    }

    /** 현재 스텝의 레그가 예약돼 실제 주행에 들어간다. */
    public void beginCurrentStep() {
        stepAt(currentStepIndex).markExecuting();
    }

    public void complete() {
        transitionTo(OrderStatus.DONE);
        this.finishedAt = LocalDateTime.now();
    }

    /** 미봉인 주문이 스텝을 소진했다 — 로봇을 쥔 채 다음 스텝을 기다린다. */
    public void parkUnsealed() {
        transitionTo(OrderStatus.PENDING);
    }

    /** add-steps로 스텝이 이어졌다 — 다시 실행으로. */
    public void resumeFromPending() {
        transitionTo(OrderStatus.EXECUTING);
    }

    /** 봉인 — 이후 스텝 편집이 막힌다. PENDING이던 주문은 서비스가 이어서 닫는다. */
    public void seal() {
        this.stepFixed = true;
    }

    /**
     * 자동 재시도 — 전체 리셋 후 재큐. 오늘의 재시도 의미(처음부터 다시)를 그대로 승계한다:
     * leg2에서 실패해도 다시 픽업부터 달렸다. 스텝 중간의 물리적 화물 상태를 서버가
     * 복구할 수 없으므로 전체 재주행이 정직하다.
     */
    public void resetForRetry(String reason) {
        transitionTo(OrderStatus.TO_BE_ALLOCATED);
        this.failureNum++;
        this.failureReason = reason;
        this.assignedRobotId = null;
        this.assignedAt = null;
        this.startedAt = null;
        this.lastProgressAt = null;
        this.currentStepIndex = -1;
        this.loaded = false;
        steps.stream()
                .filter(s -> s.getStatus() != StepStatus.CANCELLED)
                .forEach(OrderStep::resetToExecutable);
    }

    /** 재시도 예산 소진 — 상태를 얼린 채 fault를 세운다. 로봇과 구간은 서비스가 놓는다. */
    public void faultOut(String reason) {
        this.fault = true;
        this.failureNum++;
        this.failureReason = reason;
        this.assignedRobotId = null;
    }

    /**
     * 조작자 수동 retry-failed — fault로 얼어붙은 주문만 되살린다.
     *
     * <p>{@link #resetForRetry}를 그대로 재사용하지 않는 이유: 지금까지 유일한 호출자
     * ({@code markFailed}의 자동 재시도 분기)는 {@code fault==false}일 때만 부르므로
     * {@code resetForRetry}는 {@code fault}를 지우지 않는다. 수동 retry는 정확히
     * {@code fault==true}일 때 불리므로, 그대로 쓰면 영원히 배차 안 되는 주문이 남는다.
     */
    public void retryAfterFault(String reason) {
        if (!fault) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "fault 상태가 아닌 주문은 retry-failed로 되살릴 수 없습니다.");
        }
        resetForRetry(reason);
        this.fault = false;
    }

    /**
     * 조작자가 다음 레그를 막는다. {@code fault}와 같은 설계 — 상태 전이가 아니라 얹힌
     * 플래그다. <b>이미 시작된(물리적으로 끝난) 레그는 막지 않는다</b> — 그 결과는 서버가
     * 되돌릴 수 없다. 서비스가 markStepDone의 즉시 배차 경로를 이 플래그로 게이트한다.
     */
    public void suspend() {
        this.suspended = true;
    }

    public void unsuspend() {
        this.suspended = false;
    }

    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
        this.finishedAt = LocalDateTime.now();
        steps.stream()
                .filter(s -> s.getStatus() == StepStatus.EXECUTABLE || s.getStatus() == StepStatus.EXECUTING)
                .forEach(OrderStep::markCancelled);
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "허용되지 않은 주문 상태 전이입니다: " + status + " -> " + next);
        }
        this.status = next;
    }
}
