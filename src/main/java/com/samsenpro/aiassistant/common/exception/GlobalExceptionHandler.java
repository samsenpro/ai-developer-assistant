package com.samsenpro.aiassistant.common.exception;

import com.samsenpro.aiassistant.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traduce cualquier excepción a la respuesta de error común. Nunca devuelve stack traces ni
 * mensajes internos: los errores inesperados se registran con su traza y el cliente solo recibe
 * INTERNAL_ERROR (con el correlation ID en la cabecera para poder buscarlos en los logs).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        ErrorCode code = ex.code();
        if (code.status().is5xxServerError()) {
            log.warn("Request failed with {}: {}", code, ex.getMessage());
        } else {
            log.debug("Request rejected with {}: {}", code, ex.getMessage());
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
        if (ex.retryAfter() != null) {
            // Redondeo hacia arriba: un Retry-After de 0 invitaría a reintentar demasiado pronto
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, ex.retryAfter().toSeconds()
                    + (ex.retryAfter().toMillisPart() > 0 ? 1 : 0))));
        }
        return builder.body(ApiResponse.failure(code.name(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        List<String> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        Map<String, Object> details = new LinkedHashMap<>();
        if (!fieldErrors.isEmpty()) {
            details.put("fieldErrors", fieldErrors);
        }
        if (!globalErrors.isEmpty()) {
            details.put("errors", globalErrors);
        }
        return build(ErrorCode.INVALID_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<String> errors = ex.getAllErrors().stream().map(error -> error.getDefaultMessage()).toList();
        return build(ErrorCode.INVALID_REQUEST, "Request validation failed", Map.of("errors", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        // El mensaje de Jackson puede incluir fragmentos del cuerpo: no se devuelve
        return build(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage(), Map.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestPartException.class,
            MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadParameter(Exception ex) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.defaultMessage(), Map.of());
        }
        String message = switch (ex) {
            case MissingServletRequestParameterException e -> "Missing request parameter '" + e.getParameterName() + "'";
            case MissingRequestHeaderException e -> "Missing request header '" + e.getHeaderName() + "'";
            case MethodArgumentTypeMismatchException e -> "Invalid value for parameter '" + e.getName() + "'";
            case MissingServletRequestPartException e -> "Missing multipart part '" + e.getRequestPartName() + "'";
            default -> "Invalid multipart request";
        };
        return build(ErrorCode.INVALID_REQUEST, message, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(), Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(NoResourceFoundException ex) {
        return build(ErrorCode.ROUTE_NOT_FOUND, ErrorCode.ROUTE_NOT_FOUND.defaultMessage(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), Map.of());
    }

    private Map<String, String> toFieldError(FieldError error) {
        return Map.of("field", error.getField(),
                "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, Map<String, Object> details) {
        return ResponseEntity.status(code.status()).body(ApiResponse.failure(code.name(), message, details));
    }
}
