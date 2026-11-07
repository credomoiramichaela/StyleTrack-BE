package com.styletrack.dto;

import com.styletrack.entity.TreatmentRecord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TreatmentDto {

    private TreatmentDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull
        private Long customerId;
        /** Optional: link the record to the booking it was written for. */
        private Long appointmentId;
        @NotNull
        private Long serviceId;
        /** Ignored for stylist accounts (their own profile is used); optional for admins when an appointment is given. */
        private Long stylistId;
        @NotNull
        private LocalDate treatmentDate;
        @NotBlank
        @Size(max = 2000)
        private String details;
        @Size(max = 500)
        private String productsUsed;
        @Size(max = 500)
        private String reactions;
        @Size(max = 1000)
        private String followUpAdvice;
        private Long followUpServiceId;
        @Min(1)
        @Max(730)
        private Integer followUpAfterDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long customerId;
        private String customerName;
        private Long appointmentId;
        private Long stylistId;
        private String stylistName;
        private Long serviceId;
        private String serviceName;
        private LocalDate treatmentDate;
        private String details;
        private String productsUsed;
        private String reactions;
        private String followUpAdvice;
        private Long followUpServiceId;
        private String followUpServiceName;
        private Integer followUpAfterDays;
        /** treatmentDate + followUpAfterDays, when both a follow-up service and a delay are set. */
        private LocalDate followUpDueDate;
        private LocalDateTime createdAt;

        public static Response from(TreatmentRecord t) {
            LocalDate due = t.getFollowUpAfterDays() == null ? null
                    : t.getTreatmentDate().plusDays(t.getFollowUpAfterDays());
            return Response.builder()
                    .id(t.getId())
                    .customerId(t.getCustomer().getId())
                    .customerName(t.getCustomer().getFullName())
                    .appointmentId(t.getAppointment() == null ? null : t.getAppointment().getId())
                    .stylistId(t.getStylist().getId())
                    .stylistName(t.getStylist().getFullName())
                    .serviceId(t.getService().getId())
                    .serviceName(t.getService().getName())
                    .treatmentDate(t.getTreatmentDate())
                    .details(t.getDetails())
                    .productsUsed(t.getProductsUsed())
                    .reactions(t.getReactions())
                    .followUpAdvice(t.getFollowUpAdvice())
                    .followUpServiceId(t.getFollowUpService() == null ? null : t.getFollowUpService().getId())
                    .followUpServiceName(t.getFollowUpService() == null ? null : t.getFollowUpService().getName())
                    .followUpAfterDays(t.getFollowUpAfterDays())
                    .followUpDueDate(due)
                    .createdAt(t.getCreatedAt())
                    .build();
        }
    }
}
