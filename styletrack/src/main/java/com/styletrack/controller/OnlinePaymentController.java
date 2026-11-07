package com.styletrack.controller;

import com.styletrack.dto.OnlinePaymentDto;
import com.styletrack.entity.enums.OnlinePaymentStatus;
import com.styletrack.service.OnlinePaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Front-desk view of GCash / QR Ph payments made through PayMongo. */
@Tag(name = "Online payments")
@RestController
@RequestMapping("/api/online-payments")
@RequiredArgsConstructor
public class OnlinePaymentController {

    private final OnlinePaymentService onlinePaymentService;

    /** Latest 100 attempts, or every attempt for one booking. {@code status=NEEDS_REFUND} lists money to give back. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<OnlinePaymentDto.StaffView> list(
            @RequestParam(required = false) OnlinePaymentStatus status,
            @RequestParam(required = false) Long appointmentId) {
        return onlinePaymentService.listForStaff(status, appointmentId);
    }

    /** Owner only: confirms the refund was made in the PayMongo dashboard. */
    @PutMapping("/{id}/refunded")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public OnlinePaymentDto.StaffView markRefunded(@PathVariable Long id) {
        return onlinePaymentService.markRefunded(id);
    }
}
