package com.styletrack.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.styletrack.config.AppProperties;
import com.styletrack.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Thin client for the PayMongo REST API (Payment Intents with GCash and QR Ph).
 * Uses the secret key, so it only ever runs on the server. Amounts are in centavos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayMongoClient {

    /**
     * What the customer has to do next. Exactly one of redirectUrl / qrImage is set. {@code testUrl} is PayMongo's
     * simulate-payment page, only returned with a test key.
     */
    public record Started(String paymentIntentId, String status, String redirectUrl, String qrImage, String testUrl) {
    }

    /** The parts of a payment intent we care about. {@code paymentId} is the PayMongo payment (pay_...) once paid. */
    public record Intent(String id, String status, long amountCentavos, String currency, String paymentId,
                         String lastError) {
        public boolean succeeded() {
            return "succeeded".equals(status);
        }
    }

    private final AppProperties props;
    private volatile RestClient http;

    public boolean isConfigured() {
        return props.getPaymongo().isEnabled();
    }

    /**
     * Creates the payment intent, a payment method of the chosen type and attaches one to the other.
     *
     * @param method GCASH or QRPH
     */
    public Started start(long amountCentavos, String method, String description, Map<String, String> metadata,
                         String returnUrl) {
        String type = method.toLowerCase();
        JsonNode intent = post("/payment_intents", Map.of("data", Map.of("attributes", Map.of(
                "amount", amountCentavos,
                "currency", "PHP",
                "payment_method_allowed", java.util.List.of(type),
                "description", description,
                "metadata", metadata))));
        String intentId = text(intent.path("data").path("id"));
        if (intentId == null) {
            throw unavailable("PayMongo returned no payment intent id");
        }

        Map<String, Object> methodAttributes = "qrph".equals(type)
                ? Map.of("type", type, "expiry_seconds", props.getPaymongo().getQrExpirySeconds())
                : Map.of("type", type);
        JsonNode paymentMethod = post("/payment_methods", Map.of("data", Map.of("attributes", methodAttributes)));
        String paymentMethodId = text(paymentMethod.path("data").path("id"));
        if (paymentMethodId == null) {
            throw unavailable("PayMongo returned no payment method id");
        }

        JsonNode attached = post("/payment_intents/" + intentId + "/attach", Map.of("data", Map.of("attributes", Map.of(
                "payment_method", paymentMethodId,
                "return_url", returnUrl))));
        JsonNode attributes = attached.path("data").path("attributes");
        JsonNode nextAction = attributes.path("next_action");
        String redirect = text(nextAction.path("redirect").path("url"));
        String qr = text(nextAction.path("code").path("image_url"));
        if (redirect == null && qr == null) {
            throw unavailable("PayMongo returned no next step for the payment");
        }
        // test mode only: where test_url sits inside next_action isn't documented, so look for it anywhere in it
        String testUrl = text(nextAction.findValue("test_url"));
        if (testUrl != null) {
            log.info("PayMongo test payment {}: simulate it at {}", intentId, testUrl);
        }
        return new Started(intentId, text(attributes.path("status")), redirect, qr, testUrl);
    }

    public Intent retrieve(String paymentIntentId) {
        JsonNode attributes = get("/payment_intents/" + paymentIntentId).path("data").path("attributes");
        String paymentId = null;
        JsonNode payments = attributes.path("payments");
        if (payments.isArray()) {
            for (JsonNode payment : payments) {
                String status = text(payment.path("attributes").path("status"));
                if (paymentId == null || "paid".equals(status)) {
                    paymentId = text(payment.path("id"));
                }
            }
        }
        String lastError = text(attributes.path("last_payment_error").path("failed_message"));
        return new Intent(paymentIntentId, text(attributes.path("status")), attributes.path("amount").asLong(-1),
                text(attributes.path("currency")), paymentId, lastError);
    }

    // ------------------------------------------------------------------ http

    private JsonNode post(String path, Object body) {
        return execute(() -> client().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class), path);
    }

    private JsonNode get(String path) {
        return execute(() -> client().get().uri(path).retrieve().body(JsonNode.class), path);
    }

    private JsonNode execute(java.util.function.Supplier<JsonNode> call, String path) {
        try {
            JsonNode body = call.get();
            if (body == null) {
                throw unavailable("Empty response from PayMongo for " + path);
            }
            return body;
        } catch (RestClientResponseException e) {
            // The body explains what PayMongo disliked; it never contains our keys, but keep it out of the customer's view.
            String detail = e.getResponseBodyAsString();
            log.warn("PayMongo {} failed with HTTP {}: {}", path, e.getStatusCode().value(),
                    detail.length() > 600 ? detail.substring(0, 600) + "…" : detail);
            throw unavailable(null);
        } catch (RestClientException e) {
            log.warn("PayMongo {} could not be reached: {}", path, e.getMessage());
            throw unavailable(null);
        }
    }

    private RestClient client() {
        RestClient local = http;
        if (local == null) {
            synchronized (this) {
                if (http == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    int millis = props.getPaymongo().getTimeoutSeconds() * 1000;
                    factory.setConnectTimeout(millis);
                    factory.setReadTimeout(millis);
                    String credentials = props.getPaymongo().getSecretKey() + ":";
                    http = RestClient.builder()
                            .baseUrl(props.getPaymongo().getApiBase())
                            .requestFactory(factory)
                            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic "
                                    + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                            .build();
                }
                local = http;
            }
        }
        return local;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static BusinessException unavailable(String internalNote) {
        if (internalNote != null) {
            log.warn(internalNote);
        }
        return new BusinessException("Online payment isn't available right now. Please try again, or pay at the salon.",
                HttpStatus.BAD_GATEWAY);
    }
}
