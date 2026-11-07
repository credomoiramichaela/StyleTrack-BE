package com.styletrack.controller;

import com.styletrack.dto.StylistDto;
import com.styletrack.service.StylistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Stylists")
@RestController
@RequestMapping("/api/stylists")
@RequiredArgsConstructor
public class StylistController {

    private final StylistService stylistService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public List<StylistDto.Response> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return stylistService.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public StylistDto.Response get(@PathVariable Long id) {
        return stylistService.get(id);
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")

    public StylistDto.Availability availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Long serviceId,
            @RequestParam(defaultValue = "false") boolean homeService) {
        return stylistService.availability(id, date, serviceId, homeService);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public StylistDto.Response create(@Valid @RequestBody StylistDto.Request request) {
        return stylistService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public StylistDto.Response update(@PathVariable Long id, @Valid @RequestBody StylistDto.Request request) {
        return stylistService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deactivate(@PathVariable Long id) {
        stylistService.deactivate(id);
    }
}
