package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;

/**
 * Copia inmutable de un archivo para las operaciones de IA. Las llamadas al LLM se hacen fuera de
 * cualquier transacción, así que no se trabaja con entidades JPA.
 */
public record FileSnapshot(Long id, String path, String filename, ProgrammingLanguage language, String content,
                           int lineCount, String contentHash) {

    public static FileSnapshot from(SourceFile file) {
        return new FileSnapshot(file.getId(), file.getPath(), file.getFilename(), file.getLanguage(),
                file.getContent(), file.getLineCount(), file.getContentHash());
    }

    /** Nombre sin extensión: {@code UserService.java -> UserService}. */
    public String baseName() {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
