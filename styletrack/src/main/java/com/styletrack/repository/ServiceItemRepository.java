package com.styletrack.repository;

import com.styletrack.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    List<ServiceItem> findByActiveTrueOrderByCategoryAscNameAsc();

    List<ServiceItem> findAllByOrderByCategoryAscNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
