package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;

import java.time.Instant;

/** Metadatos de un archivo sin su contenido. */
public record SourceFileSummary(Long id, Long projectId, String path, String filename, ProgrammingLanguage language,
                                int sizeBytes, int lineCount, String contentHash, Instant createdAt,
                                Instant updatedAt) {

    public String baseName() {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
