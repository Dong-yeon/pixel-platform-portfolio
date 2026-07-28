package com.pixelfleet.task.repository;

import com.pixelfleet.task.domain.TaskStatus;
import com.pixelfleet.task.domain.TransportTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportTaskRepository extends JpaRepository<TransportTask, Long> {

    Optional<TransportTask> findByTaskCode(String taskCode);

    List<TransportTask> findByStatusOrderByIdAsc(TaskStatus status);

    List<TransportTask> findAllByOrderByIdDesc();

    boolean existsByAssignedRobotIdAndStatusIn(Long assignedRobotId, List<TaskStatus> statuses);

    /** 배차됐는데 로봇이 시작 보고를 하지 않은 채 오래된 작업(고아 작업 감지용). */
    List<TransportTask> findByStatusAndAssignedAtBefore(TaskStatus status, java.time.LocalDateTime cutoff);

    /** 시작은 했는데 너무 오래 끝나지 않는 작업(로봇 유실 감지용). */
    List<TransportTask> findByStatusAndStartedAtBefore(TaskStatus status, java.time.LocalDateTime cutoff);
}
