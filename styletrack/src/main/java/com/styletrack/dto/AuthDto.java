package com.styletrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class AuthDto {

    private AuthDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    /** Customer self-registration from the mobile app. The mobile number is the login name. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank
        @Size(max = 120)
        private String fullName;
        @NotBlank
        @Size(max = 30)
        private String phone;
        @Email
        @Size(max = 120)
        private String email;
        @NotBlank
        @Size(min = 8, max = 100)
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        @Builder.Default
        private String tokenType = "Bearer";
        private long expiresInSeconds;
        private UserDto.Response user;
    }
}
