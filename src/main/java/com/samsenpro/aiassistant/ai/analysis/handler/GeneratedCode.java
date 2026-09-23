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
