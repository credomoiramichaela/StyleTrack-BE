package com.styletrack.security;

import com.styletrack.config.AppProperties;
import com.styletrack.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;
    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] bytes = appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("styletrack.jwt.secret (JWT_SECRET) must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String username, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + getExpirationSeconds() * 1000);
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Optional<String> extractUsername(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return Duration.ofMinutes(appProperties.getJwt().getExpirationMinutes()).toSeconds();
    }
}
