package com.styletrack.dto;

import com.styletrack.entity.Recommendation;
import com.styletrack.entity.enums.RecommendationSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class RecommendationDto {

    private RecommendationDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull
        private Long customerId;
        @NotNull
        private Long serviceId;
        private Long stylistId;
        @NotBlank
        private String message;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Suggestion {
        private Long serviceId;
        private String serviceName;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long customerId;
        private String customerName;
        private Long serviceId;
        private String serviceName;
        private Long stylistId;
        private String stylistName;
        private String message;
        private RecommendationSource source;
        private boolean active;
        private LocalDateTime createdAt;

        public static Response from(Recommendation r) {
            return Response.builder()
                    .id(r.getId())
                    .customerId(r.getCustomer().getId())
                    .customerName(r.getCustomer().getFullName())
                    .serviceId(r.getService().getId())
                    .serviceName(r.getService().getName())
                    .stylistId(r.getStylist() == null ? null : r.getStylist().getId())
                    .stylistName(r.getStylist() == null ? null : r.getStylist().getFullName())
                    .message(r.getMessage())
                    .source(r.getSource())
                    .active(r.isActive())
                    .createdAt(r.getCreatedAt())
                    .build();
        }
    }
}
