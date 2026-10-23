package com.taskmanagement.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param issuer     value of the {@code iss} claim
 * @param expiration token lifetime
 * @param privateKey optional PEM (PKCS#8) RSA private key
 * @param publicKey  optional PEM (X.509) RSA public key
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String issuer, Duration expiration, String privateKey, String publicKey) {

    public boolean hasConfiguredKeys() {
        return privateKey != null && !privateKey.isBlank()
                && publicKey != null && !publicKey.isBlank();
    }
}
