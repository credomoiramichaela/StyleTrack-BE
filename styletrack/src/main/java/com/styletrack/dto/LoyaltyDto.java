package com.styletrack.dto;

import com.styletrack.entity.LoyaltyTransaction;
import com.styletrack.entity.enums.LoyaltyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class LoyaltyDto {

    private LoyaltyDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedeemRequest {
        @NotNull
        @Min(1)
        private Integer points;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdjustRequest {
        /** Positive to add points, negative to remove. */
        @NotNull
        private Integer points;
        @NotBlank
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Balance {
        private Long customerId;
        private String customerName;
        private int balance;
        private int completedVisits;
        /** REGULAR, SILVER or GOLD. */
        private String tier;
        /** Completed visits still needed for the next tier; 0 when already at the top tier. */
        private int visitsToNextTier;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedeemRewardRequest {
        @NotNull
        private Long rewardId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private Long id;
        private Long customerId;
        private Long appointmentId;
        private int points;
        private int balanceAfter;
        private LoyaltyType type;
        private String description;
        private LocalDateTime createdAt;

        public static TransactionResponse from(LoyaltyTransaction t) {
            return TransactionResponse.builder()
                    .id(t.getId())
                    .customerId(t.getCustomer().getId())
                    .appointmentId(t.getAppointment() == null ? null : t.getAppointment().getId())
                    .points(t.getPoints())
                    .balanceAfter(t.getBalanceAfter())
                    .type(t.getType())
                    .description(t.getDescription())
                    .createdAt(t.getCreatedAt())
                    .build();
        }
    }
}
