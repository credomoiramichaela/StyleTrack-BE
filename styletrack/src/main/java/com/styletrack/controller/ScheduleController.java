package com.styletrack.controller;

import com.styletrack.dto.ScheduleDto;
import com.styletrack.service.ScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Write access: admins manage any stylist's schedule, a stylist manages only their own (checked in the service). */
@Tag(name = "Stylist schedules")
@RestController
@RequestMapping("/api/stylists/{stylistId}/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public List<ScheduleDto.Response> list(@PathVariable Long stylistId) {
        return scheduleService.list(stylistId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','STYLIST')")
    public ScheduleDto.Response create(@PathVariable Long stylistId, @Valid @RequestBody ScheduleDto.Request request) {
        return scheduleService.create(stylistId, request);
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','STYLIST')")
    public ScheduleDto.Response update(@PathVariable Long stylistId, @PathVariable Long scheduleId,
                                       @Valid @RequestBody ScheduleDto.Request request) {
        return scheduleService.update(stylistId, scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','STYLIST')")
    public void delete(@PathVariable Long stylistId, @PathVariable Long scheduleId) {
        scheduleService.delete(stylistId, scheduleId);
    }
}
