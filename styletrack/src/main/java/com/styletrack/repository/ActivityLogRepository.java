package com.styletrack.repository;

import com.styletrack.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long>, JpaSpecificationExecutor<ActivityLog> {
}
