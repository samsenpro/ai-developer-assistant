package com.samsenpro.aiassistant.ai.provider;

/**
 * Proveedor de LLM. Es la única pieza que conoce el SDK o la API HTTP del proveedor: el resto de
 * la aplicación trabaja con {@link AIPrompt} y {@link AIResponse}.
 * <p>
 * Contrato de errores: toda implementación traduce sus fallos a
 * {@link com.samsenpro.aiassistant.common.exception.ApiException} con uno de estos códigos, que
 * {@link AIClient} usa para decidir si prueba con el proveedor de fallback:
 * AI_PROVIDER_TIMEOUT, AI_PROVIDER_UNAVAILABLE, AI_RATE_LIMIT o CONTEXT_TOO_LARGE.
 * <p>
 * Para añadir un proveedor (p. ej. uno con API propia) basta con implementar esta interfaz como
 * bean y referenciarlo por su {@link #name()} en {@code ai.provider.primary} o
 * {@code ai.provider.fallback}.
 */
public interface AIProvider {

    /** Nombre estable y de baja cardinalidad: se usa en la configuración, en las métricas y en ai_usage. */
    String name();

    /** Modelo que se usará por defecto (forma parte del hash de caché). */
    String model();

    /** Respuesta en texto libre (conversaciones). */
    AIResponse generate(AIPrompt prompt);

    /**
     * Respuesta que debe ser un objeto JSON. El proveedor usa su modo nativo de salida JSON si lo
     * tiene; aun así el resultado se valida siempre fuera del proveedor.
     */
    AIResponse generateStructured(AIPrompt prompt);
}
