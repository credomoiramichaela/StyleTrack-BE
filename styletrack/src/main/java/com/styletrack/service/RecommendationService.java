package com.styletrack.service;

import com.styletrack.dto.RecommendationDto;
import com.styletrack.entity.Customer;
import com.styletrack.entity.Recommendation;
import com.styletrack.entity.ServiceItem;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.TreatmentRecord;
import com.styletrack.entity.enums.RecommendationSource;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.RecommendationRepository;
import com.styletrack.repository.ServiceItemRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.TreatmentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int MAX_REPEAT_SUGGESTIONS = 3;
    private static final int MAX_DISCOVERY_SUGGESTIONS = 2;
    private static final int MAX_FOLLOW_UP_SUGGESTIONS = 3;
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final RecommendationRepository recommendationRepository;
    private final CustomerRepository customerRepository;
    private final ServiceItemRepository serviceRepository;
    private final StylistRepository stylistRepository;
    private final AppointmentRepository appointmentRepository;
    private final TreatmentRecordRepository treatmentRepository;

    public List<RecommendationDto.Response> listForCustomer(Long customerId, boolean activeOnly) {
        requireCustomer(customerId);
        var list = activeOnly
                ? recommendationRepository.findByCustomerIdAndActiveTrueOrderByCreatedAtDesc(customerId)
                : recommendationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return list.stream().map(RecommendationDto.Response::from).toList();
    }

    @Transactional
    public RecommendationDto.Response create(RecommendationDto.Request r) {
        Recommendation recommendation = Recommendation.builder().source(RecommendationSource.MANUAL).build();
        apply(recommendation, r);
        return RecommendationDto.Response.from(recommendationRepository.save(recommendation));
    }

    @Transactional
    public RecommendationDto.Response update(Long id, RecommendationDto.Request r) {
        Recommendation recommendation = recommendationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation", id));
        apply(recommendation, r);
        return RecommendationDto.Response.from(recommendation);
    }

    @Transactional
    public void delete(Long id) {
        if (!recommendationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Recommendation", id);
        }
        recommendationRepository.deleteById(id);
    }

    /**
     * Preview of data-driven suggestions for a customer; nothing is saved. Order of priority:
     * stylist-recommended treatment follow-ups, services the customer keeps returning for,
     * then popular services they have not tried.
     */
    public List<RecommendationDto.Suggestion> suggest(Long customerId) {
        requireCustomer(customerId);
        List<RecommendationDto.Suggestion> suggestions = new ArrayList<>();
        Set<Long> suggested = new HashSet<>();
        Set<Long> tried = new HashSet<>();

        // 1) follow-up services a stylist recorded in the customer's treatment history
        int followUps = 0;
        for (TreatmentRecord t : treatmentRepository
                .findByCustomerIdAndFollowUpServiceIsNotNullOrderByTreatmentDateDesc(customerId)) {
            ServiceItem next = t.getFollowUpService();
            if (followUps >= MAX_FOLLOW_UP_SUGGESTIONS) {
                break;
            }
            if (!next.isActive() || !suggested.add(next.getId())) {
                continue;
            }
            StringBuilder msg = new StringBuilder("After your ").append(t.getService().getName())
                    .append(" on ").append(t.getTreatmentDate()).append(", ")
                    .append(t.getStylist().getFullName()).append(" recommends ").append(next.getName());
            if (t.getFollowUpAfterDays() != null) {
                msg.append(" around ").append(t.getTreatmentDate().plusDays(t.getFollowUpAfterDays()));
            }
            msg.append('.');
            if (t.getFollowUpAdvice() != null) {
                msg.append(' ').append(t.getFollowUpAdvice());
            }
            suggestions.add(RecommendationDto.Suggestion.builder()
                    .serviceId(next.getId())
                    .serviceName(next.getName())
                    .message(truncate(msg.toString(), MAX_MESSAGE_LENGTH))
                    .build());
            followUps++;
        }

        // 2) services the customer keeps coming back for -> nudge a rebooking
        int repeats = 0;
        for (Object[] row : appointmentRepository.serviceFrequencyForCustomer(customerId)) {
            Long serviceId = ((Number) row[0]).longValue();
            long visits = ((Number) row[1]).longValue();
            tried.add(serviceId);
            if (repeats < MAX_REPEAT_SUGGESTIONS && !suggested.contains(serviceId)) {
                var service = serviceRepository.findById(serviceId).filter(ServiceItem::isActive);
                if (service.isPresent()) {
                    suggested.add(serviceId);
                    suggestions.add(RecommendationDto.Suggestion.builder()
                            .serviceId(service.get().getId())
                            .serviceName(service.get().getName())
                            .message("You've enjoyed " + service.get().getName() + " " + visits
                                    + (visits == 1 ? " time" : " times") + " - ready for another visit?")
                            .build());
                    repeats++;
                }
            }
        }

        // 3) popular services the customer has not tried yet
        int discovery = 0;
        for (Object[] row : appointmentRepository.popularServices(PageRequest.of(0, 10))) {
            if (discovery >= MAX_DISCOVERY_SUGGESTIONS) {
                break;
            }
            Long serviceId = ((Number) row[0]).longValue();
            if (tried.contains(serviceId) || suggested.contains(serviceId)) {
                continue;
            }
            var service = serviceRepository.findById(serviceId).filter(ServiceItem::isActive);
            if (service.isPresent()) {
                suggested.add(serviceId);
                suggestions.add(RecommendationDto.Suggestion.builder()
                        .serviceId(service.get().getId())
                        .serviceName(service.get().getName())
                        .message("Popular with our clients: " + service.get().getName() + ". Give it a try!")
                        .build());
                discovery++;
            }
        }
        return suggestions;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** Replaces the customer's previously generated (AUTO) recommendations with fresh ones. */
    @Transactional
    public List<RecommendationDto.Response> generate(Long customerId) {
        Customer customer = requireCustomer(customerId);
        List<RecommendationDto.Suggestion> suggestions = suggest(customerId);
        recommendationRepository.deleteByCustomerIdAndSource(customerId, RecommendationSource.AUTO);
        recommendationRepository.flush();

        List<Recommendation> saved = new ArrayList<>();
        for (RecommendationDto.Suggestion s : suggestions) {
            ServiceItem service = serviceRepository.getReferenceById(s.getServiceId());
            saved.add(recommendationRepository.save(Recommendation.builder()
                    .customer(customer)
                    .service(service)
                    .message(s.getMessage())
                    .source(RecommendationSource.AUTO)
                    .build()));
        }
        return recommendationRepository.findByCustomerIdAndActiveTrueOrderByCreatedAtDesc(customerId).stream()
                .filter(r -> r.getSource() == RecommendationSource.AUTO)
                .map(RecommendationDto.Response::from)
                .toList();
    }

    private void apply(Recommendation recommendation, RecommendationDto.Request r) {
        Customer customer = requireCustomer(r.getCustomerId());
        ServiceItem service = serviceRepository.findById(r.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", r.getServiceId()));
        Stylist stylist = r.getStylistId() == null ? null : stylistRepository.findById(r.getStylistId())
                .orElseThrow(() -> new ResourceNotFoundException("Stylist", r.getStylistId()));
        recommendation.setCustomer(customer);
        recommendation.setService(service);
        recommendation.setStylist(stylist);
        recommendation.setMessage(r.getMessage());
        recommendation.setActive(r.getActive() == null || r.getActive());
    }

    private Customer requireCustomer(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
