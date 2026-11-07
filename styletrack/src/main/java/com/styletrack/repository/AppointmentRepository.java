package com.styletrack.repository;

import com.styletrack.entity.Appointment;
import com.styletrack.entity.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Aggregate queries use {@code stylistId = 0} to mean "all stylists" so a null parameter
 * never has to be sent to PostgreSQL.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {"customer", "stylist", "service"})
    Page<Appointment> findAll(Specification<Appointment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "stylist", "service"})
    Page<Appointment> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "stylist", "service"})
    List<Appointment> findByStylistIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            Long stylistId, Collection<AppointmentStatus> statuses, LocalDateTime from, LocalDateTime to);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.stylist.id = :stylistId and a.status in :statuses
              and a.startTime < :end and a.endTime > :start and a.id <> :excludeId
            """)
    boolean existsStylistOverlap(@Param("stylistId") Long stylistId,
                                 @Param("statuses") Collection<AppointmentStatus> statuses,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 @Param("excludeId") Long excludeId);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.customer.id = :customerId and a.status in :statuses
              and a.startTime < :end and a.endTime > :start and a.id <> :excludeId
            """)
    boolean existsCustomerOverlap(@Param("customerId") Long customerId,
                                  @Param("statuses") Collection<AppointmentStatus> statuses,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("excludeId") Long excludeId);

    @Query("""
            select a.status, count(a) from Appointment a
            where a.startTime >= :from and a.startTime < :to
              and (:stylistId = 0L or a.stylist.id = :stylistId)
            group by a.status
            """)
    List<Object[]> countByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("stylistId") Long stylistId);

    @Query("""
            select count(a) from Appointment a
            where a.type = com.styletrack.entity.enums.AppointmentType.HOME_SERVICE
              and a.startTime >= :from and a.startTime < :to
              and (:stylistId = 0L or a.stylist.id = :stylistId)
            """)
    long countHomeService(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                          @Param("stylistId") Long stylistId);

    @Query("""
            select coalesce(sum(a.totalAmount), 0) from Appointment a
            where a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
              and a.startTime >= :from and a.startTime < :to
              and (:stylistId = 0L or a.stylist.id = :stylistId)
            """)
    Number sumCompletedRevenue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                               @Param("stylistId") Long stylistId);

    @Query("""
            select cast(a.startTime as date), coalesce(sum(a.totalAmount), 0), count(a)
            from Appointment a
            where a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
              and a.startTime >= :from and a.startTime < :to
            group by cast(a.startTime as date)
            order by cast(a.startTime as date)
            """)
    List<Object[]> revenueByDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select a.service.id, a.service.name, count(a), coalesce(sum(a.totalAmount), 0)
            from Appointment a
            where a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
              and a.startTime >= :from and a.startTime < :to
            group by a.service.id, a.service.name
            order by count(a) desc
            """)
    List<Object[]> topServices(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @Query("""
            select a.stylist.id, a.stylist.fullName, count(a), coalesce(sum(a.totalAmount), 0)
            from Appointment a
            where a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
              and a.startTime >= :from and a.startTime < :to
            group by a.stylist.id, a.stylist.fullName
            order by coalesce(sum(a.totalAmount), 0) desc
            """)
    List<Object[]> stylistPerformance(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            select a.service.id, count(a) from Appointment a
            where a.customer.id = :customerId
              and a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
            group by a.service.id
            order by count(a) desc
            """)
    List<Object[]> serviceFrequencyForCustomer(@Param("customerId") Long customerId);

    @Query("""
            select a.service.id, count(a) from Appointment a
            where a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
            group by a.service.id
            order by count(a) desc
            """)
    List<Object[]> popularServices(Pageable pageable);

    @Query("""
            select a.paymentMethod, count(a), coalesce(sum(a.totalAmount), 0) from Appointment a
            where a.paymentStatus = com.styletrack.entity.enums.PaymentStatus.PAID
              and a.paidAt >= :from and a.paidAt < :to
            group by a.paymentMethod
            order by coalesce(sum(a.totalAmount), 0) desc
            """)
    List<Object[]> paymentsByMethod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Completed services that have not been paid for yet: [count, total]. */
    @Query("""
            select count(a), coalesce(sum(a.totalAmount), 0) from Appointment a
            where a.status = com.styletrack.entity.enums.AppointmentStatus.COMPLETED
              and a.paymentStatus = com.styletrack.entity.enums.PaymentStatus.UNPAID
            """)
    List<Object[]> outstanding();

    boolean existsByPaymentMethodAndPaymentReferenceAndIdNot(String paymentMethod, String paymentReference, Long id);
}
