package com.styletrack.dto;

import com.styletrack.entity.Stylist;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class StylistDto {

    private StylistDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String fullName;
        private String phone;
        @Email
        private String email;
        private String specialization;
        private String bio;
        private Boolean active;
        /** Optional: create a login account for this stylist (create only). */
        @Size(min = 3, max = 50)
        private String username;
        @Size(min = 8, max = 100)
        private String password;
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
        private String specialization;
        private String bio;
        private boolean active;
        private Long userId;
        private String username;

        public static Response from(Stylist s) {
            return Response.builder()
                    .id(s.getId())
                    .fullName(s.getFullName())
                    .phone(s.getPhone())
                    .email(s.getEmail())
                    .specialization(s.getSpecialization())
                    .bio(s.getBio())
                    .active(s.isActive())
                    .userId(s.getUser() == null ? null : s.getUser().getId())
                    .username(s.getUser() == null ? null : s.getUser().getUsername())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Availability {
        private Long stylistId;
        private LocalDate date;
        private Long serviceId;
        private List<LocalTime> availableStartTimes;
    }
}
