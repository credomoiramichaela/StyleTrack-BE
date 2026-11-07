package com.styletrack.repository;

import com.styletrack.entity.ActivityLog;
import jakarta.persistence.criteria.Predicate;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class ActivityLogSpecs {

    public Specification<ActivityLog> filter(ActivityLog.Action action, Long actorUserId,
                                              LocalDateTime from, LocalDateTime toExclusive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
