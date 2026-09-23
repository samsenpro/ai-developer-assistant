package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;

/** Archivo ya validado y normalizado (ruta canónica, saltos de línea LF, hash calculado). */
public record ValidatedFile(String path, String filename, ProgrammingLanguage language, String content,
                            int sizeBytes, int lineCount, String contentHash) {
}
