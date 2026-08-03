package com.pixelfleet.task.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A request to move material from an origin node to a destination node. The status
 * field is a guarded state machine (see {@link TaskStatus}); each transition method
 * validates the move before applying it so illegal states can't be persisted.
 */
@Getter
@Entity
@Table(name = "transport_tasks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String taskCode;

    @Column(nullable = false, length = 30)
    private String originNode;

    @Column(nullable = false, length = 30)
    private String destinationNode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    private Long assignedRobotId;

    @Column(nullable = false)
    private int retryCount;

    /**
     * 이 작업이 벌어지는 층 — 출발지 노드가 속한 층이다. 같은 층 로봇에게만 배차된다.
     * (로봇은 층을 오가지 못한다. 층을 넘는 이송은 엘리베이터에서 두 작업으로 끊긴다.)
     */
    @Column(nullable = false)
    private short floorNo;

    /**
     * 엘리베이터 인수인계 — 이 작업이 끝나면 물건이 승강해서 여기로 간다.
     * null이면 층을 넘지 않는 보통 작업이다.
     */
    @Column(length = 30)
    private String handoffDestination;

    /**
     * 엘리베이터가 도착하는 시각. 그 전에는 배차하지 않는다 —
     * 물건이 아직 안 왔는데 로봇을 승강장으로 보내면 빈손으로 서 있게 된다.
     */
    private LocalDateTime availableAt;

    /** 어느 작업을 이어받았는가(앞 구간의 작업코드). 화면에서 한 흐름으로 읽으려고 남긴다. */
    @Column(length = 50)
    private String handoffOf;

    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Column(length = 500)
    private String failureReason;

    public TransportTask(String taskCode, String originNode, String destinationNode,
                         TaskPriority priority, short floorNo) {
        this.taskCode = taskCode;
        this.originNode = originNode;
        this.destinationNode = destinationNode;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
        this.retryCount = 0;
        this.floorNo = floorNo;
    }

    /** 앞 구간에 "여기까지 오면 물건은 엘리베이터를 타고 최종 목적지로 간다"를 달아 둔다. */
    public void handOffTo(String finalDestination) {
        this.handoffDestination = finalDestination;
    }

    /** 뒷 구간 — 엘리베이터가 도착할 때까지 배차 대기시킨다. */
    public void continues(String previousTaskCode, LocalDateTime elevatorArrivesAt) {
        this.handoffOf = previousTaskCode;
        this.availableAt = elevatorArrivesAt;
    }

    /** 지금 배차해도 되는가 — 엘리베이터를 기다리는 중이면 아직 아니다. */
    public boolean isDispatchable(LocalDateTime now) {
        return availableAt == null || !availableAt.isAfter(now);
    }

    public void assignTo(Long robotId) {
        transitionTo(TaskStatus.ASSIGNED);
        this.assignedRobotId = robotId;
        this.assignedAt = LocalDateTime.now();
    }

    public void start() {
        transitionTo(TaskStatus.IN_PROGRESS);
        this.startedAt = LocalDateTime.now();
    }

    public void complete() {
        transitionTo(TaskStatus.COMPLETED);
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String reason) {
        transitionTo(TaskStatus.FAILED);
        this.failureReason = reason;
        this.finishedAt = LocalDateTime.now();
    }

    /** Re-open a failed task for another attempt; clears the previous assignment. */
    public void retry() {
        transitionTo(TaskStatus.PENDING);
        this.retryCount++;
        this.assignedRobotId = null;
        this.assignedAt = null;
        this.startedAt = null;
        this.finishedAt = null;
        this.failureReason = null;
    }

    public void cancel() {
        transitionTo(TaskStatus.CANCELLED);
        this.finishedAt = LocalDateTime.now();
    }

    private void transitionTo(TaskStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "허용되지 않은 상태 전이입니다: " + status + " -> " + next);
        }
        this.status = next;
    }
}
