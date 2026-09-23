package com.samsenpro.aiassistant.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "samuel") @NotBlank @Size(max = 50) String username,
        @Schema(example = "Str0ngPassword!") @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
