package com.pixelfleet.task.domain;

import com.pixelfleet.common.entity.BaseEntity;
import com.pixelfleet.common.exception.BusinessException;
import com.pixelfleet.common.exception.ErrorCode;
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

    private LocalDateTime assignedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @Column(length = 500)
    private String failureReason;

    public TransportTask(String taskCode, String originNode, String destinationNode, TaskPriority priority) {
        this.taskCode = taskCode;
        this.originNode = originNode;
        this.destinationNode = destinationNode;
        this.priority = priority;
        this.status = TaskStatus.PENDING;
        this.retryCount = 0;
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
