package com.styletrack.controller;

import com.styletrack.dto.DashboardDto;
import com.styletrack.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardDto.Response dashboard() {
        return dashboardService.get();
    }
}
