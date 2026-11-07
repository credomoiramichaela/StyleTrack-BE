package com.styletrack.service;

import com.styletrack.dto.ReportDto;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.LoyaltyTransactionRepository;
import com.styletrack.util.NumberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Report ranges are inclusive of both dates. Defaults to the current month. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final long ALL_STYLISTS = 0L;

    private final AppointmentRepository appointmentRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository loyaltyRepository;
    private final LoyaltyService loyaltyService;

    public ReportDto.Summary summary(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = end.plusDays(1).atStartOfDay();

        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            counts.put(status, 0L);
        }
        for (Object[] row : appointmentRepository.countByStatus(s, e, ALL_STYLISTS)) {
            counts.put((AppointmentStatus) row[0], NumberUtil.toLong(row[1]));
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long completed = counts.get(AppointmentStatus.COMPLETED);
        BigDecimal revenue = NumberUtil.toBigDecimal(appointmentRepository.sumCompletedRevenue(s, e, ALL_STYLISTS));
        BigDecimal average = completed == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);

        return ReportDto.Summary.builder()
                .from(start)
                .to(end)
                .totalAppointments(total)
                .appointmentsByStatus(counts.entrySet().stream()
                        .collect(Collectors.toMap(en -> en.getKey().name(), Map.Entry::getValue,
                                (a, b) -> a, java.util.LinkedHashMap::new)))
                .homeServiceBookings(appointmentRepository.countHomeService(s, e, ALL_STYLISTS))
                .completedAppointments(completed)
                .revenue(revenue)
                .averageTicket(average)
                .newCustomers(customerRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(s, e))
                .loyaltyPointsIssued(NumberUtil.toLong(loyaltyRepository.sumEarned(s, e)))
                .build();
    }

    public List<ReportDto.DailyRevenue> dailyRevenue(LocalDate from, LocalDate to) {
        LocalDateTime s = (from == null ? LocalDate.now().withDayOfMonth(1) : from).atStartOfDay();
        LocalDateTime e = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        return appointmentRepository.revenueByDay(s, e).stream()
                .map(row -> ReportDto.DailyRevenue.builder()
                        .date(LocalDate.parse(String.valueOf(row[0])))
                        .revenue(NumberUtil.toBigDecimal(row[1]))
                        .completedAppointments(NumberUtil.toLong(row[2]))
                        .build())
                .toList();
    }

    public List<ReportDto.ServiceStat> topServices(LocalDate from, LocalDate to, int limit) {
        LocalDateTime s = (from == null ? LocalDate.now().withDayOfMonth(1) : from).atStartOfDay();
        LocalDateTime e = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        return appointmentRepository.topServices(s, e, PageRequest.of(0, Math.min(Math.max(limit, 1), 50))).stream()
                .map(row -> ReportDto.ServiceStat.builder()
                        .serviceId(NumberUtil.toLong(row[0]))
                        .serviceName((String) row[1])
                        .completedCount(NumberUtil.toLong(row[2]))
                        .revenue(NumberUtil.toBigDecimal(row[3]))
                        .build())
                .toList();
    }

    public List<ReportDto.StylistStat> stylistPerformance(LocalDate from, LocalDate to) {
        LocalDateTime s = (from == null ? LocalDate.now().withDayOfMonth(1) : from).atStartOfDay();
        LocalDateTime e = (to == null ? LocalDate.now() : to).plusDays(1).atStartOfDay();
        return appointmentRepository.stylistPerformance(s, e).stream()
                .map(row -> ReportDto.StylistStat.builder()
                        .stylistId(NumberUtil.toLong(row[0]))
                        .stylistName((String) row[1])
                        .completedCount(NumberUtil.toLong(row[2]))
                        .revenue(NumberUtil.toBigDecimal(row[3]))
                        .build())
                .toList();
    }

    /** Payments collected in the range (by the time they were recorded) plus everything still unpaid. */
    public ReportDto.PaymentSummary payments(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<ReportDto.MethodStat> methods = appointmentRepository
                .paymentsByMethod(start.atStartOfDay(), end.plusDays(1).atStartOfDay()).stream()
                .map(row -> ReportDto.MethodStat.builder()
                        .method(row[0] == null ? "UNKNOWN" : (String) row[0])
                        .count(NumberUtil.toLong(row[1]))
                        .amount(NumberUtil.toBigDecimal(row[2]))
                        .build())
                .toList();
        Object[] outstanding = appointmentRepository.outstanding().get(0);
        return ReportDto.PaymentSummary.builder()
                .from(start)
                .to(end)
                .totalCollected(methods.stream().map(ReportDto.MethodStat::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .paidCount(methods.stream().mapToLong(ReportDto.MethodStat::getCount).sum())
                .byMethod(methods)
                .outstandingCount(NumberUtil.toLong(outstanding[0]))
                .outstandingAmount(NumberUtil.toBigDecimal(outstanding[1]))
                .build();
    }

    /** Most loyal customers: the ones with the most completed visits. */
    public List<ReportDto.TopCustomer> topCustomers(int limit) {
        return customerRepository
                .findByActiveTrueAndCompletedVisitsGreaterThanOrderByCompletedVisitsDescLoyaltyPointsDesc(
                        0, PageRequest.of(0, Math.min(Math.max(limit, 1), 50)))
                .stream()
                .map(c -> ReportDto.TopCustomer.builder()
                        .customerId(c.getId())
                        .fullName(c.getFullName())
                        .phone(c.getPhone())
                        .completedVisits(c.getCompletedVisits())
                        .loyaltyPoints(c.getLoyaltyPoints())
                        .tier(loyaltyService.tierFor(c.getCompletedVisits()))
                        .build())
                .toList();
    }
}
