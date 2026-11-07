package com.styletrack.controller;

import com.styletrack.dto.PageResponse;
import com.styletrack.dto.TreatmentDto;
import com.styletrack.service.TreatmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Treatment history")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TreatmentController {

    private final TreatmentService treatmentService;

    @GetMapping("/customers/{customerId}/treatments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public PageResponse<TreatmentDto.Response> list(
            @PathVariable Long customerId,
            @PageableDefault(size = 20, sort = "treatmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return treatmentService.listForCustomer(customerId, pageable);
    }

    @PostMapping("/treatments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','STYLIST')")
    public TreatmentDto.Response create(@Valid @RequestBody TreatmentDto.Request request) {
        return treatmentService.create(request);
    }

    @PutMapping("/treatments/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','STYLIST')")
    public TreatmentDto.Response update(@PathVariable Long id, @Valid @RequestBody TreatmentDto.Request request) {
        return treatmentService.update(id, request);
    }

    @DeleteMapping("/treatments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable Long id) {
        treatmentService.delete(id);
    }
}
