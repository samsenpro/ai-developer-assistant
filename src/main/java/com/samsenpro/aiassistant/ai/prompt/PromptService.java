package com.samsenpro.aiassistant.ai.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * Carga y valida todas las plantillas al arrancar: una plantilla rota impide que la aplicación
 * arranque en lugar de fallar en la primera petición de un usuario.
 * <p>
 * Los prompts viven en archivos versionados junto al código (revisables en un PR como cualquier
 * otro cambio) y su versión viaja con cada resultado persistido.
 */
@Service
public class PromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);
    private static final String LOCATION = "prompts/";

    private final Map<PromptId, PromptTemplate> templates = new EnumMap<>(PromptId.class);

    public PromptService() {
        for (PromptId id : PromptId.values()) {
            PromptTemplate template = PromptTemplate.parse(id.file(), read(LOCATION + id.file()));
            templates.put(id, template);
            log.debug("Loaded prompt {} version {}", template.id(), template.version());
        }
        log.info("Loaded {} prompt templates", templates.size());
    }

    public PromptTemplate get(PromptId id) {
        return templates.get(id);
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read prompt template " + path, ex);
        }
    }
}
