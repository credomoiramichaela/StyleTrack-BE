package com.styletrack.service;

import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.DashboardDto;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.AppointmentSpecs;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.util.NumberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Salon-wide numbers for Super Admin and Admin — the Admin Dashboard is not available to stylists. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final StylistRepository stylistRepository;

    public DashboardDto.Response get() {
        Long scope = null;
        long scopeId = 0L;

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        long todayCount = appointmentRepository.count(
                AppointmentSpecs.filter(scope, null, null, null, dayStart, dayEnd));
        long pending = appointmentRepository.count(
                AppointmentSpecs.filter(scope, null, AppointmentStatus.PENDING, null, null, null));
        long pendingHome = appointmentRepository.count(
                AppointmentSpecs.filter(scope, null, AppointmentStatus.PENDING, AppointmentType.HOME_SERVICE, null, null));

        var upcomingSpec = AppointmentSpecs.filter(scope, null, AppointmentStatus.ACCEPTED, null,
                LocalDateTime.now(), null);
        List<AppointmentDto.Response> upcoming = appointmentRepository
                .findAll(upcomingSpec, PageRequest.of(0, 5, Sort.by("startTime").ascending()))
                .map(AppointmentDto.Response::from).getContent();

        return DashboardDto.Response.builder()
                .todayAppointments(todayCount)
                .pendingBookings(pending)
                .pendingHomeServiceBookings(pendingHome)
                .totalCustomers(customerRepository.countByActiveTrue())
                .activeStylists(stylistRepository.countByActiveTrue())
                .todayRevenue(NumberUtil.toBigDecimal(
                        appointmentRepository.sumCompletedRevenue(dayStart, dayEnd, scopeId)))
                .monthRevenue(NumberUtil.toBigDecimal(
                        appointmentRepository.sumCompletedRevenue(monthStart, monthEnd, scopeId)))
                .upcomingAppointments(upcoming)
                .build();
    }
}
