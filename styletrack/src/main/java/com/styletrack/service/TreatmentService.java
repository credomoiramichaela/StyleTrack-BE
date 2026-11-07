package com.styletrack.service;

import com.styletrack.dto.PageResponse;
import com.styletrack.dto.TreatmentDto;
import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.ServiceItem;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.TreatmentRecord;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.ServiceItemRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.TreatmentRecordRepository;
import com.styletrack.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Treatment history: what was done for a customer, with which products, any reactions and the
 * suggested follow-up. Stylists write and edit their own records; staff can read them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreatmentService {

    private final TreatmentRecordRepository treatmentRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceItemRepository serviceRepository;
    private final StylistRepository stylistRepository;
    private final CurrentUser currentUser;

    public PageResponse<TreatmentDto.Response> listForCustomer(Long customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }
        return PageResponse.of(treatmentRepository.findByCustomerId(customerId, pageable)
                .map(TreatmentDto.Response::from));
    }

    @Transactional
    public TreatmentDto.Response create(TreatmentDto.Request r) {
        Customer customer = customerRepository.findById(r.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", r.getCustomerId()));

        Appointment appointment = null;
        Stylist stylist;
        if (r.getAppointmentId() != null) {
            appointment = appointmentRepository.findById(r.getAppointmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment", r.getAppointmentId()));
            if (!appointment.getCustomer().getId().equals(customer.getId())) {
                throw new BusinessException("The appointment belongs to a different customer");
            }
            if (appointment.getStatus() == AppointmentStatus.CANCELLED
                    || appointment.getStatus() == AppointmentStatus.REJECTED) {
                throw new BusinessException("Cannot add a treatment record to a cancelled or rejected appointment");
            }
            if (treatmentRepository.existsByAppointmentId(appointment.getId())) {
                throw new BusinessException("This appointment already has a treatment record; edit it instead",
                        HttpStatus.CONFLICT);
            }
            stylist = appointment.getStylist();
            if (currentUser.isStylist() && !stylist.getId().equals(currentUser.requireStylistId())) {
                throw new BusinessException("You can only write records for your own appointments",
                        HttpStatus.FORBIDDEN);
            }
        } else if (currentUser.isStylist()) {
            Long own = currentUser.requireStylistId();
            stylist = stylistRepository.findById(own).orElseThrow(() -> new ResourceNotFoundException("Stylist", own));
        } else {
            if (r.getStylistId() == null) {
                throw new BusinessException("stylistId is required");
            }
            stylist = stylistRepository.findById(r.getStylistId())
                    .orElseThrow(() -> new ResourceNotFoundException("Stylist", r.getStylistId()));
        }

        TreatmentRecord record = TreatmentRecord.builder()
                .customer(customer)
                .appointment(appointment)
                .stylist(stylist)
                .build();
        apply(record, r);
        TreatmentRecord saved = treatmentRepository.save(record);
        log.info("Treatment record {} created for customer {}", saved.getId(), customer.getId());
        return TreatmentDto.Response.from(saved);
    }

    @Transactional
    public TreatmentDto.Response update(Long id, TreatmentDto.Request r) {
        TreatmentRecord record = treatmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Treatment record", id));
        if (currentUser.isStylist() && !record.getStylist().getId().equals(currentUser.requireStylistId())) {
            throw new BusinessException("You can only edit your own treatment records", HttpStatus.FORBIDDEN);
        }
        // customer, appointment and stylist stay as originally recorded
        apply(record, r);
        return TreatmentDto.Response.from(record);
    }

    @Transactional
    public void delete(Long id) {
        if (!treatmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Treatment record", id);
        }
        treatmentRepository.deleteById(id);
    }

    private void apply(TreatmentRecord record, TreatmentDto.Request r) {
        ServiceItem service = serviceRepository.findById(r.getServiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service", r.getServiceId()));
        ServiceItem followUp = null;
        if (r.getFollowUpServiceId() != null) {
            followUp = serviceRepository.findById(r.getFollowUpServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service", r.getFollowUpServiceId()));
        }
        record.setService(service);
        record.setTreatmentDate(r.getTreatmentDate());
        record.setDetails(r.getDetails().trim());
        record.setProductsUsed(blankToNull(r.getProductsUsed()));
        record.setReactions(blankToNull(r.getReactions()));
        record.setFollowUpAdvice(blankToNull(r.getFollowUpAdvice()));
        record.setFollowUpService(followUp);
        record.setFollowUpAfterDays(followUp == null ? null : r.getFollowUpAfterDays());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
