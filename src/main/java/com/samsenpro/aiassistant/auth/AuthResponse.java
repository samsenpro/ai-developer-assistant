package com.samsenpro.aiassistant.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "3600") long expiresIn,
        UserView user) {

    public record UserView(Long id, String username, String email) {
    }

    @Override
    public String toString() {
        return "AuthResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", user=" + user + "]";
    }
}
