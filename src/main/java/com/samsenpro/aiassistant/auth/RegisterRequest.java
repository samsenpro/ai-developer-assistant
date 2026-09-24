package com.samsenpro.aiassistant.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "samuel")
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "must be 3-50 characters: letters, digits, '.', '_' or '-'")
        String username,

        @Schema(example = "samuel@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt solo usa los primeros 72 bytes: más longitud daría una falsa sensación de seguridad
        @Schema(example = "Str0ngPassword!")
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=***]";
    }
}
