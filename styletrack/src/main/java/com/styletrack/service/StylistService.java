package com.styletrack.service;

import com.styletrack.config.AppProperties;
import com.styletrack.dto.StylistDto;
import com.styletrack.entity.ServiceItem;
import com.styletrack.entity.Stylist;
import com.styletrack.entity.StylistSchedule;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.AppointmentStatus;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.AppointmentRepository;
import com.styletrack.repository.ServiceItemRepository;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.StylistScheduleRepository;
import com.styletrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StylistService {

    private static final List<AppointmentStatus> ACTIVE =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.ACCEPTED);

    private final StylistRepository stylistRepository;
    private final StylistScheduleRepository scheduleRepository;
    private final ServiceItemRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public List<StylistDto.Response> list(boolean activeOnly) {
        List<Stylist> stylists = activeOnly
                ? stylistRepository.findByActiveTrueOrderByFullNameAsc()
                : stylistRepository.findAllByOrderByFullNameAsc();
        return stylists.stream().map(StylistDto.Response::from).toList();
    }

    public StylistDto.Response get(Long id) {
        return StylistDto.Response.from(find(id));
    }

    @Transactional
    public StylistDto.Response create(StylistDto.Request r) {
        Stylist stylist = Stylist.builder().build();
        apply(stylist, r);
        if (StringUtils.hasText(r.getUsername())) {
            if (!StringUtils.hasText(r.getPassword())) {
                throw new BusinessException("A password is required when creating a stylist login");
            }
            if (userRepository.existsByUsername(r.getUsername())) {
                throw new BusinessException("Username already exists", HttpStatus.CONFLICT);
            }
            User user = userRepository.save(User.builder()
                    .username(r.getUsername().trim())
                    .password(passwordEncoder.encode(r.getPassword()))
                    .fullName(r.getFullName())
                    .email(r.getEmail())
                    .phone(r.getPhone())
                    .role(Role.STYLIST)
                    .build());
            stylist.setUser(user);
        }
        return StylistDto.Response.from(stylistRepository.save(stylist));
    }

    @Transactional
    public StylistDto.Response update(Long id, StylistDto.Request r) {
        Stylist stylist = find(id);
        apply(stylist, r);
        if (stylist.getUser() != null) {
            stylist.getUser().setActive(stylist.isActive());
        }
        return StylistDto.Response.from(stylist);
    }

    @Transactional
    public void deactivate(Long id) {
        Stylist stylist = find(id);
        stylist.setActive(false);
        if (stylist.getUser() != null) {
            stylist.getUser().setActive(false);
        }
    }

    /**
     * Start times (on the slot grid) at which the stylist can take the given service on that date:
     * inside a schedule block, not in the past and not overlapping an active booking.
     */
    public StylistDto.Availability availability(Long stylistId, LocalDate date, Long serviceId, boolean homeService) {
        Stylist stylist = find(stylistId);
        ServiceItem service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));

        List<StylistSchedule> blocks = scheduleRepository.findByStylistIdAndDayOfWeek(stylistId, date.getDayOfWeek());
        var booked = appointmentRepository.findByStylistIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                stylistId, ACTIVE, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        int step = Math.max(5, appProperties.getBooking().getSlotMinutes());
        int buffer = homeService ? appProperties.getBooking().getTravelBufferMinutes() : 0;
        LocalDateTime now = LocalDateTime.now();
        List<LocalTime> slots = new ArrayList<>();

        if (stylist.isActive() && service.isActive() && (!homeService || service.isHomeServiceAvailable())) {
            for (StylistSchedule block : blocks) {
                if (homeService && !block.isHomeServiceAvailable()) {
                    continue;
                }
                LocalTime t = block.getStartTime();
                while (!t.plusMinutes(service.getDurationMinutes()).isAfter(block.getEndTime())
                        && t.plusMinutes(service.getDurationMinutes()).isAfter(t)) {
                    LocalDateTime start = date.atTime(t);
                    LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
                    LocalDateTime checkStart = start.minusMinutes(buffer);
                    LocalDateTime checkEnd = end.plusMinutes(buffer);
                    boolean free = booked.stream()
                            .noneMatch(a -> a.getStartTime().isBefore(checkEnd) && a.getEndTime().isAfter(checkStart));
                    if (start.isAfter(now) && free) {
                        slots.add(t);
                    }
                    LocalTime next = t.plusMinutes(step);
                    if (!next.isAfter(t)) {
                        break;
                    }
                    t = next;
                }
            }
        }
        return StylistDto.Availability.builder()
                .stylistId(stylistId)
                .date(date)
                .serviceId(serviceId)
                .availableStartTimes(slots.stream().distinct().sorted().toList())
                .build();
    }

    private Stylist find(Long id) {
        return stylistRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Stylist", id));
    }

    private void apply(Stylist s, StylistDto.Request r) {
        s.setFullName(r.getFullName().trim());
        s.setPhone(r.getPhone());
        s.setEmail(r.getEmail());
        s.setSpecialization(r.getSpecialization());
        s.setBio(r.getBio());
        s.setActive(r.getActive() == null || r.getActive());
    }
}
