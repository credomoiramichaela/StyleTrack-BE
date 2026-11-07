package com.styletrack.repository;

import com.styletrack.entity.TreatmentRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TreatmentRecordRepository extends JpaRepository<TreatmentRecord, Long> {

    @EntityGraph(attributePaths = {"customer", "stylist", "service", "followUpService", "appointment"})
    Page<TreatmentRecord> findByCustomerId(Long customerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer", "stylist", "service", "followUpService", "appointment"})
    java.util.Optional<TreatmentRecord> findById(Long id);

    boolean existsByAppointmentId(Long appointmentId);

    @EntityGraph(attributePaths = {"customer", "stylist", "service", "followUpService"})
    List<TreatmentRecord> findByCustomerIdAndFollowUpServiceIsNotNullOrderByTreatmentDateDesc(Long customerId);
}
