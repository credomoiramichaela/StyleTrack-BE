package com.styletrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/** An append-only security/admin audit trail, visible only to Super Admin via the Log Reports page. */
@Entity
@Table(name = "activity_logs", indexes = {
        @Index(name = "idx_activity_log_created_at", columnList = "created_at"),
        @Index(name = "idx_activity_log_actor", columnList = "actor_user_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ActivityLog extends BaseEntity {

    public enum Action {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        USER_CREATED,
        USER_UPDATED,
        USER_DEACTIVATED,
        ROLE_CHANGED,
        APPOINTMENT_ACCEPTED,
        APPOINTMENT_REJECTED,
        APPOINTMENT_CANCELLED
    }

    /** Null when the actor could not be identified (e.g. a failed login with an unknown username). */
    private Long actorUserId;

    @Column(length = 150)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Action action;

    @Column(length = 60)
    private String targetType;

    private Long targetId;

    @Column(length = 1000)
    private String details;
}
