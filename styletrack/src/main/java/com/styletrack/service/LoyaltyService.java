package com.styletrack.service;

import com.styletrack.config.AppProperties;
import com.styletrack.dto.LoyaltyDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.LoyaltyTransaction;
import com.styletrack.entity.enums.LoyaltyType;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.LoyaltyTransactionRepository;
import com.styletrack.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyService {

    private final CustomerRepository customerRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final RewardRepository rewardRepository;
    private final AppProperties appProperties;

    public LoyaltyDto.Balance balance(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        return LoyaltyDto.Balance.builder()
                .customerId(customer.getId())
                .customerName(customer.getFullName())
                .balance(customer.getLoyaltyPoints())
                .completedVisits(customer.getCompletedVisits())
                .tier(tierFor(customer.getCompletedVisits()))
                .visitsToNextTier(visitsToNextTier(customer.getCompletedVisits()))
                .build();
    }

    /** Repeat-patronage tier from completed visits: REGULAR, SILVER or GOLD. */
    public String tierFor(int completedVisits) {
        AppProperties.Loyalty cfg = appProperties.getLoyalty();
        if (completedVisits >= cfg.getGoldVisits()) {
            return "GOLD";
        }
        return completedVisits >= cfg.getSilverVisits() ? "SILVER" : "REGULAR";
    }

    private int visitsToNextTier(int completedVisits) {
        AppProperties.Loyalty cfg = appProperties.getLoyalty();
        if (completedVisits >= cfg.getGoldVisits()) {
            return 0;
        }
        return (completedVisits >= cfg.getSilverVisits() ? cfg.getGoldVisits() : cfg.getSilverVisits())
                - completedVisits;
    }

    public PageResponse<LoyaltyDto.TransactionResponse> history(Long customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
        return PageResponse.of(transactionRepository.findByCustomerId(customerId, pageable)
                .map(LoyaltyDto.TransactionResponse::from));
    }

    @Transactional
    public LoyaltyDto.TransactionResponse redeem(Long customerId, LoyaltyDto.RedeemRequest r) {
        String description = r.getDescription() == null || r.getDescription().isBlank()
                ? "Points redeemed" : r.getDescription();
        return LoyaltyDto.TransactionResponse.from(
                record(customerId, null, -r.getPoints(), LoyaltyType.REDEEMED, description));
    }

    /** Redeems points against a catalogue reward at the reward's point cost. */
    @Transactional
    public LoyaltyDto.TransactionResponse redeemReward(Long customerId, LoyaltyDto.RedeemRewardRequest r) {
        var reward = rewardRepository.findById(r.getRewardId())
                .orElseThrow(() -> new ResourceNotFoundException("Reward", r.getRewardId()));
        if (!reward.isActive()) {
            throw new BusinessException("This reward is no longer available");
        }
        return LoyaltyDto.TransactionResponse.from(record(customerId, null, -reward.getPointsCost(),
                LoyaltyType.REDEEMED, "Reward: " + reward.getName()));
    }

    /** Counts a completed service toward the customer's tier (row-locked so concurrent completions add up). */
    @Transactional
    public void registerCompletedVisit(Long customerId) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        customer.setCompletedVisits(customer.getCompletedVisits() + 1);
    }

    @Transactional
    public LoyaltyDto.TransactionResponse adjust(Long customerId, LoyaltyDto.AdjustRequest r) {
        if (r.getPoints() == 0) {
            throw new BusinessException("Adjustment must not be zero");
        }
        return LoyaltyDto.TransactionResponse.from(
                record(customerId, null, r.getPoints(), LoyaltyType.ADJUSTED, r.getDescription()));
    }

    /**
     * Awards points for a completed appointment: the service's fixed points when set,
     * otherwise one point per configured amount of pesos spent.
     */
    @Transactional
    public int earnForAppointment(Appointment appointment) {
        Integer fixed = appointment.getService().getPointsAwarded();
        int points;
        if (fixed != null) {
            points = fixed;
        } else {
            int pesosPerPoint = Math.max(1, appProperties.getLoyalty().getPesosPerPoint());
            points = appointment.getTotalAmount()
                    .divide(BigDecimal.valueOf(pesosPerPoint), 0, RoundingMode.DOWN).intValue();
        }
        if (points > 0) {
            record(appointment.getCustomer().getId(), appointment, points, LoyaltyType.EARNED,
                    "Completed: " + appointment.getService().getName());
        }
        return points;
    }

    private LoyaltyTransaction record(Long customerId, Appointment appointment, int points,
                                      LoyaltyType type, String description) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        int newBalance = customer.getLoyaltyPoints() + points;
        if (newBalance < 0) {
            throw new BusinessException("Insufficient loyalty points (balance: " + customer.getLoyaltyPoints() + ")");
        }
        customer.setLoyaltyPoints(newBalance);
        LoyaltyTransaction tx = transactionRepository.save(LoyaltyTransaction.builder()
                .customer(customer)
                .appointment(appointment)
                .points(points)
                .balanceAfter(newBalance)
                .type(type)
                .description(description)
                .build());
        log.info("Loyalty {} {} pts for customer {} (balance {})", type, points, customerId, newBalance);
        return tx;
    }
}
