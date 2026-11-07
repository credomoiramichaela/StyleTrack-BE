package com.styletrack.repository;

import com.styletrack.entity.LoyaltyTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    Page<LoyaltyTransaction> findByCustomerId(Long customerId, Pageable pageable);

    @Query("""
            select coalesce(sum(t.points), 0) from LoyaltyTransaction t
            where t.type = com.styletrack.entity.enums.LoyaltyType.EARNED
              and t.createdAt >= :from and t.createdAt < :to
            """)
    Number sumEarned(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
