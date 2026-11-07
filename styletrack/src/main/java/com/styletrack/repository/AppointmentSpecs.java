package com.styletrack.repository;

import com.styletrack.entity.Appointment;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class AppointmentSpecs {

    public Specification<Appointment> filter(Long stylistId, Long customerId, AppointmentStatus status,
                                             AppointmentType type, LocalDateTime from, LocalDateTime toExclusive) {
        return filter(stylistId, customerId, status, type, null, from, toExclusive);
    }

    public Specification<Appointment> filter(Long stylistId, Long customerId, AppointmentStatus status,
                                             AppointmentType type, PaymentStatus paymentStatus,
                                             LocalDateTime from, LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stylistId != null) {
                predicates.add(cb.equal(root.get("stylist").get("id"), stylistId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (paymentStatus != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("startTime"), toExclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
