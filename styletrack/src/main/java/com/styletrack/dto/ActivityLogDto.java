package com.styletrack.dto;

import com.styletrack.entity.ActivityLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public final class ActivityLogDto {

    private ActivityLogDto() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private LocalDateTime createdAt;
        private Long actorUserId;
        private String actorName;
        private ActivityLog.Action action;
        private String targetType;
        private Long targetId;
        private String details;

        public static Response from(ActivityLog a) {
            return Response.builder()
                    .id(a.getId())
                    .createdAt(a.getCreatedAt())
                    .actorUserId(a.getActorUserId())
                    .actorName(a.getActorName())
                    .action(a.getAction())
                    .targetType(a.getTargetType())
                    .targetId(a.getTargetId())
                    .details(a.getDetails())
                    .build();
        }
    }
}
