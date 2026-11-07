package com.styletrack.dto;

import com.styletrack.entity.Reward;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class RewardDto {

    private RewardDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotBlank
        @Size(max = 120)
        private String name;
        @Size(max = 500)
        private String description;
        @NotNull
        @Min(1)
        private Integer pointsCost;
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
        private int pointsCost;
        private boolean active;

        public static Response from(Reward r) {
            return Response.builder()
                    .id(r.getId())
                    .name(r.getName())
                    .description(r.getDescription())
                    .pointsCost(r.getPointsCost())
                    .active(r.isActive())
                    .build();
        }
    }
}
