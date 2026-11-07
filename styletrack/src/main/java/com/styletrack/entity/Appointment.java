package com.styletrack.entity;

import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.AppointmentType;
import com.styletrack.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointment_stylist_start", columnList = "stylist_id,start_time"),
        @Index(name = "idx_appointment_customer", columnList = "customer_id"),
        @Index(name = "idx_appointment_status", columnList = "status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Appointment extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stylist_id", nullable = false)
    private Stylist stylist;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceItem service;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    /** Customer address for home-service bookings. */
    @Column(length = 500)
    private String address;

    @Column(length = 1000)
    private String notes;

    @Column(length = 500)
    private String rejectionReason;

    /** An alternative stylist suggested to the customer when this booking is rejected. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_stylist_id")
    private Stylist suggestedStylist;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal homeServiceFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** e.g. CASH, GCASH. */
    @Column(length = 30)
    private String paymentMethod;

    @Column(length = 100)
    private String paymentReference;

    private LocalDateTime paidAt;

    @Builder.Default
    @Column(nullable = false)
    private int pointsEarned = 0;
}
