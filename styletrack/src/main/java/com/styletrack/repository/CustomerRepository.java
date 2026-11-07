package com.styletrack.repository;

import com.styletrack.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("""
            select c from Customer c
            where lower(c.fullName) like lower(concat('%', :q, '%'))
               or c.phone like concat('%', :q, '%')
               or lower(coalesce(c.email, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Customer> search(@Param("q") String q, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    long countByActiveTrue();

    Optional<Customer> findByUserId(Long userId);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndUserIsNotNull(String phone);

    Page<Customer> findByActiveTrueAndCompletedVisitsGreaterThanOrderByCompletedVisitsDescLoyaltyPointsDesc(
            int minVisits, Pageable pageable);
}
