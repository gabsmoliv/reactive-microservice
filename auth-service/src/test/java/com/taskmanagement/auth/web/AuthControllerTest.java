package com.taskmanagement.auth.web;

import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.taskmanagement.auth.config.SecurityConfig;
import com.taskmanagement.auth.dto.LoginRequest;
import com.taskmanagement.auth.dto.RegisterRequest;
import com.taskmanagement.auth.dto.TokenResponse;
import com.taskmanagement.auth.dto.UserResponse;
import com.taskmanagement.auth.exception.EmailAlreadyExistsException;
import com.taskmanagement.auth.exception.GlobalExceptionHandler;
import com.taskmanagement.auth.exception.InvalidCredentialsException;
import com.taskmanagement.auth.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthControllerTest.KeyConfig.class})
class AuthControllerTest {

    @TestConfiguration
    static class KeyConfig {
        @Bean
        RSAKey rsaKey() throws Exception {
            return new RSAKeyGenerator(2048).keyID("test").generate();
        }
    }

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private AuthService authService;

    @Test
    void register_returns201() {
        UserResponse user = new UserResponse(UUID.randomUUID(), "john@example.com", Instant.now());
        when(authService.register(new RegisterRequest("john@example.com", "s3cret-pass")))
                .thenReturn(Mono.just(user));

        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("john@example.com", "s3cret-pass"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.email").isEqualTo("john@example.com")
                .jsonPath("$.passwordHash").doesNotExist();
    }

    @Test
    void register_returns400OnInvalidPayload() {
        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"not-an-email\",\"password\":\"short\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Validation failed")
                .jsonPath("$.details.length()").isEqualTo(2);
    }

    @Test
    void register_returns409WhenEmailTaken() {
        when(authService.register(new RegisterRequest("john@example.com", "s3cret-pass")))
                .thenReturn(Mono.error(new EmailAlreadyExistsException("john@example.com")));

        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterRequest("john@example.com", "s3cret-pass"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void login_returnsToken() {
        when(authService.login(new LoginRequest("john@example.com", "s3cret-pass")))
                .thenReturn(Mono.just(TokenResponse.bearer("jwt-value", 3600)));

        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("john@example.com", "s3cret-pass"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("jwt-value")
                .jsonPath("$.tokenType").isEqualTo("Bearer");
    }

    @Test
    void login_returns401OnInvalidCredentials() {
        when(authService.login(new LoginRequest("john@example.com", "bad")))
                .thenReturn(Mono.error(new InvalidCredentialsException()));

        client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new LoginRequest("john@example.com", "bad"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void jwks_exposesOnlyPublicKeyMaterial() {
        client.get().uri("/.well-known/jwks.json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.keys[0].kty").isEqualTo("RSA")
                .jsonPath("$.keys[0].n").exists()
                .jsonPath("$.keys[0].d").doesNotExist()
                .jsonPath("$.keys[0].p").doesNotExist();
    }
}
