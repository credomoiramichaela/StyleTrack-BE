package com.styletrack.controller;

import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.LoyaltyDto;
import com.styletrack.dto.MeDto;
import com.styletrack.dto.OnlinePaymentDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.dto.RecommendationDto;
import com.styletrack.dto.ServiceDto;
import com.styletrack.dto.StylistDto;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.service.MeService;
import com.styletrack.service.OnlinePaymentService;
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
import java.util.List;

/** Customer mobile app API. Only accounts with the CUSTOMER role can call it, and only see their own data. */
@Tag(name = "Customer app")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class MeController {

    private final MeService meService;
    private final OnlinePaymentService onlinePaymentService;

    @GetMapping
    public MeDto.Profile profile() {
        return meService.profile();
    }

    @PutMapping
    public MeDto.Profile updateProfile(@Valid @RequestBody MeDto.UpdateProfile request) {
        return meService.updateProfile(request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody MeDto.ChangePassword request) {
        meService.changePassword(request);
    }

    @GetMapping("/services")
    public List<ServiceDto.Response> services() {
        return meService.services();
    }

    @GetMapping("/stylists")
    public List<MeDto.StylistInfo> stylists() {
        return meService.stylists();
    }

    @GetMapping("/stylists/{stylistId}/availability")
    public StylistDto.Availability availability(
            @PathVariable Long stylistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long serviceId,
            @RequestParam(defaultValue = "false") boolean homeService) {
        return meService.availability(stylistId, date, serviceId, homeService);
    }

    @GetMapping("/appointments")
    public PageResponse<AppointmentDto.Response> appointments(
            @RequestParam(required = false) AppointmentStatus status,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return meService.appointments(status, pageable);
    }

    @GetMapping("/appointments/{id}")
    public AppointmentDto.Response appointment(@PathVariable Long id) {
        return meService.appointment(id);
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto.Response book(@Valid @RequestBody MeDto.BookRequest request) {
        return meService.book(request);
    }

    @PutMapping("/appointments/{id}/cancel")
    public AppointmentDto.Response cancel(@PathVariable Long id) {
        return meService.cancel(id);
    }

    @PutMapping("/appointments/{id}/reschedule")
    public AppointmentDto.Response reschedule(@PathVariable Long id,
                                              @Valid @RequestBody MeDto.RescheduleRequest request) {
        return meService.reschedule(id, request);
    }

    @GetMapping("/payment-options")
    public OnlinePaymentDto.Options paymentOptions() {
        return onlinePaymentService.options();
    }

    /** Starts a GCash or QR Ph payment for this booking. */
    @PostMapping("/appointments/{id}/pay")
    public OnlinePaymentDto.Started pay(@PathVariable Long id, @Valid @RequestBody OnlinePaymentDto.PayRequest request) {
        return onlinePaymentService.start(id, request.getMethod());
    }

    /** Progress of the latest online payment for this booking (checks with PayMongo while it is pending). */
    @GetMapping("/appointments/{id}/online-payment")
    public OnlinePaymentDto.Status onlinePayment(@PathVariable Long id) {
        return onlinePaymentService.status(id);
    }

    @GetMapping("/loyalty")
    public LoyaltyDto.Balance loyalty() {
        return meService.loyalty();
    }

    @GetMapping("/loyalty/transactions")
    public PageResponse<LoyaltyDto.TransactionResponse> loyaltyHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return meService.loyaltyHistory(pageable);
    }

    @GetMapping("/recommendations")
    public List<RecommendationDto.Response> recommendations() {
        return meService.recommendations();
    }

    @GetMapping("/notifications")
    public PageResponse<MeDto.NotificationResponse> notifications(
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return meService.notifications(pageable);
    }

    @GetMapping("/notifications/unread-count")
    public MeDto.UnreadCount unreadCount() {
        return meService.unreadCount();
    }

    @PutMapping("/notifications/{id}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markSeen(@PathVariable Long id) {
        meService.markSeen(id);
    }

    @PutMapping("/notifications/seen-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllSeen() {
        meService.markAllSeen();
    }
}
