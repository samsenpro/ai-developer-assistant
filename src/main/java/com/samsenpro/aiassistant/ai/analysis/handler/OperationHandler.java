package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import com.samsenpro.aiassistant.file.FileSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Lo específico de cada operación de IA: su plantilla, su DTO de resultado, sus variables de prompt
 * y la validación de negocio del resultado. El flujo común (autorización, contexto, caché,
 * idempotencia, cuotas, llamada al proveedor, persistencia) vive en AnalysisService.
 */
public interface OperationHandler<T> {

    AIOperation operation();

    PromptId promptId();

    Class<T> resultType();

    /** Validaciones propias de la operación, antes de gastar nada. */
    default void validate(AIOperationCommand command, List<FileSnapshot> files) {
    }

    /** Variables propias de la plantilla (las comunes las añade AnalysisService). */
    default Map<String, String> variables(OperationInput input) {
        return Map.of();
    }

    /** Si el código no cabe en un prompt, ¿se puede analizar por partes y combinar los resultados? */
    default boolean supportsSplit() {
        return false;
    }

    default T merge(List<T> parts) {
        throw new UnsupportedOperationException(operation() + " does not support split analysis");
    }

    /**
     * Validación y saneado del resultado contra los datos reales (archivos, líneas...). Los avisos
     * que se añadan a {@code warnings} llegan al usuario.
     */
    default T postProcess(T result, OperationInput input, List<String> warnings) {
        return result;
    }
}
