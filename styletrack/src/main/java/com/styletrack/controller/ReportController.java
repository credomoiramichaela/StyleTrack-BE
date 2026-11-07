package com.styletrack.controller;

import com.styletrack.dto.ReportDto;
import com.styletrack.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Super Admin and Admin can view reports; only Super Admin can print them (enforced on the frontend). All ranges are inclusive and default to the current month. */
@Tag(name = "Reports & statistics")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ReportDto.Summary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.summary(from, to);
    }

    @GetMapping("/revenue-daily")
    public List<ReportDto.DailyRevenue> dailyRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.dailyRevenue(from, to);
    }

    @GetMapping("/top-services")
    public List<ReportDto.ServiceStat> topServices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit) {
        return reportService.topServices(from, to, limit);
    }

    @GetMapping("/stylist-performance")
    public List<ReportDto.StylistStat> stylistPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.stylistPerformance(from, to);
    }

    @GetMapping("/payments")
    public ReportDto.PaymentSummary payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.payments(from, to);
    }

    @GetMapping("/top-customers")
    public List<ReportDto.TopCustomer> topCustomers(@RequestParam(defaultValue = "10") int limit) {
        return reportService.topCustomers(limit);
    }
}
