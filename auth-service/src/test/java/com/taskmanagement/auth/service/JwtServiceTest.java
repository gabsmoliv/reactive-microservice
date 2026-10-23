package com.taskmanagement.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.taskmanagement.auth.config.JwtProperties;
import com.taskmanagement.auth.domain.User;
import com.taskmanagement.auth.dto.TokenResponse;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private static RSAPublicKey publicKey;
    private static JwtService jwtService;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        publicKey = (RSAPublicKey) pair.getPublic();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("test-key")
                .build();

        JwtProperties properties = new JwtProperties("test-issuer", Duration.ofMinutes(30), null, null);
        jwtService = new JwtService(
                new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey))),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueToken_producesRs256TokenVerifiableWithPublicKey() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, "john@example.com", "hash", NOW);

        TokenResponse response = jwtService.issueToken(user);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);

        Jwt jwt = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(response.accessToken());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(jwt.<String>getClaim("email")).isEqualTo("john@example.com");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }
}
