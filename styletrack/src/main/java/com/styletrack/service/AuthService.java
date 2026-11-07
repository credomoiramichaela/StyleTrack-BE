package com.styletrack.service;

import com.styletrack.dto.AuthDto;
import com.styletrack.dto.UserDto;
import com.styletrack.entity.ActivityLog;
import com.styletrack.entity.Customer;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.repository.CustomerRepository;
import com.styletrack.repository.UserRepository;
import com.styletrack.security.CurrentUser;
import com.styletrack.security.JwtService;
import com.styletrack.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    @Transactional
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (AuthenticationException e) {
            activityLogService.record(null, request.getUsername(), ActivityLog.Action.LOGIN_FAILED,
                    "User", null, "Failed login attempt for username: " + request.getUsername());
            throw e;
        }
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        log.info("User '{}' logged in with role {}", user.getUsername(), user.getRole());
        activityLogService.record(user.getId(), user.getFullName(), ActivityLog.Action.LOGIN_SUCCESS,
                "User", user.getId(), null);
        return AuthDto.LoginResponse.builder()
                .token(jwtService.generateToken(user.getUsername(), user.getRole()))
                .expiresInSeconds(jwtService.getExpirationSeconds())
                .user(UserDto.Response.from(user))
                .build();
    }

    /**
     * Creates a customer account (login = mobile number) together with its customer record, and signs the
     * customer in. A number that the salon already has on file is not claimed automatically, because the
     * number has not been verified; the front desk sets up the app login for those customers instead.
     */
    @Transactional
    public AuthDto.LoginResponse register(AuthDto.RegisterRequest r) {
        String phone = PhoneUtil.normalize(r.getPhone());
        if (!PhoneUtil.isValid(phone)) {
            throw new BusinessException("Enter a valid mobile number");
        }
        if (userRepository.existsByUsername(phone)) {
            throw new BusinessException("An account with this mobile number already exists. Try signing in.",
                    HttpStatus.CONFLICT);
        }
        if (customerRepository.existsByPhone(phone)) {
            throw new BusinessException("This number is already on file at the salon. "
                    + "Please ask the front desk to set up your app login.", HttpStatus.CONFLICT);
        }
        User user = userRepository.save(User.builder()
                .username(phone)
                .password(passwordEncoder.encode(r.getPassword()))
                .fullName(r.getFullName().trim())
                .email(blankToNull(r.getEmail()))
                .phone(phone)
                .role(Role.CUSTOMER)
                .build());
        customerRepository.save(Customer.builder()
                .user(user)
                .fullName(user.getFullName())
                .phone(phone)
                .email(user.getEmail())
                .build());
        log.info("Customer account registered (user {})", user.getId());
        return AuthDto.LoginResponse.builder()
                .token(jwtService.generateToken(user.getUsername(), user.getRole()))
                .expiresInSeconds(jwtService.getExpirationSeconds())
                .user(UserDto.Response.from(user))
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Transactional(readOnly = true)
    public UserDto.Response me() {
        return UserDto.Response.from(currentUser.get());
    }
}
