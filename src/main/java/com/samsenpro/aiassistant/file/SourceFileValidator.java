package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.common.Hashing;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Valida y normaliza un archivo antes de guardarlo: ruta, extensión soportada, tamaño y contenido.
 * <p>
 * La ruta es solo un identificador lógico dentro del proyecto (el archivo nunca se escribe en
 * disco), pero se normaliza igualmente: acaba en los prompts y en las respuestas, y no debe
 * contener secuencias como {@code ..} o caracteres de control.
 */
@Component
public class SourceFileValidator {

    private static final int MAX_PATH_LENGTH = 500;
    private static final Pattern PATH_CHARS = Pattern.compile("^[\\p{L}\\p{N}._\\-/ @+]+$");
    private static final String SUPPORTED_EXTENSIONS = Arrays.stream(ProgrammingLanguage.values())
            .flatMap(language -> language.extensions().stream())
            .sorted()
            .map(extension -> "." + extension)
            .collect(Collectors.joining(", "));

    private final FileProperties properties;

    public SourceFileValidator(FileProperties properties) {
        this.properties = properties;
    }

    public ValidatedFile validate(String rawPath, String rawContent, ProgrammingLanguage declaredLanguage) {
        String path = normalizePath(rawPath);
        String filename = path.substring(path.lastIndexOf('/') + 1);

        ProgrammingLanguage language = ProgrammingLanguage.fromFilename(filename)
                .orElseThrow(() -> invalid("Unsupported file extension. Supported extensions: " + SUPPORTED_EXTENSIONS,
                        Map.of("filename", filename)));
        if (declaredLanguage != null && declaredLanguage != language) {
            throw invalid("The declared language does not match the file extension",
                    Map.of("declared", declaredLanguage, "detected", language));
        }

        if (rawContent == null || rawContent.isBlank()) {
            throw invalid("The file content must not be empty", Map.of());
        }
        // LF en todos los archivos: los números de línea que se envían al modelo y los que devuelve
        // deben coincidir con los que ve el usuario
        String content = rawContent.replace("\r\n", "\n").replace('\r', '\n');
        int sizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (sizeBytes > properties.maxSize().toBytes()) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "The file exceeds the maximum allowed size",
                    Map.of("maxBytes", properties.maxSize().toBytes(), "actualBytes", sizeBytes));
        }
        if (looksBinary(content)) {
            throw invalid("The file content must be text (binary content detected)", Map.of());
        }

        return new ValidatedFile(path, filename, language, content, sizeBytes, countLines(content),
                Hashing.sha256(content));
    }

    static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw invalid("The file path is required", Map.of());
        }
        String path = rawPath.trim().replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw invalid("The file path must be relative to the project root", Map.of());
        }
        if (path.length() > MAX_PATH_LENGTH || !PATH_CHARS.matcher(path).matches()) {
            throw invalid("The file path contains invalid characters or is too long", Map.of());
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw invalid("The file path contains empty, '.' or '..' segments", Map.of());
            }
        }
        return path;
    }

    /** Contenido con NUL u otros caracteres de control que no aparecen en código fuente. */
    private static boolean looksBinary(String content) {
        return content.chars().anyMatch(c -> c < 0x20 && c != '\n' && c != '\t' && c != '\f');
    }

    private static int countLines(String content) {
        int lines = (int) content.chars().filter(c -> c == '\n').count();
        return content.endsWith("\n") ? lines : lines + 1;
    }

    private static ApiException invalid(String message, Map<String, Object> details) {
        return new ApiException(ErrorCode.INVALID_REQUEST, message, details);
    }
}
