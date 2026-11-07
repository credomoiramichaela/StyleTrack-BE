package com.styletrack.controller;

import com.styletrack.dto.AuthDto;
import com.styletrack.dto.UserDto;
import com.styletrack.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDto.LoginResponse register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public UserDto.Response me() {
        return authService.me();
    }
}
