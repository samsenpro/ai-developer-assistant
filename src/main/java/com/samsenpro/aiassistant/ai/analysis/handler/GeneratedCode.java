package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.context.BuiltContext;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Utilidades para el código y las rutas que devuelve el modelo. */
final class GeneratedCode {

    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*\\d+ \\| ?");

    private GeneratedCode() {
    }

    /**
     * Limpia el código generado: quita el bloque de Markdown que lo envuelve (```java ... ```) y los
     * prefijos de número de línea copiados del prompt.
     */
    static String cleanCode(String content) {
        return stripLineNumbers(stripCodeFence(content));
    }

    /** Si todo el contenido es un único bloque de código Markdown, devuelve solo su interior. */
    static String stripCodeFence(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        if (!trimmed.startsWith("```") || !trimmed.endsWith("```") || trimmed.length() < 6) {
            return content;
        }
        int firstNewline = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || closing <= firstNewline || trimmed.substring(3, closing).contains("```")) {
            return content;
        }
        return trimmed.substring(firstNewline + 1, closing).stripTrailing() + "\n";
    }

    /**
     * Error frecuente de los modelos: copiar el prefijo de número de línea del prompt ("  42 | ")
     * en el código generado. Si todas las líneas lo llevan, se elimina.
     */
    static String stripLineNumbers(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String[] lines = content.split("\n", -1);
        boolean allNumbered = Arrays.stream(lines)
                .filter(line -> !line.isBlank())
                .allMatch(line -> NUMBERED_LINE.matcher(line).find());
        if (!allNumbered) {
            return content;
        }
        return String.join("\n", Arrays.stream(lines)
                .map(line -> NUMBERED_LINE.matcher(line).replaceFirst(""))
                .toList());
    }

    /**
     * Resuelve la ruta que devuelve el modelo contra los archivos que realmente vio. Acepta la ruta
     * exacta o, si no es ambiguo, solo el nombre del archivo ("UserService.java").
     */
    static Optional<String> resolvePath(String reported, Map<String, Integer> knownFiles) {
        if (reported == null || reported.isBlank()) {
            return Optional.empty();
        }
        String path = reported.strip().replace('\\', '/');
        if (knownFiles.containsKey(path)) {
            return Optional.of(path);
        }
        String suffix = "/" + path;
        var matches = knownFiles.keySet().stream()
                .filter(known -> known.endsWith(suffix))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    static Map<String, Integer> knownFiles(BuiltContext context) {
        return context == null ? Map.of() : context.lineCounts();
    }
}
