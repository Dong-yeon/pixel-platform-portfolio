package com.pixelfactory.oee.repository;

import com.pixelfactory.oee.domain.ShiftCalendar;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftCalendarRepository extends JpaRepository<ShiftCalendar, Long> {

    List<ShiftCalendar> findByLineId(Long lineId);
}
