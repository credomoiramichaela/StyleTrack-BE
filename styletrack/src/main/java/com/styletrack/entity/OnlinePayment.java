package com.styletrack.entity;

import com.styletrack.entity.enums.OnlinePaymentStatus;
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

import java.time.LocalDateTime;

/** One attempt to pay a booking online through PayMongo (a PayMongo "payment intent"). */
@Entity
@Table(name = "online_payments", indexes = {
        @Index(name = "idx_online_payment_appointment", columnList = "appointment_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OnlinePayment extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    /** PayMongo payment intent id (pi_...). */
    @Column(nullable = false, unique = true, length = 100)
    private String paymentIntentId;

    /** GCASH or QRPH. */
    @Column(nullable = false, length = 10)
    private String method;

    /** Amount in centavos, as sent to PayMongo. */
    @Column(nullable = false)
    private long amountCentavos;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OnlinePaymentStatus status = OnlinePaymentStatus.PENDING;

    /** PayMongo payment id (pay_...), known once it is paid. */
    @Column(length = 100)
    private String paymongoPaymentId;

    private LocalDateTime expiresAt;

    private LocalDateTime paidAt;

    /** When we last asked PayMongo about this payment; keeps app polling from hammering their API. */
    private LocalDateTime lastCheckedAt;

    @Column(length = 255)
    private String failureReason;
}
