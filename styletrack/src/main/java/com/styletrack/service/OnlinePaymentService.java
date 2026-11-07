package com.styletrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.styletrack.config.AppProperties;
import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.OnlinePaymentDto;
import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.OnlinePayment;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.NotificationType;
import com.styletrack.entity.enums.OnlinePaymentStatus;
import com.styletrack.entity.enums.PaymentStatus;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.payment.PayMongoClient;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.OnlinePaymentRepository;
import com.styletrack.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Online payment of a booking through PayMongo (GCash or QR Ph).
 * <p>
 * The customer never tells us a payment succeeded: we only believe PayMongo. A webhook or the app's status
 * check triggers {@link #settle(String)}, which asks PayMongo for the payment intent and only then marks the
 * booking paid. The class is deliberately not {@code @Transactional}: calls to PayMongo must not hold a database
 * transaction open, so each database step runs in its own short transaction.
 */
@Slf4j
@Service
public class OnlinePaymentService {

    private static final long MIN_CENTAVOS = 100;
    private static final long GCASH_MAX_CENTAVOS = 10_000_000;
    private static final Duration GCASH_VALIDITY = Duration.ofHours(4);
    private static final Duration RECHECK_AFTER = Duration.ofSeconds(3);

    private final PayMongoClient payMongo;
    private final OnlinePaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentService appointmentService;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;
    private final AppProperties props;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public OnlinePaymentService(PayMongoClient payMongo, OnlinePaymentRepository paymentRepository,
                                AppointmentRepository appointmentRepository, CustomerRepository customerRepository,
                                AppointmentService appointmentService, NotificationService notificationService,
                                CurrentUser currentUser, AppProperties props, ObjectMapper json,
                                PlatformTransactionManager transactionManager) {
        this.payMongo = payMongo;
        this.paymentRepository = paymentRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerRepository = customerRepository;
        this.appointmentService = appointmentService;
        this.notificationService = notificationService;
        this.currentUser = currentUser;
        this.props = props;
        this.json = json;
        this.tx = new TransactionTemplate(transactionManager);
    }

    private record Context(Long appointmentId, long amountCentavos, String description) {
    }

    // ------------------------------------------------------------ customer

    public OnlinePaymentDto.Options options() {
        boolean enabled = payMongo.isConfigured();
        return OnlinePaymentDto.Options.builder().enabled(enabled)
                .methods(enabled ? List.of("GCASH", "QRPH") : List.of()).build();
    }

    /** Starts a GCash or QR Ph payment for one of the signed-in customer's bookings. */
    public OnlinePaymentDto.Started start(Long appointmentId, String rawMethod) {
        if (!payMongo.isConfigured()) {
            throw new BusinessException("Online payment isn't set up yet. Please pay at the salon.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        String method = normalizeMethod(rawMethod);

        Context ctx = tx.execute(status -> {
            Appointment a = owned(appointmentId);
            if (a.getPaymentStatus() == PaymentStatus.PAID) {
                throw new BusinessException("This booking is already paid", HttpStatus.CONFLICT);
            }
            if (a.getStatus() != AppointmentStatus.ACCEPTED && a.getStatus() != AppointmentStatus.COMPLETED) {
                throw new BusinessException(a.getStatus() == AppointmentStatus.PENDING
                        ? "You can pay once the salon has confirmed your booking."
                        : "This booking can't be paid.");
            }
            long centavos = toCentavos(a.getTotalAmount());
            if (centavos < MIN_CENTAVOS) {
                throw new BusinessException("This booking has nothing to pay online.");
            }
            if ("GCASH".equals(method) && centavos > GCASH_MAX_CENTAVOS) {
                throw new BusinessException("This amount is above the GCash limit. Please pay with QR Ph or at the salon.");
            }
            return new Context(a.getId(), centavos,
                    "StyleTrack booking #" + a.getId() + " - " + a.getService().getName());
        });

        PayMongoClient.Started started = payMongo.start(ctx.amountCentavos(), method, ctx.description(),
                Map.of("appointment_id", String.valueOf(ctx.appointmentId())), returnUrl());

        LocalDateTime expiresAt = LocalDateTime.now().plus("QRPH".equals(method)
                ? Duration.ofSeconds(props.getPaymongo().getQrExpirySeconds()) : GCASH_VALIDITY);
        OnlinePayment saved = tx.execute(status -> paymentRepository.save(OnlinePayment.builder()
                .appointment(appointmentRepository.getReferenceById(ctx.appointmentId()))
                .paymentIntentId(started.paymentIntentId())
                .method(method)
                .amountCentavos(ctx.amountCentavos())
                .expiresAt(expiresAt)
                .build()));

        return OnlinePaymentDto.Started.builder()
                .paymentId(saved.getId())
                .method(method)
                .status(OnlinePaymentStatus.PENDING.name())
                .amount(BigDecimal.valueOf(ctx.amountCentavos(), 2))
                .redirectUrl(started.redirectUrl())
                .qrImage(started.qrImage())
                .testUrl(props.getPaymongo().isLive() ? null : started.testUrl())
                .expiresAt(expiresAt)
                .build();
    }

    /** The latest online payment of one of the customer's bookings; checks with PayMongo while it is pending. */
    public OnlinePaymentDto.Status status(Long appointmentId) {
        OnlinePayment latest = tx.execute(s -> {
            owned(appointmentId);
            return paymentRepository.findFirstByAppointmentIdOrderByIdDesc(appointmentId).orElse(null);
        });
        if (latest == null) {
            return OnlinePaymentDto.Status.builder().status("NONE").build();
        }
        if (latest.getStatus() == OnlinePaymentStatus.PENDING && payMongo.isConfigured()) {
            LocalDateTime checked = latest.getLastCheckedAt();
            if (checked == null || checked.isBefore(LocalDateTime.now().minus(RECHECK_AFTER))) {
                settle(latest.getPaymentIntentId());
            }
            latest = paymentRepository.findById(latest.getId()).orElse(latest);
            if (latest.getStatus() == OnlinePaymentStatus.PENDING && latest.getExpiresAt() != null
                    && latest.getExpiresAt().isBefore(LocalDateTime.now())) {
                markIfPending(latest.getPaymentIntentId(), OnlinePaymentStatus.EXPIRED, "The payment window ran out");
                latest = paymentRepository.findById(latest.getId()).orElse(latest);
            }
        }
        return OnlinePaymentDto.Status.builder().status(latest.getStatus().name()).method(latest.getMethod())
                .expiresAt(latest.getExpiresAt()).build();
    }

    // --------------------------------------------------------------- staff

    /** Online payment attempts for the front desk, newest first. Filter by status and/or booking. */
    public List<OnlinePaymentDto.StaffView> listForStaff(OnlinePaymentStatus status, Long appointmentId) {
        return tx.execute(s -> {
            List<OnlinePayment> rows = appointmentId != null
                    ? paymentRepository.findByAppointmentIdOrderByIdDesc(appointmentId)
                    : status != null
                        ? paymentRepository.findTop100ByStatusOrderByIdDesc(status)
                        : paymentRepository.findTop100ByOrderByIdDesc();
            return rows.stream()
                    .filter(p -> status == null || appointmentId == null || p.getStatus() == status)
                    .map(this::toStaffView)
                    .toList();
        });
    }

    /** The owner confirms the money was returned to the customer in the PayMongo dashboard. */
    public OnlinePaymentDto.StaffView markRefunded(Long paymentId) {
        return tx.execute(s -> {
            OnlinePayment p = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Online payment", paymentId));
            if (p.getStatus() != OnlinePaymentStatus.NEEDS_REFUND) {
                throw new BusinessException("Only payments that need a refund can be marked as refunded.", HttpStatus.CONFLICT);
            }
            p.setStatus(OnlinePaymentStatus.REFUNDED);
            log.info("Online payment {} (booking {}) marked as refunded", p.getId(), p.getAppointment().getId());
            return toStaffView(p);
        });
    }

    private OnlinePaymentDto.StaffView toStaffView(OnlinePayment p) {
        Appointment a = p.getAppointment();
        // a payment nobody finished is shown as expired once its window has passed, even before the app next asks
        OnlinePaymentStatus shown = p.getStatus() == OnlinePaymentStatus.PENDING
                && p.getExpiresAt() != null && p.getExpiresAt().isBefore(LocalDateTime.now())
                ? OnlinePaymentStatus.EXPIRED : p.getStatus();
        return OnlinePaymentDto.StaffView.builder()
                .id(p.getId())
                .appointmentId(a.getId())
                .customerName(a.getCustomer().getFullName())
                .serviceName(a.getService().getName())
                .method(p.getMethod())
                .status(shown.name())
                .amount(BigDecimal.valueOf(p.getAmountCentavos(), 2))
                .paymentIntentId(p.getPaymentIntentId())
                .paymongoPaymentId(p.getPaymongoPaymentId())
                .failureReason(p.getFailureReason())
                .expiresAt(p.getExpiresAt())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }

    // ------------------------------------------------------------- webhook

    /**
     * Handles a PayMongo webhook that has already passed signature verification. The event only tells us which
     * payment intent to look at: the real state is always fetched from PayMongo.
     */
    public void handleWebhook(String rawBody) {
        JsonNode event;
        try {
            event = json.readTree(rawBody);
        } catch (Exception e) {
            log.warn("PayMongo webhook with unreadable body ignored");
            return;
        }
        JsonNode eventAttributes = event.path("data").path("attributes");
        String type = eventAttributes.path("type").asText("");
        String intentId = eventAttributes.path("data").path("attributes").path("payment_intent_id").asText(null);
        log.info("PayMongo webhook {} (intent {})", type, intentId);
        if (intentId == null || intentId.isBlank()) {
            return; // not about a payment intent (or a shape we don't use); the app's status check covers it
        }
        switch (type) {
            case "payment.paid" -> settle(intentId);
            case "payment.failed" -> {
                String reason = eventAttributes.path("data").path("attributes").path("failed_message").asText("Payment failed");
                markIfPending(intentId, OnlinePaymentStatus.FAILED, reason);
            }
            default -> log.debug("PayMongo webhook {} ignored", type);
        }
    }

    // ---------------------------------------------------------- settlement

    /** Asks PayMongo about the intent and, if it has been paid, marks the booking paid. Safe to call repeatedly. */
    public void settle(String paymentIntentId) {
        OnlinePayment known = paymentRepository.findByPaymentIntentId(paymentIntentId).orElse(null);
        if (known == null) {
            log.warn("PayMongo reported intent {} that we have no record of", paymentIntentId);
            return;
        }
        if (known.getStatus() == OnlinePaymentStatus.PAID || known.getStatus() == OnlinePaymentStatus.NEEDS_REFUND) {
            return;
        }
        tx.executeWithoutResult(s -> paymentRepository.findByPaymentIntentId(paymentIntentId)
                .ifPresent(p -> p.setLastCheckedAt(LocalDateTime.now())));

        PayMongoClient.Intent intent = payMongo.retrieve(paymentIntentId);
        if (!intent.succeeded()) {
            return;
        }

        tx.executeWithoutResult(s -> {
            OnlinePayment p = paymentRepository.lockByPaymentIntentId(paymentIntentId).orElseThrow();
            if (p.getStatus() == OnlinePaymentStatus.PAID || p.getStatus() == OnlinePaymentStatus.NEEDS_REFUND) {
                return;
            }
            if (intent.amountCentavos() != p.getAmountCentavos() || !"PHP".equalsIgnoreCase(intent.currency())) {
                log.error("PayMongo intent {} succeeded with {} {} centavos but we expected {} PHP; NOT marking booking {} paid",
                        paymentIntentId, intent.currency(), intent.amountCentavos(), p.getAmountCentavos(),
                        p.getAppointment().getId());
                p.setStatus(OnlinePaymentStatus.FAILED);
                p.setFailureReason("Amount mismatch - check PayMongo");
                return;
            }
            String reference = intent.paymentId() != null ? intent.paymentId() : paymentIntentId;
            p.setPaymongoPaymentId(intent.paymentId());
            p.setPaidAt(LocalDateTime.now());
            Appointment appointment = p.getAppointment();
            if (appointment.getPaymentStatus() == PaymentStatus.PAID
                    || appointment.getStatus() == AppointmentStatus.CANCELLED
                    || appointment.getStatus() == AppointmentStatus.REJECTED) {
                p.setStatus(OnlinePaymentStatus.NEEDS_REFUND);
                log.warn("PayMongo payment {} arrived for booking {} which is {} / {}: it needs a refund",
                        reference, appointment.getId(), appointment.getStatus(), appointment.getPaymentStatus());
                return;
            }
            appointmentService.markPaid(appointment.getId(), new AppointmentDto.PaymentRequest(p.getMethod(), reference));
            p.setStatus(OnlinePaymentStatus.PAID);
            notificationService.notifyBooking(appointment, NotificationType.PAYMENT_RECEIVED, "Payment received",
                    "We received your " + ("GCASH".equals(p.getMethod()) ? "GCash" : "QR Ph") + " payment of ₱"
                            + BigDecimal.valueOf(p.getAmountCentavos(), 2).toPlainString() + ". Thank you!");
            log.info("Booking {} paid online via {} ({})", appointment.getId(), p.getMethod(), reference);
        });
    }

    private void markIfPending(String paymentIntentId, OnlinePaymentStatus to, String reason) {
        tx.executeWithoutResult(s -> paymentRepository.lockByPaymentIntentId(paymentIntentId).ifPresent(p -> {
            if (p.getStatus() == OnlinePaymentStatus.PENDING) {
                p.setStatus(to);
                p.setFailureReason(reason == null ? null : reason.length() > 250 ? reason.substring(0, 250) : reason);
            }
        }));
    }

    // -------------------------------------------------------------- helpers

    private String returnUrl() {
        String base = props.getPublicUrl() == null ? "" : props.getPublicUrl().replaceAll("/+$", "");
        return base + "/payment-return";
    }

    static String normalizeMethod(String raw) {
        String key = raw == null ? "" : raw.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (!"GCASH".equals(key) && !"QRPH".equals(key)) {
            throw new BusinessException("Online payment method must be GCASH or QRPH");
        }
        return key;
    }

    static long toCentavos(BigDecimal pesos) {
        if (pesos == null) {
            return 0;
        }
        return pesos.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** The signed-in customer's own booking; anyone else's looks like it does not exist. */
    private Appointment owned(Long appointmentId) {
        Customer customer = customerRepository.findByUserId(currentUser.get().getId())
                .orElseThrow(() -> new BusinessException("No customer profile is linked to this account", HttpStatus.FORBIDDEN));
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));
        if (!a.getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("Appointment", appointmentId);
        }
        return a;
    }
}
