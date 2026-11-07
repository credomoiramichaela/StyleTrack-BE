package com.styletrack.dto;

import com.styletrack.entity.Customer;
import com.styletrack.entity.Notification;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Request/response shapes for the customer mobile app ({@code /api/me/**}). */
public final class MeDto {

    private MeDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Profile {
        private Long id;
        private String fullName;
        private String phone;
        private String email;
        private String address;
        private LocalDate birthDate;
        private int loyaltyPoints;
        private int completedVisits;
        private String tier;
        private int visitsToNextTier;

        public static Profile from(Customer c, String tier, int visitsToNextTier) {
            return Profile.builder()
                    .id(c.getId())
                    .fullName(c.getFullName())
                    .phone(c.getPhone())
                    .email(c.getEmail())
                    .address(c.getAddress())
                    .birthDate(c.getBirthDate())
                    .loyaltyPoints(c.getLoyaltyPoints())
                    .completedVisits(c.getCompletedVisits())
                    .tier(tier)
                    .visitsToNextTier(visitsToNextTier)
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfile {
        @NotBlank
        @Size(max = 120)
        private String fullName;
        @Email
        @Size(max = 120)
        private String email;
        @Size(max = 500)
        private String address;
        private LocalDate birthDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePassword {
        @NotBlank
        private String currentPassword;
        @NotBlank
        @Size(min = 8, max = 100)
        private String newPassword;
    }

    /** What a customer may see about a stylist: no contact details or login information. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StylistInfo {
        private Long id;
        private String fullName;
        private String specialization;
        private String bio;

        public static StylistInfo from(Stylist s) {
            return StylistInfo.builder()
                    .id(s.getId())
                    .fullName(s.getFullName())
                    .specialization(s.getSpecialization())
                    .bio(s.getBio())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookRequest {
        @NotNull
        private Long stylistId;
        @NotNull
        private Long serviceId;
        @NotNull
        private AppointmentType type;
        @NotNull
        private LocalDateTime startTime;
        /** Required for home-service bookings. */
        @Size(max = 500)
        private String address;
        @Size(max = 1000)
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RescheduleRequest {
        @NotNull
        private LocalDateTime startTime;
        /** Optional: move to a different stylist. */
        private Long stylistId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationResponse {
        private Long id;
        private NotificationType type;
        private String title;
        private String message;
        private Long appointmentId;
        private boolean seen;
        private LocalDateTime createdAt;

        public static NotificationResponse from(Notification n) {
            return NotificationResponse.builder()
                    .id(n.getId())
                    .type(n.getType())
                    .title(n.getTitle())
                    .message(n.getMessage())
                    .appointmentId(n.getAppointmentId())
                    .seen(n.isSeen())
                    .createdAt(n.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnreadCount {
        private long unread;
    }
}
