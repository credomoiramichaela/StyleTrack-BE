package com.styletrack.service;

import com.styletrack.dto.ScheduleDto;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.StylistSchedule;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.StylistScheduleRepository;
import com.styletrack.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final StylistScheduleRepository scheduleRepository;
    private final StylistRepository stylistRepository;
    private final CurrentUser currentUser;

    public List<ScheduleDto.Response> list(Long stylistId) {
        findStylist(stylistId);
        return scheduleRepository.findByStylistIdOrderByDayOfWeekAscStartTimeAsc(stylistId).stream()
                .map(ScheduleDto.Response::from).toList();
    }

    @Transactional
    public ScheduleDto.Response create(Long stylistId, ScheduleDto.Request r) {
        Stylist stylist = findStylist(stylistId);
        assertCanEdit(stylist);
        validate(stylistId, r, null);
        StylistSchedule schedule = StylistSchedule.builder().stylist(stylist).build();
        apply(schedule, r);
        return ScheduleDto.Response.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleDto.Response update(Long stylistId, Long scheduleId, ScheduleDto.Request r) {
        StylistSchedule schedule = findSchedule(stylistId, scheduleId);
        assertCanEdit(schedule.getStylist());
        validate(stylistId, r, scheduleId);
        apply(schedule, r);
        return ScheduleDto.Response.from(schedule);
    }

    @Transactional
    public void delete(Long stylistId, Long scheduleId) {
        StylistSchedule schedule = findSchedule(stylistId, scheduleId);
        assertCanEdit(schedule.getStylist());
        scheduleRepository.delete(schedule);
    }

    private void validate(Long stylistId, ScheduleDto.Request r, Long excludeId) {
        if (!r.getEndTime().isAfter(r.getStartTime())) {
            throw new BusinessException("End time must be after start time");
        }
        boolean overlaps = scheduleRepository.findByStylistIdAndDayOfWeek(stylistId, r.getDayOfWeek()).stream()
                .filter(s -> !s.getId().equals(excludeId))
                .anyMatch(s -> r.getStartTime().isBefore(s.getEndTime()) && r.getEndTime().isAfter(s.getStartTime()));
        if (overlaps) {
            throw new BusinessException("This block overlaps an existing schedule block on the same day",
                    HttpStatus.CONFLICT);
        }
    }

    /** Admins manage any schedule; a stylist can manage only their own. */
    private void assertCanEdit(Stylist stylist) {
        User me = currentUser.get();
        if (me.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        boolean own = me.getRole() == Role.STYLIST && stylist.getUser() != null
                && stylist.getUser().getId().equals(me.getId());
        if (!own) {
            throw new BusinessException("You are not allowed to change this schedule", HttpStatus.FORBIDDEN);
        }
    }

    private void apply(StylistSchedule schedule, ScheduleDto.Request r) {
        schedule.setDayOfWeek(r.getDayOfWeek());
        schedule.setStartTime(r.getStartTime());
        schedule.setEndTime(r.getEndTime());
        schedule.setHomeServiceAvailable(r.isHomeServiceAvailable());
    }

    private Stylist findStylist(Long id) {
        return stylistRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Stylist", id));
    }

    private StylistSchedule findSchedule(Long stylistId, Long scheduleId) {
        StylistSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
        if (!schedule.getStylist().getId().equals(stylistId)) {
            throw new ResourceNotFoundException("Schedule", scheduleId);
        }
        return schedule;
    }
}
