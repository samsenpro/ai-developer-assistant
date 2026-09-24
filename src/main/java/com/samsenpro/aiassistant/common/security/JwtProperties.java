package com.samsenpro.aiassistant.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret     clave HMAC (mínimo 32 bytes). Solo por variable de entorno JWT_SECRET.
 * @param expiration validez del token en segundos
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @Positive long expiration, @NotBlank String issuer) {

    @Override
    public String toString() {
        return "JwtProperties[secret=***, expiration=" + expiration + ", issuer=" + issuer + "]";
    }
}
