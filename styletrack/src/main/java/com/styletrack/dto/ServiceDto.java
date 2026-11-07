package com.styletrack.dto;

import com.styletrack.entity.ServiceItem;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public final class ServiceDto {

    private ServiceDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        private String name;
        private String description;
        private String category;
        @NotNull
        @DecimalMin("0.00")
        private BigDecimal price;
        @NotNull
        @Min(5)
        private Integer durationMinutes;
        private boolean homeServiceAvailable;
        @Min(0)
        private Integer pointsAwarded;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private String category;
        private BigDecimal price;
        private int durationMinutes;
        private boolean homeServiceAvailable;
        private Integer pointsAwarded;
        private boolean active;

        public static Response from(ServiceItem s) {
            return Response.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .description(s.getDescription())
                    .category(s.getCategory())
                    .price(s.getPrice())
                    .durationMinutes(s.getDurationMinutes())
                    .homeServiceAvailable(s.isHomeServiceAvailable())
                    .pointsAwarded(s.getPointsAwarded())
                    .active(s.isActive())
                    .build();
        }
    }
}
