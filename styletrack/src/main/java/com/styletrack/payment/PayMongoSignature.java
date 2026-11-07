package com.styletrack.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Checks the {@code Paymongo-Signature} header of a webhook call.
 * Header format: {@code t=<timestamp>,te=<test signature>,li=<live signature>}.
 * The signature is HMAC-SHA256 (hex) of {@code <timestamp>.<raw request body>} with the webhook's signing secret.
 */
public final class PayMongoSignature {

    private PayMongoSignature() {
    }

    public static boolean verify(String header, String rawBody, String secret, boolean live) {
        if (header == null || rawBody == null || secret == null || secret.isBlank()) {
            return false;
        }
        String timestamp = null;
        String test = null;
        String liveSig = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0].trim()) {
                case "t" -> timestamp = kv[1].trim();
                case "te" -> test = kv[1].trim();
                case "li" -> liveSig = kv[1].trim();
                default -> { }
            }
        }
        String given = live ? liveSig : test;
        if (timestamp == null || timestamp.isEmpty() || given == null || given.isEmpty()) {
            return false;
        }
        String expected = sign(timestamp + "." + rawBody, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                given.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}
