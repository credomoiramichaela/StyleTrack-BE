package com.styletrack.service;

import com.styletrack.dto.UserDto;
import com.styletrack.entity.ActivityLog;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.exception.ResourceNotFoundException;
import com.styletrack.repository.UserRepository;
import com.styletrack.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;
    private final ActivityLogService activityLogService;

    public List<UserDto.Response> list() {
        // customers' app accounts are managed from the customer record, not here
        return userRepository.findAll().stream().filter(u -> u.getRole() != Role.CUSTOMER)
                .map(UserDto.Response::from).toList();
    }

    public UserDto.Response get(Long id) {
        return UserDto.Response.from(find(id));
    }

    @Transactional
    public UserDto.Response create(UserDto.CreateRequest r) {
        if (r.getRole() == Role.CUSTOMER) {
            throw new BusinessException("Customer logins are created from the customer's record");
        }
        if (userRepository.existsByUsername(r.getUsername())) {
            throw new BusinessException("Username already exists", HttpStatus.CONFLICT);
        }
        User user = User.builder()
                .username(r.getUsername().trim())
                .password(passwordEncoder.encode(r.getPassword()))
                .fullName(r.getFullName())
                .email(r.getEmail())
                .phone(r.getPhone())
                .role(r.getRole())
                .build();
        User saved = userRepository.save(user);
        activityLogService.record(ActivityLog.Action.USER_CREATED, "User", saved.getId(),
                "Created '" + saved.getUsername() + "' with role " + saved.getRole());
        return UserDto.Response.from(saved);
    }

    @Transactional
    public UserDto.Response update(Long id, UserDto.UpdateRequest r) {
        User user = find(id);
        User me = currentUser.get();
        if (me.getId().equals(user.getId()) && (!r.getActive() || r.getRole() != user.getRole())) {
            throw new BusinessException("You cannot deactivate or change the role of your own account");
        }
        if ((r.getRole() == Role.CUSTOMER) != (user.getRole() == Role.CUSTOMER)) {
            throw new BusinessException("Customer logins cannot be converted to or from staff roles");
        }
        Role previousRole = user.getRole();
        user.setFullName(r.getFullName());
        user.setEmail(r.getEmail());
        user.setPhone(r.getPhone());
        user.setRole(r.getRole());
        user.setActive(r.getActive());
        if (previousRole != r.getRole()) {
            activityLogService.record(ActivityLog.Action.ROLE_CHANGED, "User", user.getId(),
                    "Role changed from " + previousRole + " to " + r.getRole());
        } else {
            activityLogService.record(ActivityLog.Action.USER_UPDATED, "User", user.getId(),
                    "Updated '" + user.getUsername() + "'");
        }
        return UserDto.Response.from(user);
    }

    @Transactional
    public void changePassword(Long id, UserDto.PasswordRequest r) {
        User user = find(id);
        user.setPassword(passwordEncoder.encode(r.getNewPassword()));
        log.info("Password reset for user '{}'", user.getUsername());
    }

    @Transactional
    public void deactivate(Long id) {
        User user = find(id);
        if (currentUser.get().getId().equals(user.getId())) {
            throw new BusinessException("You cannot deactivate your own account");
        }
        user.setActive(false);
        activityLogService.record(ActivityLog.Action.USER_DEACTIVATED, "User", user.getId(),
                "Deactivated '" + user.getUsername() + "'");
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
