package com.pixelfleet.robot.repository;

import com.pixelfleet.robot.domain.Robot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<Robot, Long> {

    Optional<Robot> findByRobotCode(String robotCode);

    List<Robot> findAllByOrderByRobotCodeAsc();
}
