package com.styletrack.dto;

import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class UserDto {

    private UserDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        private String username;
        @NotBlank
        @Size(min = 8, max = 100)
        private String password;
        @NotBlank
        private String fullName;
        @Email
        private String email;
        private String phone;
        @NotNull
        private Role role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotBlank
        private String fullName;
        @Email
        private String email;
        private String phone;
        @NotNull
        private Role role;
        @NotNull
        private Boolean active;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordRequest {
        @NotBlank
        @Size(min = 8, max = 100)
        private String newPassword;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String username;
        private String fullName;
        private String email;
        private String phone;
        private Role role;
        private boolean active;

        public static Response from(User u) {
            return Response.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .role(u.getRole())
                    .active(u.isActive())
                    .build();
        }
    }
}
