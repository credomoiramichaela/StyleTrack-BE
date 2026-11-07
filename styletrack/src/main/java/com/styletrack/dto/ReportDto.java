package com.styletrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public final class ReportDto {

    private ReportDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private LocalDate from;
        private LocalDate to;
        private long totalAppointments;
        private Map<String, Long> appointmentsByStatus;
        private long homeServiceBookings;
        private long completedAppointments;
        private BigDecimal revenue;
        private BigDecimal averageTicket;
        private long newCustomers;
        private long loyaltyPointsIssued;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenue {
        private LocalDate date;
        private BigDecimal revenue;
        private long completedAppointments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceStat {
        private Long serviceId;
        private String serviceName;
        private long completedCount;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StylistStat {
        private Long stylistId;
        private String stylistName;
        private long completedCount;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MethodStat {
        private String method;
        private long count;
        private BigDecimal amount;
    }

    /** Digital-payment monitoring: what was collected, by method, and what is still owed. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSummary {
        private LocalDate from;
        private LocalDate to;
        private BigDecimal totalCollected;
        private long paidCount;
        private java.util.List<MethodStat> byMethod;
        private long outstandingCount;
        private BigDecimal outstandingAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopCustomer {
        private Long customerId;
        private String fullName;
        private String phone;
        private int completedVisits;
        private int loyaltyPoints;
        private String tier;
    }
}
