package com.samsenpro.aiassistant.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Rechaza con 413 los cuerpos que superan el límite antes de leerlos. Tomcat no limita los cuerpos
 * JSON, y sin esto una sola petición podría obligar a cargar en memoria megas de "código".
 * <p>
 * Los cuerpos sin Content-Length (chunked) los acota el límite de tamaño de cada campo validado en
 * los DTOs y el tamaño máximo de archivo.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(@Value("${app.http.max-request-size:1MB}") DataSize maxRequestSize,
                                  ObjectMapper objectMapper) {
        this.maxBytes = maxRequestSize.toBytes();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            ErrorCode code = ErrorCode.PAYLOAD_TOO_LARGE;
            response.setStatus(code.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code.name(),
                    code.defaultMessage(), Map.of("maxBytes", maxBytes)));
            return;
        }
        chain.doFilter(request, response);
    }
}
