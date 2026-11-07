package com.styletrack.controller;

import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.PaymentStatus;
import com.styletrack.service.AppointmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Appointments & home-service bookings")
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public PageResponse<AppointmentDto.Response> search(
            @RequestParam(required = false) Long stylistId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) AppointmentType type,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return appointmentService.search(stylistId, customerId, status, type, paymentStatus, from, to, pageable);
    }

    /** Home-service booking queue; filter with status=PENDING to see requests awaiting accept/reject. */
    @GetMapping("/home-service")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public PageResponse<AppointmentDto.Response> homeService(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return appointmentService.search(null, null, status, AppointmentType.HOME_SERVICE, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public AppointmentDto.Response get(@PathVariable Long id) {
        return appointmentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public AppointmentDto.Response create(@Valid @RequestBody AppointmentDto.CreateRequest request) {
        return appointmentService.create(request);
    }

    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public AppointmentDto.Response reschedule(@PathVariable Long id,
                                              @Valid @RequestBody AppointmentDto.RescheduleRequest request) {
        return appointmentService.reschedule(id, request);
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public AppointmentDto.Response accept(@PathVariable Long id) {
        return appointmentService.accept(id);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public AppointmentDto.Response reject(@PathVariable Long id,
                                          @Valid @RequestBody AppointmentDto.RejectRequest request) {
        return appointmentService.reject(id, request);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public AppointmentDto.Response cancel(@PathVariable Long id) {
        return appointmentService.cancel(id);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public AppointmentDto.Response complete(@PathVariable Long id) {
        return appointmentService.complete(id);
    }

    @PutMapping("/{id}/payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public AppointmentDto.Response pay(@PathVariable Long id, @Valid @RequestBody AppointmentDto.PaymentRequest request) {
        return appointmentService.markPaid(id, request);
    }
}
