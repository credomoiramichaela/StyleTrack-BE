package com.styletrack.controller;

import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.CustomerDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.service.CustomerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customers")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public PageResponse<CustomerDto.Response> search(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return customerService.search(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public CustomerDto.Response get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public PageResponse<AppointmentDto.Response> history(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.history(id, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public CustomerDto.Response create(@Valid @RequestBody CustomerDto.Request request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public CustomerDto.Response update(@PathVariable Long id, @Valid @RequestBody CustomerDto.Request request) {
        return customerService.update(id, request);
    }

    @PostMapping("/{id}/app-account")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public CustomerDto.AppAccountResponse createAppAccount(@PathVariable Long id,
                                                           @Valid @RequestBody CustomerDto.AppAccountRequest request) {
        return customerService.createAppAccount(id, request);
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public CustomerDto.Response reactivate(@PathVariable Long id) {
        return customerService.reactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deactivate(@PathVariable Long id) {
        customerService.deactivate(id);
    }
}
