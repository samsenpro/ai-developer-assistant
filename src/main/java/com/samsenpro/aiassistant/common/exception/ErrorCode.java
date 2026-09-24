package com.samsenpro.aiassistant.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error estables de la API. El cliente decide qué hacer a partir del código, nunca
 * del mensaje (que puede cambiar).
 */
public enum ErrorCode {

    // Petición del cliente
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "The request is not valid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request body could not be read"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    UNAUTHORIZED_RESOURCE(HttpStatus.FORBIDDEN, "You do not have access to this resource"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested route does not exist"),
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project not found"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "File not found"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "Analysis not found"),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Job not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed for this route"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "The username is already taken"),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "The email is already registered"),
    PROJECT_NAME_TAKEN(HttpStatus.CONFLICT, "A project with that name already exists"),
    FILE_PATH_TAKEN(HttpStatus.CONFLICT, "A file with that path already exists in the project"),
    PROJECT_FILE_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "The project has reached its file limit"),
    CONVERSATION_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "The conversation has reached its message limit"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The request body is too large"),

    // Idempotencia
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY,
            "The Idempotency-Key was already used with a different request"),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT,
            "A request with this Idempotency-Key is still being processed"),

    // IA
    CONTEXT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The selected content exceeds the AI context limit"),
    AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Too many AI requests"),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AI usage quota exceeded"),
    AI_PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The AI provider did not respond in time"),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The AI provider is not available"),
    INVALID_AI_RESPONSE(HttpStatus.BAD_GATEWAY, "The AI provider returned a response that could not be validated"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** Fallos atribuibles al proveedor en los que tiene sentido probar con otro proveedor. */
    public boolean isProviderFailure() {
        return this == AI_PROVIDER_TIMEOUT || this == AI_PROVIDER_UNAVAILABLE || this == AI_RATE_LIMIT;
    }
}
