package com.styletrack.repository;

import com.styletrack.entity.Recommendation;
import com.styletrack.entity.enums.RecommendationSource;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    @EntityGraph(attributePaths = {"customer", "service", "stylist"})
    List<Recommendation> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "service", "stylist"})
    List<Recommendation> findByCustomerIdAndActiveTrueOrderByCreatedAtDesc(Long customerId);

    void deleteByCustomerIdAndSource(Long customerId, RecommendationSource source);
}
