package com.styletrack.controller;

import com.styletrack.config.AppProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Settings")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppProperties appProperties;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSettings {
        /** Public URL of the salon's QR Ph code; empty when not configured. */
        private String qrPhImageUrl;
        private String qrPhAccountName;
    }

    /** What the front desk shows customers when they pay by QR Ph. */
    @GetMapping("/payment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PaymentSettings payment() {
        AppProperties.Payment p = appProperties.getPayment();
        return new PaymentSettings(p.getQrPhImageUrl(), p.getQrPhAccountName());
    }
}
