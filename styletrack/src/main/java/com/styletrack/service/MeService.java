package com.styletrack.service;

import com.styletrack.dto.AppointmentDto;
import com.styletrack.dto.LoyaltyDto;
import com.styletrack.dto.MeDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.dto.RecommendationDto;
import com.styletrack.dto.ServiceDto;
import com.styletrack.dto.StylistDto;
import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.AppointmentSpecs;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the customer mobile app can do. Every method works on the signed-in customer's own data only:
 * the customer is resolved from the login, never from a request parameter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeService {

    private final CurrentUser currentUser;
    private final CustomerRepository customerRepository;
    private final AppointmentRepository appointmentRepository;
    private final StylistRepository stylistRepository;
    private final StylistService stylistService;
    private final ServiceCatalogService serviceCatalogService;
    private final AppointmentService appointmentService;
    private final LoyaltyService loyaltyService;
    private final RecommendationService recommendationService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    // ---------------------------------------------------------------- profile

    public MeDto.Profile profile() {
        Customer c = customer();
        LoyaltyDto.Balance b = loyaltyService.balance(c.getId());
        return MeDto.Profile.from(c, b.getTier(), b.getVisitsToNextTier());
    }

    @Transactional
    public MeDto.Profile updateProfile(MeDto.UpdateProfile r) {
        Customer c = customer();
        c.setFullName(r.getFullName().trim());
        c.setEmail(r.getEmail() == null || r.getEmail().isBlank() ? null : r.getEmail().trim());
        c.setAddress(r.getAddress() == null || r.getAddress().isBlank() ? null : r.getAddress().trim());
        c.setBirthDate(r.getBirthDate());
        c.getUser().setFullName(c.getFullName());
        c.getUser().setEmail(c.getEmail());
        return profile();
    }

    @Transactional
    public void changePassword(MeDto.ChangePassword r) {
        User user = currentUser.get();
        if (!passwordEncoder.matches(r.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Your current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(r.getNewPassword()));
        log.info("Customer user {} changed their password", user.getId());
    }

    // -------------------------------------------------------------- catalogue

    public List<ServiceDto.Response> services() {
        return serviceCatalogService.list(true);
    }

    public List<MeDto.StylistInfo> stylists() {
        return stylistRepository.findByActiveTrueOrderByFullNameAsc().stream().map(MeDto.StylistInfo::from).toList();
    }

    public StylistDto.Availability availability(Long stylistId, LocalDate date, Long serviceId, boolean homeService) {
        stylistRepository.findById(stylistId).filter(s -> s.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Stylist", stylistId));
        return stylistService.availability(stylistId, date, serviceId, homeService);
    }

    // ------------------------------------------------------------ appointments

    public PageResponse<AppointmentDto.Response> appointments(AppointmentStatus status, Pageable pageable) {
        Long customerId = customer().getId();
        var spec = AppointmentSpecs.filter(null, customerId, status, null, null, null);
        return PageResponse.of(appointmentRepository.findAll(spec, pageable).map(AppointmentDto.Response::from));
    }

    public AppointmentDto.Response appointment(Long id) {
        return AppointmentDto.Response.from(owned(id));
    }

    /** Always created as PENDING: the salon accepts or rejects it. */
    @Transactional
    public AppointmentDto.Response book(MeDto.BookRequest r) {
        AppointmentDto.CreateRequest request = new AppointmentDto.CreateRequest();
        request.setCustomerId(customer().getId());
        request.setStylistId(r.getStylistId());
        request.setServiceId(r.getServiceId());
        request.setType(r.getType());
        request.setStartTime(r.getStartTime());
        request.setAddress(r.getAddress());
        request.setNotes(r.getNotes());
        return appointmentService.create(request, true);
    }

    @Transactional
    public AppointmentDto.Response cancel(Long id) {
        Appointment a = owned(id);
        if (!a.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("This appointment has already started", HttpStatus.CONFLICT);
        }
        return appointmentService.cancel(id, true);
    }

    @Transactional
    public AppointmentDto.Response reschedule(Long id, MeDto.RescheduleRequest r) {
        Appointment a = owned(id);
        if (!a.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("This appointment has already started", HttpStatus.CONFLICT);
        }
        return appointmentService.reschedule(id,
                new AppointmentDto.RescheduleRequest(r.getStartTime(), r.getStylistId()), true);
    }

    // ----------------------------------------------------- loyalty, suggestions

    public LoyaltyDto.Balance loyalty() {
        return loyaltyService.balance(customer().getId());
    }

    public PageResponse<LoyaltyDto.TransactionResponse> loyaltyHistory(Pageable pageable) {
        return loyaltyService.history(customer().getId(), pageable);
    }

    public List<RecommendationDto.Response> recommendations() {
        return recommendationService.listForCustomer(customer().getId(), true);
    }

    // ----------------------------------------------------------- notifications

    public PageResponse<MeDto.NotificationResponse> notifications(Pageable pageable) {
        return notificationService.list(currentUser.get().getId(), pageable);
    }

    public MeDto.UnreadCount unreadCount() {
        return MeDto.UnreadCount.builder().unread(notificationService.unreadCount(currentUser.get().getId())).build();
    }

    @Transactional
    public void markSeen(Long id) {
        notificationService.markSeen(currentUser.get().getId(), id);
    }

    @Transactional
    public void markAllSeen() {
        notificationService.markAllSeen(currentUser.get().getId());
    }

    // ---------------------------------------------------------------- helpers

    private Customer customer() {
        User user = currentUser.get();
        return customerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("No customer profile is linked to this account",
                        HttpStatus.FORBIDDEN));
    }

    /** Someone else's booking looks exactly like one that does not exist. */
    private Appointment owned(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        if (!a.getCustomer().getId().equals(customer().getId())) {
            throw new ResourceNotFoundException("Appointment", id);
        }
        return a;
    }
}
