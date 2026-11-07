package com.styletrack.repository;

import com.styletrack.entity.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardRepository extends JpaRepository<Reward, Long> {

    List<Reward> findAllByOrderByPointsCostAsc();

    List<Reward> findByActiveTrueOrderByPointsCostAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
