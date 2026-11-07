package com.styletrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardDto {

    private DashboardDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private long todayAppointments;
        private long pendingBookings;
        private long pendingHomeServiceBookings;
        private long totalCustomers;
        private long activeStylists;
        private BigDecimal todayRevenue;
        private BigDecimal monthRevenue;
        private List<AppointmentDto.Response> upcomingAppointments;
    }
}
