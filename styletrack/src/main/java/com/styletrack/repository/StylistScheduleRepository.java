package com.styletrack.repository;

import com.styletrack.entity.StylistSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface StylistScheduleRepository extends JpaRepository<StylistSchedule, Long> {

    List<StylistSchedule> findByStylistIdOrderByDayOfWeekAscStartTimeAsc(Long stylistId);

    List<StylistSchedule> findByStylistIdAndDayOfWeek(Long stylistId, DayOfWeek dayOfWeek);
}
