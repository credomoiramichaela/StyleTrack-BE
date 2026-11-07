package com.styletrack.config;

import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.OnlinePayment;
import com.styletrack.entity.ServiceItem;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.OnlinePaymentStatus;
import com.styletrack.entity.enums.PaymentStatus;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.OnlinePaymentRepository;
import com.styletrack.repository.ServiceItemRepository;
import com.styletrack.repository.StylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sample bookings with GCash / QR Ph payments in every state, so the payment screens have something to show
 * without calling PayMongo. Loaded only when SEED_DEMO=true, and only once (it skips if any online payment exists).
 * These rows are fake: the PayMongo ids all start with "demo_".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoPaymentSeeder {

    private final CustomerRepository customerRepository;
    private final StylistRepository stylistRepository;
    private final ServiceItemRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final OnlinePaymentRepository paymentRepository;

    /** Runs inside the caller's transaction. */
    public void seed() {
        if (paymentRepository.count() > 0) {
            return;
        }
        List<Customer> customers = customerRepository.findAll();
        List<Stylist> stylists = stylistRepository.findAll();
        List<ServiceItem> services = serviceRepository.findAll();
        if (customers.isEmpty() || stylists.isEmpty() || services.isEmpty()) {
            log.info("Demo online payments skipped: no demo customers, stylists or services yet");
            return;
        }
        Customer maria = customers.get(0);
        Customer juan = customers.get(customers.size() > 1 ? 1 : 0);
        Stylist anna = stylists.get(0);
        Stylist marco = stylists.get(stylists.size() > 1 ? 1 : 0);
        ServiceItem haircut = service(services, "Haircut");
        ServiceItem color = service(services, "Hair Color");
        ServiceItem spa = service(services, "Hair Spa");
        ServiceItem mani = service(services, "Manicure");
        ServiceItem pedi = service(services, "Pedicure");

        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        // 1. Finished and paid with GCash
        Appointment a1 = booking(maria, anna, haircut, now.minusDays(3), AppointmentStatus.COMPLETED);
        markPaid(a1, "GCASH", "demo_pay_0001", now.minusDays(3).plusMinutes(40));
        attempt(a1, "GCASH", "demo_pi_0001", OnlinePaymentStatus.PAID, "demo_pay_0001", now.minusDays(3).minusHours(2),
                now.minusDays(3).plusMinutes(40), null);

        // 2. Finished and paid by scanning a QR Ph code
        Appointment a2 = booking(juan, marco, color, now.minusDays(2), AppointmentStatus.COMPLETED);
        markPaid(a2, "QRPH", "demo_pay_0002", now.minusDays(2).plusHours(2));
        attempt(a2, "QRPH", "demo_pi_0002", OnlinePaymentStatus.PAID, "demo_pay_0002", now.minusDays(2).plusHours(1),
                now.minusDays(2).plusHours(2), null);

        // 3. Customer is paying right now (shows the "payment in progress" warning to staff)
        Appointment a3 = booking(maria, anna, mani, now.plusDays(1), AppointmentStatus.ACCEPTED);
        attempt(a3, "GCASH", "demo_pi_0003", OnlinePaymentStatus.PENDING, null, now.minusMinutes(5), null, null);

        // 4. QR code was never scanned and ran out
        Appointment a4 = booking(juan, marco, pedi, now.plusDays(2), AppointmentStatus.ACCEPTED);
        attempt(a4, "QRPH", "demo_pi_0004", OnlinePaymentStatus.EXPIRED, null, now.minusHours(3), null,
                "The payment window ran out");

        // 5. First attempt failed, second attempt (QR Ph) worked
        Appointment a5 = booking(maria, anna, spa, now.minusDays(1), AppointmentStatus.COMPLETED);
        attempt(a5, "GCASH", "demo_pi_0005a", OnlinePaymentStatus.FAILED, null, now.minusDays(1).minusHours(1), null,
                "Payment was declined");
        markPaid(a5, "QRPH", "demo_pay_0005", now.minusDays(1).plusHours(2));
        attempt(a5, "QRPH", "demo_pi_0005b", OnlinePaymentStatus.PAID, "demo_pay_0005", now.minusDays(1).plusHours(1),
                now.minusDays(1).plusHours(2), null);

        // 6. Customer paid after the booking was cancelled: the salon owes a refund
        Appointment a6 = booking(juan, anna, haircut, now.plusDays(3), AppointmentStatus.CANCELLED);
        attempt(a6, "QRPH", "demo_pi_0006", OnlinePaymentStatus.NEEDS_REFUND, "demo_pay_0006", now.minusHours(6),
                now.minusHours(5), null);

        // 7. Staff recorded cash while the customer was also paying online: the online payment needs a refund
        Appointment a7 = booking(maria, marco, haircut, now.minusHours(4), AppointmentStatus.COMPLETED);
        markPaid(a7, "CASH", null, now.minusHours(3));
        attempt(a7, "GCASH", "demo_pi_0007", OnlinePaymentStatus.NEEDS_REFUND, "demo_pay_0007", now.minusHours(4),
                now.minusHours(3), null);

        // 8. A refund that has already been handled
        Appointment a8 = booking(juan, marco, mani, now.minusDays(5), AppointmentStatus.COMPLETED);
        markPaid(a8, "CASH", null, now.minusDays(5).plusHours(1));
        attempt(a8, "QRPH", "demo_pi_0008", OnlinePaymentStatus.REFUNDED, "demo_pay_0008", now.minusDays(5),
                now.minusDays(5).plusMinutes(50), null);

        log.info("Demo online payments loaded: 8 bookings in the states PAID, PENDING, EXPIRED, FAILED, NEEDS_REFUND, REFUNDED.");
    }

    private static ServiceItem service(List<ServiceItem> all, String name) {
        return all.stream().filter(s -> name.equalsIgnoreCase(s.getName())).findFirst().orElse(all.get(0));
    }

    private Appointment booking(Customer customer, Stylist stylist, ServiceItem service, LocalDateTime start,
                                AppointmentStatus status) {
        return appointmentRepository.save(Appointment.builder()
                .customer(customer).stylist(stylist).service(service)
                .startTime(start).endTime(start.plusMinutes(service.getDurationMinutes()))
                .type(AppointmentType.SALON).status(status)
                .notes("Demo booking for online payments")
                .totalAmount(service.getPrice())
                .build());
    }

    private void markPaid(Appointment a, String method, String reference, LocalDateTime paidAt) {
        a.setPaymentStatus(PaymentStatus.PAID);
        a.setPaymentMethod(method);
        a.setPaymentReference(reference);
        a.setPaidAt(paidAt);
        appointmentRepository.save(a);
    }

    private void attempt(Appointment a, String method, String intentId, OnlinePaymentStatus status, String payId,
                         LocalDateTime startedAt, LocalDateTime paidAt, String failureReason) {
        long centavos = a.getTotalAmount().movePointRight(2).longValue();
        LocalDateTime expires = "QRPH".equals(method) ? startedAt.plusMinutes(30) : startedAt.plusHours(4);
        if (status == OnlinePaymentStatus.PENDING) {
            expires = LocalDateTime.now().plusMinutes(25);
        }
        paymentRepository.save(OnlinePayment.builder()
                .appointment(a).paymentIntentId(intentId).method(method).amountCentavos(centavos)
                .status(status).paymongoPaymentId(payId).expiresAt(expires).paidAt(paidAt)
                .lastCheckedAt(paidAt).failureReason(failureReason)
                .build());
    }
}
