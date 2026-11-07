package com.styletrack.controller;

import com.styletrack.dto.ServiceDto;
import com.styletrack.service.ServiceCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Services")
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    public List<ServiceDto.Response> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return serviceCatalogService.list(activeOnly);
    }

    @GetMapping("/{id}")
    public ServiceDto.Response get(@PathVariable Long id) {
        return serviceCatalogService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ServiceDto.Response create(@Valid @RequestBody ServiceDto.Request request) {
        return serviceCatalogService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ServiceDto.Response update(@PathVariable Long id, @Valid @RequestBody ServiceDto.Request request) {
        return serviceCatalogService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deactivate(@PathVariable Long id) {
        serviceCatalogService.deactivate(id);
    }
}
