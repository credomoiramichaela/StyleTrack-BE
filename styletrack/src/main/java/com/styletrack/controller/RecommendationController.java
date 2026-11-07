package com.styletrack.controller;

import com.styletrack.dto.RecommendationDto;
import com.styletrack.service.RecommendationService;
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

@Tag(name = "Personalized recommendations")
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public List<RecommendationDto.Response> listForCustomer(@PathVariable Long customerId,
                                                            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return recommendationService.listForCustomer(customerId, activeOnly);
    }

    /** Preview of data-driven suggestions based on the customer's history. Nothing is saved. */
    @GetMapping("/customer/{customerId}/suggestions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public List<RecommendationDto.Suggestion> suggestions(@PathVariable Long customerId) {
        return recommendationService.suggest(customerId);
    }

    /** Generates and saves fresh automatic recommendations, replacing the previous automatic ones. */
    @PostMapping("/customer/{customerId}/generate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public List<RecommendationDto.Response> generate(@PathVariable Long customerId) {
        return recommendationService.generate(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public RecommendationDto.Response create(@Valid @RequestBody RecommendationDto.Request request) {
        return recommendationService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public RecommendationDto.Response update(@PathVariable Long id,
                                             @Valid @RequestBody RecommendationDto.Request request) {
        return recommendationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public void delete(@PathVariable Long id) {
        recommendationService.delete(id);
    }
}
