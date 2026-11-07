package com.styletrack.dto;

import com.styletrack.entity.Appointment;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AppointmentDto {

    private AppointmentDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull
        private Long customerId;
        @NotNull
        private Long stylistId;
        @NotNull
        private Long serviceId;
        @NotNull
        private AppointmentType type;
        @NotNull
        private LocalDateTime startTime;
        /** Required when type is HOME_SERVICE. */
        private String address;
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RescheduleRequest {
        @NotNull
        private LocalDateTime startTime;
        /** Optional: move the booking to a different stylist. */
        private Long stylistId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        @NotBlank
        private String reason;
        /** Optional: a different stylist suggested to the customer as an alternative. */
        private Long suggestedStylistId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentRequest {
        @NotBlank
        private String method;
        private String reference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long customerId;
        private String customerName;
        private String customerPhone;
        private Long stylistId;
        private String stylistName;
        private Long serviceId;
        private String serviceName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private AppointmentType type;
        private AppointmentStatus status;
        private String address;
        private String notes;
        private String rejectionReason;
        private Long suggestedStylistId;
        private String suggestedStylistName;
        private BigDecimal homeServiceFee;
        private BigDecimal totalAmount;
        private PaymentStatus paymentStatus;
        private String paymentMethod;
        private String paymentReference;
        private LocalDateTime paidAt;
        private int pointsEarned;
        private LocalDateTime createdAt;

        public static Response from(Appointment a) {
            return Response.builder()
                    .id(a.getId())
                    .customerId(a.getCustomer().getId())
                    .customerName(a.getCustomer().getFullName())
                    .customerPhone(a.getCustomer().getPhone())
                    .stylistId(a.getStylist().getId())
                    .stylistName(a.getStylist().getFullName())
                    .serviceId(a.getService().getId())
                    .serviceName(a.getService().getName())
                    .startTime(a.getStartTime())
                    .endTime(a.getEndTime())
                    .type(a.getType())
                    .status(a.getStatus())
                    .address(a.getAddress())
                    .notes(a.getNotes())
                    .rejectionReason(a.getRejectionReason())
                    .suggestedStylistId(a.getSuggestedStylist() == null ? null : a.getSuggestedStylist().getId())
                    .suggestedStylistName(a.getSuggestedStylist() == null ? null : a.getSuggestedStylist().getFullName())
                    .homeServiceFee(a.getHomeServiceFee())
                    .totalAmount(a.getTotalAmount())
                    .paymentStatus(a.getPaymentStatus())
                    .paymentMethod(a.getPaymentMethod())
                    .paymentReference(a.getPaymentReference())
                    .paidAt(a.getPaidAt())
                    .pointsEarned(a.getPointsEarned())
                    .createdAt(a.getCreatedAt())
                    .build();
        }
    }
}
