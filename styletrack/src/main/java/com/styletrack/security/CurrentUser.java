package com.styletrack.security;

import com.styletrack.entity.Stylist;
import com.styletrack.entity.User;
import com.styletrack.entity.enums.Role;
import com.styletrack.exception.BusinessException;
import com.styletrack.repository.StylistRepository;
import com.styletrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the authenticated user for the current request. */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;
    private final StylistRepository stylistRepository;

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new BusinessException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BusinessException("Not authenticated", HttpStatus.UNAUTHORIZED));
    }

    public boolean isStylist() {
        return get().getRole() == Role.STYLIST;
    }

    /** The stylist profile id linked to the current user. */
    public Long requireStylistId() {
        User user = get();
        return stylistRepository.findByUserId(user.getId())
                .map(Stylist::getId)
                .orElseThrow(() -> new BusinessException("No stylist profile is linked to this account",
                        HttpStatus.FORBIDDEN));
    }
}
