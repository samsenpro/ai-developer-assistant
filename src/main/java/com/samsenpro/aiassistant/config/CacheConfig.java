package com.samsenpro.aiassistant.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache con Caffeine (configurado en application.yml: tamaño máximo y expiración). Es el
 * nivel 1 de la caché de resultados; el nivel 2 es PostgreSQL (ver AnalysisCache).
 * <p>
 * Redis no aporta lo suficiente con una sola instancia: los resultados ya persisten en PostgreSQL y
 * la caché en memoria solo evita una consulta indexada.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
