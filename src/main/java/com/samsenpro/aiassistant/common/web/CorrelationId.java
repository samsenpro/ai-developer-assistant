package com.samsenpro.aiassistant.common.web;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador que agrupa todo lo que ocurre a raíz de una petición: logs de la petición, del
 * job asíncrono que lanza y de las llamadas al proveedor LLM.
 * <p>
 * Misma cabecera y misma clave de MDC que en observability-platform, para que ambos proyectos se
 * puedan correlacionar con las mismas herramientas.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    /**
     * Solo se aceptan valores cortos y con caracteres seguros: el valor acaba en logs y cabeceras,
     * y un valor arbitrario del cliente permitiría inyectar texto en ellos.
     */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private CorrelationId() {
    }

    /** Conserva el valor recibido si es válido; si falta o no es válido, genera uno nuevo. */
    public static String resolve(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }
}
