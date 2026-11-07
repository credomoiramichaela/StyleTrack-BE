package com.styletrack.service;

import com.styletrack.config.AppProperties;
import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.ActivityLog;
import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.ServiceItem;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.NotificationType;
import com.styletrack.entity.enums.PaymentStatus;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.AppointmentSpecs;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.ServiceItemRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.StylistScheduleRepository;
import com.styletrack.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Booking rules enforced here:
 * <ul>
 *   <li>the slot must fall inside one of the stylist's schedule blocks (home service needs a home-service block);</li>
 *   <li>a stylist and a customer can never hold two overlapping PENDING/ACCEPTED bookings;</li>
 *   <li>home-service bookings reserve a travel buffer around the visit;</li>
 *   <li>the stylist row is locked while booking so concurrent requests cannot take the same slot.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {

    private static final List<AppointmentStatus> ACTIVE =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.ACCEPTED);

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final StylistRepository stylistRepository;
    private final ServiceItemRepository serviceRepository;
    private final StylistScheduleRepository scheduleRepository;
    private final LoyaltyService loyaltyService;
    private final NotificationService notificationService;
    private final CurrentUser currentUser;
    private final AppProperties appProperties;
    private final ActivityLogService activityLogService;

    // ---------------------------------------------------------------- queries

    public PageResponse<AppointmentDto.Response> search(Long stylistId, Long customerId, AppointmentStatus status,
                                                        AppointmentType type, LocalDate from, LocalDate to,
                                                        Pageable pageable) {
        return search(stylistId, customerId, status, type, null, from, to, pageable);
    }

    public PageResponse<AppointmentDto.Response> search(Long stylistId, Long customerId, AppointmentStatus status,
                                                        AppointmentType type, PaymentStatus paymentStatus,
                                                        LocalDate from, LocalDate to, Pageable pageable) {
        Long effectiveStylist = isStylist() ? currentUser.requireStylistId() : stylistId;
        var spec = AppointmentSpecs.filter(effectiveStylist, customerId, status, type, paymentStatus,
                from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay());
        return PageResponse.of(appointmentRepository.findAll(spec, pageable).map(AppointmentDto.Response::from));
    }

    public AppointmentDto.Response get(Long id) {
        Appointment appointment = find(id);
        assertStylistOwnsIfStylist(appointment);
        return AppointmentDto.Response.from(appointment);
    }

    // --------------------------------------------------------------- commands

    /** Booking entered by staff: salon bookings are confirmed straight away. */
    @Transactional
    public AppointmentDto.Response create(AppointmentDto.CreateRequest r) {
        return create(r, false);
    }

    /**
     * @param customerRequest true when the customer booked from the mobile app: every such booking
     *                        waits for the salon to accept or reject it.
     */
    @Transactional
    public AppointmentDto.Response create(AppointmentDto.CreateRequest r, boolean customerRequest) {
        Customer customer = customerRepository.findById(r.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", r.getCustomerId()));
        if (!customer.isActive()) {
            throw new BusinessException("Customer is inactive");
        }
        ServiceItem service = serviceRepository.findById(r.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", r.getServiceId()));
        if (!service.isActive()) {
            throw new BusinessException("Service is not available");
        }
        Stylist stylist = lockActiveStylist(r.getStylistId());

        boolean home = r.getType() == AppointmentType.HOME_SERVICE;
        if (home) {
            if (!service.isHomeServiceAvailable()) {
                throw new BusinessException("This service is not offered as a home service");
            }
            if (!StringUtils.hasText(r.getAddress())) {
                throw new BusinessException("An address is required for home-service bookings");
            }
        }

        LocalDateTime start = r.getStartTime().truncatedTo(ChronoUnit.MINUTES);
        if (start.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new BusinessException("Start time cannot be in the past");
        }
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        validateSlot(stylist, r.getType(), start, end, null, customer.getId());

        BigDecimal fee = home ? appProperties.getBooking().getHomeServiceFee() : BigDecimal.ZERO;
        Appointment appointment = Appointment.builder()
                .customer(customer)
                .stylist(stylist)
                .service(service)
                .startTime(start)
                .endTime(end)
                .type(r.getType())
                // Home-service and customer-made requests need an explicit accept/reject; salon bookings made by staff are confirmed.
                .status(home || customerRequest ? AppointmentStatus.PENDING : AppointmentStatus.ACCEPTED)
                .address(home ? r.getAddress().trim() : null)
                .notes(r.getNotes())
                .homeServiceFee(fee)
                .totalAmount(service.getPrice().add(fee))
                .build();
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment {} created ({} {} for customer {})", saved.getId(), saved.getType(),
                saved.getStatus(), customer.getId());
        return AppointmentDto.Response.from(saved);
    }

    @Transactional
    public AppointmentDto.Response reschedule(Long id, AppointmentDto.RescheduleRequest r) {
        return reschedule(id, r, false);
    }

    /** A customer-initiated move always goes back to PENDING so the salon can confirm the new time. */
    @Transactional
    public AppointmentDto.Response reschedule(Long id, AppointmentDto.RescheduleRequest r, boolean byCustomer) {
        Appointment appointment = find(id);
        requireStatus(appointment, ACTIVE, "Only pending or accepted appointments can be rescheduled");

        Stylist stylist = r.getStylistId() == null || r.getStylistId().equals(appointment.getStylist().getId())
                ? lockActiveStylist(appointment.getStylist().getId())
                : lockActiveStylist(r.getStylistId());

        LocalDateTime start = r.getStartTime().truncatedTo(ChronoUnit.MINUTES);
        if (start.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new BusinessException("Start time cannot be in the past");
        }
        LocalDateTime end = start.plusMinutes(appointment.getService().getDurationMinutes());
        validateSlot(stylist, appointment.getType(), start, end, appointment.getId(),
                appointment.getCustomer().getId());

        appointment.setStylist(stylist);
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        if (byCustomer || appointment.getType() == AppointmentType.HOME_SERVICE) {
            appointment.setStatus(AppointmentStatus.PENDING); // new time must be re-confirmed
        }
        if (!byCustomer) {
            notificationService.notifyBooking(appointment, NotificationType.BOOKING_RESCHEDULED,
                    "Your booking was moved", appointment.getStatus() == AppointmentStatus.PENDING
                            ? "It needs to be confirmed again." : null);
        }
        return AppointmentDto.Response.from(appointment);
    }

    @Transactional
    public AppointmentDto.Response accept(Long id) {
        Appointment appointment = find(id);
        assertStylistOwnsIfStylist(appointment);
        requireStatus(appointment, List.of(AppointmentStatus.PENDING), "Only pending bookings can be accepted");
        appointment.setStatus(AppointmentStatus.ACCEPTED);
        appointment.setRejectionReason(null);
        appointment.setSuggestedStylist(null);
        notificationService.notifyBooking(appointment, NotificationType.BOOKING_ACCEPTED, "Booking confirmed", null);
        activityLogService.record(ActivityLog.Action.APPOINTMENT_ACCEPTED, "Appointment", appointment.getId(), null);
        return AppointmentDto.Response.from(appointment);
    }

    @Transactional
    public AppointmentDto.Response reject(Long id, AppointmentDto.RejectRequest r) {
        Appointment appointment = find(id);
        assertStylistOwnsIfStylist(appointment);
        requireStatus(appointment, List.of(AppointmentStatus.PENDING), "Only pending bookings can be rejected");
        appointment.setStatus(AppointmentStatus.REJECTED);
        appointment.setRejectionReason(r.getReason());
        appointment.setSuggestedStylist(r.getSuggestedStylistId() == null ? null
                : stylistRepository.findById(r.getSuggestedStylistId())
                        .orElseThrow(() -> new ResourceNotFoundException("Stylist", r.getSuggestedStylistId())));
        notificationService.notifyBooking(appointment, NotificationType.BOOKING_REJECTED,
                "Booking not accepted", "Reason: " + r.getReason());
        activityLogService.record(ActivityLog.Action.APPOINTMENT_REJECTED,
                "Appointment", appointment.getId(), "Reason: " + r.getReason());
        return AppointmentDto.Response.from(appointment);
    }

    @Transactional
    public AppointmentDto.Response cancel(Long id) {
        return cancel(id, false);
    }

    @Transactional
    public AppointmentDto.Response cancel(Long id, boolean byCustomer) {
        Appointment appointment = find(id);
        requireStatus(appointment, ACTIVE, "Only pending or accepted appointments can be cancelled");
        appointment.setStatus(AppointmentStatus.CANCELLED);
        if (!byCustomer) {
            notificationService.notifyBooking(appointment, NotificationType.BOOKING_CANCELLED,
                    "Your booking was cancelled", "Please contact the salon to rebook.");
        }
        activityLogService.record(ActivityLog.Action.APPOINTMENT_CANCELLED, "Appointment", appointment.getId(),
                byCustomer ? "Cancelled by customer" : "Cancelled by staff");
        return AppointmentDto.Response.from(appointment);
    }

    @Transactional
    public AppointmentDto.Response complete(Long id) {
        Appointment appointment = find(id);
        assertStylistOwnsIfStylist(appointment);
        requireStatus(appointment, List.of(AppointmentStatus.ACCEPTED), "Only accepted appointments can be completed");
        appointment.setStatus(AppointmentStatus.COMPLETED);
        loyaltyService.registerCompletedVisit(appointment.getCustomer().getId());
        appointment.setPointsEarned(loyaltyService.earnForAppointment(appointment));
        notificationService.notifyBooking(appointment, NotificationType.BOOKING_COMPLETED, "Thanks for visiting!",
                appointment.getPointsEarned() > 0 ? "You earned " + appointment.getPointsEarned() + " loyalty points." : null);
        return AppointmentDto.Response.from(appointment);
    }

    @Transactional
    public AppointmentDto.Response markPaid(Long id, AppointmentDto.PaymentRequest r) {
        Appointment appointment = find(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.REJECTED) {
            throw new BusinessException("Cancelled or rejected appointments cannot be paid");
        }
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Appointment is already paid", HttpStatus.CONFLICT);
        }
        String method = normalizeMethod(r.getMethod());
        String reference = r.getReference() == null || r.getReference().isBlank() ? null : r.getReference().trim();
        if (!"CASH".equals(method)) {
            if (reference == null) {
                throw new BusinessException("A reference number is required for " + method + " payments");
            }
            if (appointmentRepository.existsByPaymentMethodAndPaymentReferenceAndIdNot(method, reference, id)) {
                throw new BusinessException("This " + method + " reference number was already used on another payment",
                        HttpStatus.CONFLICT);
            }
        }
        appointment.setPaymentStatus(PaymentStatus.PAID);
        appointment.setPaymentMethod(method);
        appointment.setPaymentReference(reference);
        appointment.setPaidAt(LocalDateTime.now());
        return AppointmentDto.Response.from(appointment);
    }

    // ---------------------------------------------------------------- helpers

    /** Accepts "QR Ph", "qr_ph", "QRPH", "GCash", "Cash"... and returns CASH, QRPH or GCASH. */
    private static String normalizeMethod(String raw) {
        String key = raw == null ? "" : raw.replaceAll("[^A-Za-z]", "").toUpperCase();
        if (!List.of("CASH", "QRPH", "GCASH").contains(key)) {
            throw new BusinessException("Payment method must be one of: CASH, QRPH, GCASH");
        }
        return key;
    }

    private void validateSlot(Stylist stylist, AppointmentType type, LocalDateTime start, LocalDateTime end,
                              Long excludeId, Long customerId) {
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BusinessException("An appointment must start and end on the same day");
        }
        boolean home = type == AppointmentType.HOME_SERVICE;
        boolean insideShift = scheduleRepository
                .findByStylistIdAndDayOfWeek(stylist.getId(), start.getDayOfWeek()).stream()
                .anyMatch(block -> !start.toLocalTime().isBefore(block.getStartTime())
                        && !end.toLocalTime().isAfter(block.getEndTime())
                        && (!home || block.isHomeServiceAvailable()));
        if (!insideShift) {
            throw new BusinessException("The stylist is not scheduled to work" + (home ? " home service" : "")
                    + " at the requested time", HttpStatus.CONFLICT);
        }

        long exclude = excludeId == null ? -1L : excludeId;
        LocalDateTime from = start;
        LocalDateTime to = end;
        if (home) {
            int buffer = appProperties.getBooking().getTravelBufferMinutes();
            from = start.minusMinutes(buffer);
            to = end.plusMinutes(buffer);
        }
        if (appointmentRepository.existsStylistOverlap(stylist.getId(), ACTIVE, from, to, exclude)) {
            throw new BusinessException("The stylist already has a booking that overlaps this time"
                    + (home ? " (including travel buffer)" : ""), HttpStatus.CONFLICT);
        }
        if (appointmentRepository.existsCustomerOverlap(customerId, ACTIVE, start, end, exclude)) {
            throw new BusinessException("The customer already has a booking that overlaps this time",
                    HttpStatus.CONFLICT);
        }
    }

    private Stylist lockActiveStylist(Long stylistId) {
        Stylist stylist = stylistRepository.findByIdForUpdate(stylistId)
                .orElseThrow(() -> new ResourceNotFoundException("Stylist", stylistId));
        if (!stylist.isActive()) {
            throw new BusinessException("Stylist is not active");
        }
        return stylist;
    }

    private void requireStatus(Appointment a, List<AppointmentStatus> allowed, String message) {
        if (!allowed.contains(a.getStatus())) {
            throw new BusinessException(message + " (current status: " + a.getStatus() + ")", HttpStatus.CONFLICT);
        }
    }

    private boolean isStylist() {
        return currentUser.get().getRole() == Role.STYLIST;
    }

    /** Stylists may only see and act on their own appointments. */
    private void assertStylistOwnsIfStylist(Appointment appointment) {
        User me = currentUser.get();
        if (me.getRole() != Role.STYLIST) {
            return;
        }
        User owner = appointment.getStylist().getUser();
        if (owner == null || !owner.getId().equals(me.getId())) {
            throw new BusinessException("You can only manage your own appointments", HttpStatus.FORBIDDEN);
        }
    }

    private Appointment find(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }
}
