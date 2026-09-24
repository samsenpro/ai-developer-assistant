package com.samsenpro.aiassistant.ai.context;

import java.util.List;
import java.util.Map;

/**
 * Contexto listo para los prompts.
 *
 * @param chunks                   bloques de código a enviar; más de uno solo si se dividió una revisión
 * @param files                    qué archivos se incluyeron y cómo (completo, resumen, parcial u omitido)
 * @param lineCounts               ruta → número de líneas de cada archivo visible para el modelo, para
 *                                 validar las referencias que devuelva
 * @param warnings                 avisos para el usuario (secretos enmascarados, posible prompt injection...)
 * @param promptInjectionSuspected algún contenido parece contener instrucciones dirigidas al modelo
 * @param boundary                 identificador de los bloques de datos no fiables de esta petición
 */
public record BuiltContext(List<Chunk> chunks, List<ContextFile> files, Map<String, Integer> lineCounts,
                           List<String> warnings, boolean promptInjectionSuspected, String boundary) {

    public record Chunk(String source, int estimatedTokens) {
    }

    /**
     * @param role  PRIMARY (seleccionado por el usuario) o RELATED (añadido por el ContextBuilder)
     * @param mode  FULL, OUTLINE, PARTIAL (dividido entre varias partes) u OMITTED (no cupo)
     * @param depth distancia a la selección (0 para los seleccionados)
     */
    public record ContextFile(Long fileId, String path, String role, String mode, int depth) {
    }

    public int estimatedTokens() {
        return chunks.stream().mapToInt(Chunk::estimatedTokens).sum();
    }
}
