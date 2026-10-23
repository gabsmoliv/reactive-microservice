package com.taskmanagement.auth.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.taskmanagement.auth.dto.LoginRequest;
import com.taskmanagement.auth.dto.RegisterRequest;
import com.taskmanagement.auth.dto.TokenResponse;
import com.taskmanagement.auth.dto.UserResponse;
import com.taskmanagement.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Authentication")
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final RSAKey rsaKey;

    public AuthController(AuthService authService, RSAKey rsaKey) {
        this.authService = authService;
        this.rsaKey = rsaKey;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public Mono<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        LOGGER.info("Starting to register a new user");
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Authenticate and obtain a JWT access token")
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LOGGER.info("Starting a login request for a user");
        return authService.login(request);
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Public keys (JWKS) used to verify issued tokens")
    public Mono<Map<String, Object>> jwks() {
        LOGGER.info("Returning the public keys of issued tokens");
        return Mono.just(new JWKSet(rsaKey).toPublicJWKSet().toJSONObject());
    }
}
