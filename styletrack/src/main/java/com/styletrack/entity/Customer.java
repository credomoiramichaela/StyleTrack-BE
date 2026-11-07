package com.styletrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "customers")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Customer extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 500)
    private String address;

    private LocalDate birthDate;

    @Column(length = 1000)
    private String notes;

    /** Mobile-app login for this customer, when they have one. */
    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private int loyaltyPoints = 0;

    /** Number of completed services; drives repeat-patronage recognition (loyalty tiers). */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int completedVisits = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
