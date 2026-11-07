package com.styletrack.service;

import com.styletrack.dto.MeDto;
import com.styletrack.dto.PageResponse;
import com.styletrack.entity.Appointment;
import com.styletrack.entity.Customer;
import com.styletrack.entity.Notification;
import com.styletrack.entity.enums.NotificationType;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** In-app notifications for customers who have a mobile-app account. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH);

    private final NotificationRepository notificationRepository;

    /** Tells the customer about a booking change. Customers without an app account are skipped. */
    @Transactional
    public void notifyBooking(Appointment appointment, NotificationType type, String title, String extra) {
        Customer customer = appointment.getCustomer();
        if (customer.getUser() == null) {
            return;
        }
        String message = appointment.getService().getName() + " with " + appointment.getStylist().getFullName()
                + ", " + appointment.getStartTime().format(WHEN) + "." + (extra == null || extra.isBlank() ? "" : " " + extra);
        notificationRepository.save(Notification.builder()
                .user(customer.getUser())
                .type(type)
                .title(title)
                .message(message.length() > 500 ? message.substring(0, 499) + "…" : message)
                .appointmentId(appointment.getId())
                .build());
        log.debug("Notification {} queued for customer {}", type, customer.getId());
    }

    public PageResponse<MeDto.NotificationResponse> list(Long userId, Pageable pageable) {
        return PageResponse.of(notificationRepository.findByUserId(userId, pageable).map(MeDto.NotificationResponse::from));
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndSeenFalse(userId);
    }

    @Transactional
    public void markSeen(Long userId, Long id) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.setSeen(true);
    }

    @Transactional
    public void markAllSeen(Long userId) {
        notificationRepository.markAllSeen(userId);
    }
}
