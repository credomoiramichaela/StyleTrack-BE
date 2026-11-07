package com.styletrack.controller;

import com.styletrack.config.AppProperties;
import com.styletrack.payment.PayMongoSignature;
import com.styletrack.service.OnlinePaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoints used by PayMongo and by customers' browsers. Neither needs a login:
 * the webhook is protected by its signature, and the return page shows nothing private.
 */
@Slf4j
@Hidden
@RestController
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final OnlinePaymentService onlinePaymentService;
    private final AppProperties props;

    @PostMapping(value = "/api/payments/paymongo/webhook", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, String>> webhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "Paymongo-Signature", required = false) String signature) {
        AppProperties.PayMongo config = props.getPaymongo();
        if (config.getWebhookSecret() == null || config.getWebhookSecret().isBlank()) {
            log.error("PayMongo webhook received but PAYMONGO_WEBHOOK_SECRET is not set; ignoring it");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "not configured"));
        }
        // The raw body must be used exactly as received: re-serialising it would change the signature.
        if (!PayMongoSignature.verify(signature, rawBody, config.getWebhookSecret(), config.isLive())) {
            log.warn("PayMongo webhook with an invalid signature rejected");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "invalid signature"));
        }
        // Any failure here (for example PayMongo being unreachable) returns an error, so PayMongo sends the event again.
        onlinePaymentService.handleWebhook(rawBody);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** Where GCash sends the customer's browser after they authorise (or cancel) the payment. */
    @GetMapping(value = "/payment-return", produces = MediaType.TEXT_HTML_VALUE)
    public String paymentReturn() {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Back to StyleTrack</title>
                <style>body{font-family:system-ui,sans-serif;background:#F5F3F8;color:#231B33;display:flex;
                align-items:center;justify-content:center;min-height:100vh;margin:0;text-align:center;padding:24px}
                h1{font-size:22px;margin:0 0 8px}p{margin:0;color:#5A4F70;line-height:1.5}</style></head>
                <body><main><h1>Thank you!</h1>
                <p>You can close this page and go back to the StyleTrack app.<br>
                Your booking will show as paid in a moment.</p></main></body></html>
                """;
    }
}
