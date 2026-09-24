package com.samsenpro.aiassistant.usage;

import java.util.Locale;

/** Resultado de una llamada al proveedor. */
public enum UsageStatus {

    SUCCESS,
    /** El proveedor falló (timeout, no disponible, rate limit...). */
    FAILED,
    /** El proveedor respondió (y consumió tokens) pero la respuesta no superó la validación. */
    INVALID_RESPONSE;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
