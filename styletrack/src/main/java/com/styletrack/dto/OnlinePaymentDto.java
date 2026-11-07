package com.styletrack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OnlinePaymentDto {

    private OnlinePaymentDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayRequest {
        /** GCASH or QRPH. */
        @NotBlank
        private String method;
    }

    /** What the app shows after starting a payment: open {@code redirectUrl} (GCash) or show {@code qrImage} (QR Ph). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Started {
        private Long paymentId;
        private String method;
        private String status;
        private BigDecimal amount;
        private String redirectUrl;
        /** Base64 image (or data URI) of the QR Ph code. */
        private String qrImage;
        /** PayMongo test mode only: page to simulate the customer paying (or failing). Null with a live key. */
        private String testUrl;
        private LocalDateTime expiresAt;
    }

    /** NONE when the booking has never been paid online. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Status {
        private String status;
        private String method;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Options {
        private boolean enabled;
        private List<String> methods;
    }

    /** What salon staff see about one online payment attempt. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaffView {
        private Long id;
        private Long appointmentId;
        private String customerName;
        private String serviceName;
        /** GCASH or QRPH. */
        private String method;
        /** PENDING, PAID, FAILED, EXPIRED, NEEDS_REFUND or REFUNDED. */
        private String status;
        private BigDecimal amount;
        private String paymentIntentId;
        private String paymongoPaymentId;
        private String failureReason;
        private LocalDateTime expiresAt;
        private LocalDateTime paidAt;
        private LocalDateTime createdAt;
    }
}
