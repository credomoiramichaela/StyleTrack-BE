package com.styletrack.dto;

import com.styletrack.entity.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CustomerDto {

    private CustomerDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String fullName;
        @NotBlank
        private String phone;
        @Email
        private String email;
        private String address;
        private LocalDate birthDate;
        private String notes;
    }

    /** Staff sets up the mobile-app login for a customer who is already on file. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppAccountRequest {
        @NotBlank
        @Size(min = 8, max = 100)
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppAccountResponse {
        /** What the customer types to sign in: their mobile number. */
        private String username;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String fullName;
        private String phone;
        private String email;
        private String address;
        private LocalDate birthDate;
        private String notes;
        private int loyaltyPoints;
        /** Services completed so far. */
        private int completedVisits;
        /** REGULAR, SILVER or GOLD - set by the service layer from the configured visit thresholds. */
        private String tier;
        private boolean active;
        /** True when the customer has a mobile-app login. */
        private boolean hasAppAccount;
        private LocalDateTime createdAt;

        public static Response from(Customer c) {
            return Response.builder()
                    .id(c.getId())
                    .fullName(c.getFullName())
                    .phone(c.getPhone())
                    .email(c.getEmail())
                    .address(c.getAddress())
                    .birthDate(c.getBirthDate())
                    .notes(c.getNotes())
                    .loyaltyPoints(c.getLoyaltyPoints())
                    .completedVisits(c.getCompletedVisits())
                    .active(c.isActive())
                    .hasAppAccount(c.getUser() != null)
                    .createdAt(c.getCreatedAt())
                    .build();
        }
    }
}
