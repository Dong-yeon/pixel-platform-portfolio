package com.pixelfleet.task.domain;

import java.util.Set;

/**
 * Transport task lifecycle. Transitions are enforced in {@link TransportTask};
 * anything not listed in {@link #canTransitionTo} is rejected.
 *
 * <pre>
 *   PENDING ─▶ ASSIGNED ─▶ IN_PROGRESS ─▶ COMPLETED
 *      ▲          │             │
 *      │          ▼             ▼
 *      └───────  FAILED  ◀──────┘   (retry re-opens to PENDING)
 *   PENDING/ASSIGNED ─▶ CANCELLED
 * </pre>
 */
public enum TaskStatus {
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(TaskStatus next) {
        return switch (this) {
            case PENDING -> Set.of(ASSIGNED, CANCELLED).contains(next);
            case ASSIGNED -> Set.of(IN_PROGRESS, FAILED, CANCELLED).contains(next);
            case IN_PROGRESS -> Set.of(COMPLETED, FAILED).contains(next);
            case FAILED -> Set.of(PENDING).contains(next); // retry
            case COMPLETED, CANCELLED -> false;            // terminal
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
