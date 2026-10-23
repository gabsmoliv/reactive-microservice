package com.taskmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskmanagement.auth.domain.User;
import com.taskmanagement.auth.dto.LoginRequest;
import com.taskmanagement.auth.dto.RegisterRequest;
import com.taskmanagement.auth.dto.TokenResponse;
import com.taskmanagement.auth.exception.EmailAlreadyExistsException;
import com.taskmanagement.auth.exception.InvalidCredentialsException;
import com.taskmanagement.auth.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_savesUserWithHashedPasswordAndNormalizedEmail() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return Mono.just(u);
        });

        StepVerifier.create(authService.register(new RegisterRequest("  John@Example.COM ", "s3cret-pass")))
                .assertNext(response -> {
                    assertThat(response.id()).isNotNull();
                    assertThat(response.email()).isEqualTo("john@example.com");
                })
                .verifyComplete();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash())
                .isNotEqualTo("s3cret-pass")
                .satisfies(h -> assertThat(passwordEncoder.matches("s3cret-pass", h)).isTrue());
    }

    @Test
    void register_failsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(authService.register(new RegisterRequest("john@example.com", "s3cret-pass")))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_mapsDuplicateKeyRaceToEmailAlreadyExists() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenReturn(Mono.error(new DuplicateKeyException("dup")));

        StepVerifier.create(authService.register(new RegisterRequest("john@example.com", "s3cret-pass")))
                .expectError(EmailAlreadyExistsException.class)
                .verify();
    }

    @Test
    void login_returnsTokenWhenCredentialsAreValid() {
        User user = new User(UUID.randomUUID(), "john@example.com", passwordEncoder.encode("s3cret-pass"),
                Instant.now());
        TokenResponse token = TokenResponse.bearer("jwt", 3600);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Mono.just(user));
        when(jwtService.issueToken(user)).thenReturn(token);

        StepVerifier.create(authService.login(new LoginRequest("John@example.com", "s3cret-pass")))
                .expectNext(token)
                .verifyComplete();
    }

    @Test
    void login_failsWhenPasswordIsWrong() {
        User user = new User(UUID.randomUUID(), "john@example.com", passwordEncoder.encode("s3cret-pass"),
                Instant.now());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Mono.just(user));

        StepVerifier.create(authService.login(new LoginRequest("john@example.com", "wrong")))
                .expectError(InvalidCredentialsException.class)
                .verify();

        verify(jwtService, never()).issueToken(any());
    }

    @Test
    void login_failsWithSameErrorWhenUserDoesNotExist() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.login(new LoginRequest("ghost@example.com", "whatever")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }
}
