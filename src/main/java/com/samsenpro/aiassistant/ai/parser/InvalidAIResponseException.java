package com.samsenpro.aiassistant.ai.parser;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La respuesta del modelo no se puede usar. Nunca se devuelve al cliente el texto del modelo: solo
 * el motivo y, si aplica, qué campos fallaron.
 */
public class InvalidAIResponseException extends ApiException {

    public enum Reason {
        EMPTY_RESPONSE,
        TRUNCATED_RESPONSE,
        INVALID_JSON,
        SCHEMA_VIOLATION
    }

    private final Reason reason;

    public InvalidAIResponseException(Reason reason, String message, List<String> violations) {
        super(ErrorCode.INVALID_AI_RESPONSE, message, details(reason, violations));
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    private static Map<String, Object> details(Reason reason, List<String> violations) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason.name());
        if (violations != null && !violations.isEmpty()) {
            details.put("violations", violations);
        }
        return details;
    }
}
