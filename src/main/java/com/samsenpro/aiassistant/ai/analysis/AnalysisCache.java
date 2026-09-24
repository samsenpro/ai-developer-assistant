package com.samsenpro.aiassistant.ai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.samsenpro.aiassistant.ai.AIOperation;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Caché de resultados de IA en dos niveles:
 * <pre>
 * petición → inputHash → nivel 1: Spring Cache (Caffeine, en memoria, 1 h)
 *                               └─ fallo → nivel 2: analysis_results en PostgreSQL (cache-ttl)
 *                                              └─ fallo → llamada al LLM
 * </pre>
 * El inputHash es el SHA-256 del prompt exacto + proveedor + modelo + versión de plantilla. Si el
 * archivo cambia, cambia el hash: no hace falta invalidar nada. La clave incluye el usuario, así que
 * un usuario nunca recibe un resultado generado para otro.
 */
@Component
public class AnalysisCache {

    public static final String CACHE_NAME = "analysis-results";

    private final AnalysisResultRepository repository;
    private final AnalysisProperties properties;
    private final Clock clock;

    public AnalysisCache(AnalysisResultRepository repository, AnalysisProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#userId + ':' + #inputHash", unless = "#result == null")
    public CachedAnalysis find(Long userId, String inputHash) {
        return repository.findReusable(userId, inputHash, clock.instant().minus(properties.cacheTtl()))
                .map(CachedAnalysis::from)
                .orElse(null);
    }

    @CachePut(cacheNames = CACHE_NAME, key = "#userId + ':' + #inputHash")
    public CachedAnalysis put(Long userId, String inputHash, AnalysisResult result) {
        return CachedAnalysis.from(result);
    }

    /** Lo necesario para servir un resultado sin volver a leerlo de la base de datos. */
    public record CachedAnalysis(Long id, Long userId, Long projectId, AIOperation operation, String inputHash,
                                 String promptVersion, JsonNode result, String provider, String model) {

        static CachedAnalysis from(AnalysisResult result) {
            return new CachedAnalysis(result.getId(), result.getUserId(), result.getProjectId(), result.getOperation(),
                    result.getInputHash(), result.getPromptVersion(), result.getResult(), result.getProvider(),
                    result.getModel());
        }
    }
}
