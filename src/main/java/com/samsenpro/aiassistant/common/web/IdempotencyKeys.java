package com.samsenpro.aiassistant.common.web;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;

import java.util.regex.Pattern;

/** Validación de la cabecera Idempotency-Key. */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");

    private IdempotencyKeys() {
    }

    /** Devuelve la clave normalizada, null si no se envió, o INVALID_REQUEST si no es válida. */
    public static String validate(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (!VALID.matcher(trimmed).matches()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Idempotency-Key must be 8-100 characters: letters, digits, '.', '_', ':' or '-' (a UUID is recommended)");
        }
        return trimmed;
    }
}
