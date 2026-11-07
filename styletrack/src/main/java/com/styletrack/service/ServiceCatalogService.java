package com.styletrack.service;

import com.styletrack.dto.ServiceDto;
import com.styletrack.entity.ServiceItem;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.ServiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCatalogService {

    private final ServiceItemRepository serviceRepository;

    public List<ServiceDto.Response> list(boolean activeOnly) {
        List<ServiceItem> items = activeOnly
                ? serviceRepository.findByActiveTrueOrderByCategoryAscNameAsc()
                : serviceRepository.findAllByOrderByCategoryAscNameAsc();
        return items.stream().map(ServiceDto.Response::from).toList();
    }

    public ServiceDto.Response get(Long id) {
        return ServiceDto.Response.from(find(id));
    }

    @Transactional
    public ServiceDto.Response create(ServiceDto.Request r) {
        if (serviceRepository.existsByNameIgnoreCase(r.getName().trim())) {
            throw new BusinessException("A service with this name already exists", HttpStatus.CONFLICT);
        }
        ServiceItem item = ServiceItem.builder().build();
        apply(item, r);
        return ServiceDto.Response.from(serviceRepository.save(item));
    }

    @Transactional
    public ServiceDto.Response update(Long id, ServiceDto.Request r) {
        ServiceItem item = find(id);
        apply(item, r);
        return ServiceDto.Response.from(item);
    }

    /** Services referenced by past appointments cannot be deleted, so they are deactivated. */
    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private ServiceItem find(Long id) {
        return serviceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }

    private void apply(ServiceItem item, ServiceDto.Request r) {
        item.setName(r.getName().trim());
        item.setDescription(r.getDescription());
        item.setCategory(r.getCategory());
        item.setPrice(r.getPrice());
        item.setDurationMinutes(r.getDurationMinutes());
        item.setHomeServiceAvailable(r.isHomeServiceAvailable());
        item.setPointsAwarded(r.getPointsAwarded());
        item.setActive(r.getActive() == null || r.getActive());
    }
}
