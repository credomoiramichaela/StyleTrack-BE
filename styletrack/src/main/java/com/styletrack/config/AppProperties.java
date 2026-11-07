package com.styletrack.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "styletrack")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Loyalty loyalty = new Loyalty();
    private Booking booking = new Booking();
    private Cors cors = new Cors();
    private Seed seed = new Seed();
    private Payment payment = new Payment();
    private PayMongo paymongo = new PayMongo();
    /** Public base URL of this API (used for the page customers land on after paying). */
    private String publicUrl = "http://localhost:8080";

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMinutes = 480;
    }

    @Data
    public static class Loyalty {
        /** One loyalty point is earned per this many pesos spent. */
        private int pesosPerPoint = 100;
        /** Completed visits needed to be recognised as a Silver / Gold loyal customer. */
        private int silverVisits = 5;
        private int goldVisits = 10;
    }

    @Data
    public static class Payment {
        /** Public URL of the salon's QR Ph code image, shown when recording a QR Ph payment. */
        private String qrPhImageUrl = "";
        private String qrPhAccountName = "Jonathan Hair Studio";
    }

    /** PayMongo online payments (GCash and QR Ph). Leave the secret key empty to switch them off. */
    @Data
    public static class PayMongo {
        /** sk_test_... or sk_live_.... Server-side only: never sent to the mobile app. */
        private String secretKey = "";
        /** Signing secret returned when the webhook is created in PayMongo (whsk_...). */
        private String webhookSecret = "";
        private String apiBase = "https://api.paymongo.com/v1";
        /** How long a QR Ph code stays valid (PayMongo allows 60 to 9000 seconds). */
        private int qrExpirySeconds = 1800;
        private int timeoutSeconds = 15;

        public boolean isEnabled() {
            return secretKey != null && !secretKey.isBlank();
        }

        public boolean isLive() {
            return secretKey != null && secretKey.startsWith("sk_live");
        }
    }

    @Data
    public static class Booking {
        private BigDecimal homeServiceFee = BigDecimal.ZERO;
        /** Extra time reserved around a home-service booking for travel. */
        private int travelBufferMinutes = 30;
        private int slotMinutes = 30;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3000");
    }

    @Data
    public static class Seed {
        private String adminUsername = "admin";
        private String adminPassword = "ChangeMe123!";
        private boolean demo = false;
    }
}
