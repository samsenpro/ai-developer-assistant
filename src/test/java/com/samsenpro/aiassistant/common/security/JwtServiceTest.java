package com.samsenpro.aiassistant.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final AuthenticatedUser USER = new AuthenticatedUser(42L, "samuel", "hash");

    @Test
    void issuesATokenWhoseSubjectIsTheUserId() {
        JwtService service = service(SECRET, NOW);

        assertThat(service.extractUserId(service.generateToken(USER))).contains(42L);
    }

    @Test
    void rejectsExpiredTokens() {
        String token = service(SECRET, NOW).generateToken(USER);

        assertThat(service(SECRET, NOW.plusSeconds(3601)).extractUserId(token)).isEmpty();
    }

    @Test
    void rejectsTamperedTokensAndTokensSignedWithAnotherKey() {
        String token = service(SECRET, NOW).generateToken(USER);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(service(SECRET, NOW).extractUserId(tampered)).isEmpty();
        assertThat(service("another-secret-another-secret-12345678", NOW).extractUserId(token)).isEmpty();
        assertThat(service(SECRET, NOW).extractUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesToStartWithAShortSecret() {
        assertThatThrownBy(() -> service("short", NOW)).hasMessageContaining("at least 32 bytes");
    }

    @Test
    void neverPrintsTheSecret() {
        assertThat(new JwtProperties(SECRET, 3600, "issuer").toString()).doesNotContain(SECRET);
    }

    private static JwtService service(String secret, Instant now) {
        return new JwtService(new JwtProperties(secret, 3600, "ai-developer-assistant"), Clock.fixed(now, ZoneOffset.UTC));
    }
}
