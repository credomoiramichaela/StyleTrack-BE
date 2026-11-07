package com.styletrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "salon_services")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ServiceItem extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int durationMinutes;

    @Builder.Default
    @Column(nullable = false)
    private boolean homeServiceAvailable = false;

    /** Fixed loyalty points for this service. When null, points are derived from the price. */
    private Integer pointsAwarded;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
