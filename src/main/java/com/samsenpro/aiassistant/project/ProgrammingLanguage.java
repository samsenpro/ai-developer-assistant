package com.samsenpro.aiassistant.project;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lenguajes soportados. La lista es corta a propósito: cada lenguaje necesita su detección de
 * dependencias, su estrategia de tests y prompts que se hayan probado con él.
 */
public enum ProgrammingLanguage {

    JAVA("Java", Set.of("java")),
    PYTHON("Python", Set.of("py")),
    JAVASCRIPT("JavaScript", Set.of("js", "mjs", "cjs", "jsx")),
    TYPESCRIPT("TypeScript", Set.of("ts", "tsx", "mts", "cts")),
    SQL("SQL", Set.of("sql")),
    HTML("HTML", Set.of("html", "htm")),
    CSS("CSS", Set.of("css"));

    private final String displayName;
    private final Set<String> extensions;

    ProgrammingLanguage(String displayName, Set<String> extensions) {
        this.displayName = displayName;
        this.extensions = extensions;
    }

    public String displayName() {
        return displayName;
    }

    public Set<String> extensions() {
        return extensions;
    }

    /** Lenguaje que corresponde a la extensión del nombre de archivo, si está soportada. */
    public static Optional<ProgrammingLanguage> fromFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(language -> language.extensions.contains(extension)).findFirst();
    }

    /** Lenguajes con una estrategia de generación de tests definida. */
    public boolean supportsTestGeneration() {
        return this == JAVA || this == PYTHON || this == JAVASCRIPT || this == TYPESCRIPT;
    }
}
