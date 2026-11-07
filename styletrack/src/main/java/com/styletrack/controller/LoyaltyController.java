package com.styletrack.controller;

import com.styletrack.dto.LoyaltyDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.service.LoyaltyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Loyalty points")
@RestController
@RequestMapping("/api/loyalty/customers/{customerId}")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','STYLIST')")
    public LoyaltyDto.Balance balance(@PathVariable Long customerId) {
        return loyaltyService.balance(customerId);
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public PageResponse<LoyaltyDto.TransactionResponse> history(
            @PathVariable Long customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return loyaltyService.history(customerId, pageable);
    }

    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public LoyaltyDto.TransactionResponse redeem(@PathVariable Long customerId,
                                                 @Valid @RequestBody LoyaltyDto.RedeemRequest request) {
        return loyaltyService.redeem(customerId, request);
    }

    @PostMapping("/redeem-reward")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public LoyaltyDto.TransactionResponse redeemReward(@PathVariable Long customerId,
                                                       @Valid @RequestBody LoyaltyDto.RedeemRewardRequest request) {
        return loyaltyService.redeemReward(customerId, request);
    }

    @PostMapping("/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public LoyaltyDto.TransactionResponse adjust(@PathVariable Long customerId,
                                                 @Valid @RequestBody LoyaltyDto.AdjustRequest request) {
        return loyaltyService.adjust(customerId, request);
    }
}
