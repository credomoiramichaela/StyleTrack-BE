package com.styletrack.repository;

import com.styletrack.entity.OnlinePayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.styletrack.entity.enums.OnlinePaymentStatus;

import java.util.List;
import java.util.Optional;

public interface OnlinePaymentRepository extends JpaRepository<OnlinePayment, Long> {

    Optional<OnlinePayment> findFirstByAppointmentIdOrderByIdDesc(Long appointmentId);

    List<OnlinePayment> findTop100ByOrderByIdDesc();

    List<OnlinePayment> findTop100ByStatusOrderByIdDesc(OnlinePaymentStatus status);

    List<OnlinePayment> findByAppointmentIdOrderByIdDesc(Long appointmentId);

    Optional<OnlinePayment> findByPaymentIntentId(String paymentIntentId);

    /** Locks the row so the webhook and the app's status check can't both mark it paid. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from OnlinePayment p where p.paymentIntentId = :intentId")
    Optional<OnlinePayment> lockByPaymentIntentId(@Param("intentId") String intentId);
}
