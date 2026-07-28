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
}
