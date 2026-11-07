package com.styletrack.repository;

import com.styletrack.entity.Stylist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StylistRepository extends JpaRepository<Stylist, Long> {

    List<Stylist> findByActiveTrueOrderByFullNameAsc();

    List<Stylist> findAllByOrderByFullNameAsc();

    Optional<Stylist> findByUserId(Long userId);

    long countByActiveTrue();

    /** Row lock used while booking so two requests cannot grab the same slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stylist s where s.id = :id")
    Optional<Stylist> findByIdForUpdate(@Param("id") Long id);
}
