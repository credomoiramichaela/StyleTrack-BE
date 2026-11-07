package com.styletrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * A detailed record of what was done for a customer during a service: used by stylists to review
 * the customer before a session and to drive personalised follow-up recommendations.
 */
@Entity
@Table(name = "treatment_records", indexes = {
        @Index(name = "idx_treatment_customer_date", columnList = "customer_id,treatment_date")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class TreatmentRecord extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** The booking this record belongs to, when it was written from an appointment. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stylist_id", nullable = false)
    private Stylist stylist;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceItem service;

    @Column(nullable = false)
    private LocalDate treatmentDate;

    /** What was done: technique, results, hair/scalp condition. */
    @Column(nullable = false, length = 2000)
    private String details;

    /** Products, colour formula or shade used. */
    @Column(length = 500)
    private String productsUsed;

    /** Allergies, sensitivities or reactions noticed. */
    @Column(length = 500)
    private String reactions;

    @Column(length = 1000)
    private String followUpAdvice;

    /** Service the stylist suggests for the customer's next visit. */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follow_up_service_id")
    private ServiceItem followUpService;

    /** Suggested number of days until the follow-up service is due. */
    private Integer followUpAfterDays;
}
