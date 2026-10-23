package com.taskmanagement.auth.service;

import com.taskmanagement.auth.domain.User;
import com.taskmanagement.auth.dto.LoginRequest;
import com.taskmanagement.auth.dto.RegisterRequest;
import com.taskmanagement.auth.dto.TokenResponse;
import com.taskmanagement.auth.dto.UserResponse;
import com.taskmanagement.auth.exception.EmailAlreadyExistsException;
import com.taskmanagement.auth.exception.InvalidCredentialsException;
import com.taskmanagement.auth.repository.UserRepository;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Mono<UserResponse> register(RegisterRequest request) {
        String email = normalize(request.email());

        return userRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if(exists) {
                        LOGGER.warn("Registration rejected - email already exists in repository: {}", email);
                        return Mono.error(new EmailAlreadyExistsException(email));
                    }
                    Mono<User> registeredUser = hash(request.password())
                            .flatMap(hashedPassword -> userRepository.save(User.create(email, hashedPassword)));
                    LOGGER.info("Successfully registered a new user with e-mail: {}", email);
                    return registeredUser;
                })
                .onErrorMap(DuplicateKeyException.class, e ->  {
                    LOGGER.warn("Registration rejected - race condition duplicate key error for email: {}", email);
                    return new EmailAlreadyExistsException(email);
                })
                .map(UserResponse::from);
    }

    public Mono<TokenResponse> login(LoginRequest request) {
        String email = normalize(request.email());
        LOGGER.info("Starting to login new user");
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.warn("Login attempt rejected for email: {}", email);
                    return Mono.error(new InvalidCredentialsException());
                }))
                .flatMap(user -> matches(request.password(), user.getPasswordHash())
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.defer(() -> {
                            LOGGER.warn("Login failed - password mismatch for email: {}", email);
                            return Mono.error(new InvalidCredentialsException());
                        }))
                        .thenReturn(user))
                .map(jwtService::issueToken);
    }

    private Mono<String> hash(String rawPassword) {
        return Mono.fromCallable(() -> passwordEncoder.encode(rawPassword))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matches(String rawPassword, String hash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, hash))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
