package com.styletrack.controller;

import com.styletrack.dto.ActivityLogDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.ActivityLog;
import com.styletrack.service.ActivityLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Super Admin-only audit trail: logins, user/role changes, and appointment accept/reject/cancel actions. */
@Tag(name = "Log reports")
@RestController
@RequestMapping("/api/log-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class LogReportController {

    private final ActivityLogService activityLogService;

    @GetMapping
    public PageResponse<ActivityLogDto.Response> search(
            @RequestParam(required = false) ActivityLog.Action action,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return activityLogService.search(action, actorUserId,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(),
                pageable);
    }
}
