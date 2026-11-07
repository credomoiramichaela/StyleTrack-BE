package com.styletrack.dto;

import com.styletrack.entity.StylistSchedule;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

public final class ScheduleDto {

    private ScheduleDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        @NotNull
        private DayOfWeek dayOfWeek;
        @NotNull
        private LocalTime startTime;
        @NotNull
        private LocalTime endTime;
        private boolean homeServiceAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long stylistId;
        private DayOfWeek dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean homeServiceAvailable;

        public static Response from(StylistSchedule s) {
            return Response.builder()
                    .id(s.getId())
                    .stylistId(s.getStylist().getId())
                    .dayOfWeek(s.getDayOfWeek())
                    .startTime(s.getStartTime())
                    .endTime(s.getEndTime())
                    .homeServiceAvailable(s.isHomeServiceAvailable())
                    .build();
        }
    }
}
