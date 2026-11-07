package com.styletrack.service;

import com.styletrack.dto.ActivityLogDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.ActivityLog;
import com.styletrack.entity.User;
import com.styletrack.repository.ActivityLogRepository;
import com.styletrack.repository.ActivityLogSpecs;
import com.styletrack.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Append-only audit trail for Log Reports (Super Admin only). Never blocks the action it records. */
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository repository;
    private final CurrentUser currentUser;

    /** Records an action by the currently authenticated user. */
    @Transactional
    public void record(ActivityLog.Action action, String targetType, Long targetId, String details) {
        User me = currentUser.get();
        record(me.getId(), me.getFullName(), action, targetType, targetId, details);
    }

    /** Records an action with an explicit actor, for cases (e.g. a failed login) with no authenticated user. */
    @Transactional
    public void record(Long actorUserId, String actorName, ActivityLog.Action action,
                        String targetType, Long targetId, String details) {
        repository.save(ActivityLog.builder()
                .actorUserId(actorUserId)
                .actorName(actorName)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityLogDto.Response> search(ActivityLog.Action action, Long actorUserId,
                                                         LocalDateTime from, LocalDateTime toExclusive,
                                                         Pageable pageable) {
        var spec = ActivityLogSpecs.filter(action, actorUserId, from, toExclusive);
        return PageResponse.of(repository.findAll(spec, pageable).map(ActivityLogDto.Response::from));
    }
}
