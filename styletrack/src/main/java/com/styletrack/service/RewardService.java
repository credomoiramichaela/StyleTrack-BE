package com.styletrack.service;

import com.styletrack.dto.RewardDto;
import com.styletrack.entity.Reward;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Catalogue of things customers can redeem loyalty points for. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    private final RewardRepository rewardRepository;

    public List<RewardDto.Response> list(boolean activeOnly) {
        List<Reward> rewards = activeOnly
                ? rewardRepository.findByActiveTrueOrderByPointsCostAsc()
                : rewardRepository.findAllByOrderByPointsCostAsc();
        return rewards.stream().map(RewardDto.Response::from).toList();
    }

    @Transactional
    public RewardDto.Response create(RewardDto.Request r) {
        if (rewardRepository.existsByNameIgnoreCase(r.getName().trim())) {
            throw new BusinessException("A reward with this name already exists", HttpStatus.CONFLICT);
        }
        Reward reward = Reward.builder().build();
        apply(reward, r);
        return RewardDto.Response.from(rewardRepository.save(reward));
    }

    @Transactional
    public RewardDto.Response update(Long id, RewardDto.Request r) {
        Reward reward = find(id);
        if (rewardRepository.existsByNameIgnoreCaseAndIdNot(r.getName().trim(), id)) {
            throw new BusinessException("A reward with this name already exists", HttpStatus.CONFLICT);
        }
        apply(reward, r);
        return RewardDto.Response.from(reward);
    }

    /** Rewards are deactivated rather than deleted so past redemptions keep making sense. */
    @Transactional
    public void deactivate(Long id) {
        find(id).setActive(false);
    }

    private Reward find(Long id) {
        return rewardRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reward", id));
    }

    private void apply(Reward reward, RewardDto.Request r) {
        reward.setName(r.getName().trim());
        reward.setDescription(r.getDescription());
        reward.setPointsCost(r.getPointsCost());
        reward.setActive(r.getActive() == null || r.getActive());
    }
}
