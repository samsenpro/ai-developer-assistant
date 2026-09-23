package com.samsenpro.aiassistant.ai.context;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enmascara secretos evidentes antes de enviar contenido a un proveedor externo: claves de API,
 * tokens, claves privadas, contraseñas en literales y credenciales en URLs.
 * <p>
 * Nunca cambia el número de líneas, para que los números de línea que devuelve el modelo sigan
 * correspondiendo al archivo original. No pretende ser un escáner de secretos completo: es una
 * mitigación de fuga de datos, no una garantía (ver docs/ai-security.md).
 */
@Component
public class SecretRedactor {

    public static final String MASK = "[REDACTED]";

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----");

    /** Patrones cuyo match completo es el secreto. */
    private static final List<Pattern> TOKENS = List.of(
            // JWT
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}"),
            // Claves estilo OpenAI / Anthropic
            Pattern.compile("\\bsk-(?:ant-|proj-)?[A-Za-z0-9_-]{20,}"),
            // AWS access key ID
            Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            // Tokens de GitHub
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}"),
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{22,}"),
            // Slack
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}"),
            // Google API key
            Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}"));

    /** usuario:contraseña@ dentro de una URL (cadenas de conexión). Se conserva el usuario. */
    private static final Pattern URL_CREDENTIALS = Pattern.compile(
            "(\\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s:/@\"']+:)([^\\s@/\"']+)(@)");

    /** password = "valor" y similares: solo literales entre comillas, no expresiones de código. */
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b\\w*(?:password|passwd|pwd|secret|api[_-]?key|access[_-]?key|auth[_-]?token|private[_-]?key)\\w*"
                    + "[\"']?\\s*[:=]\\s*)([\"'])([^\"'\\n]{4,})\\2");

    public Redaction redact(String text) {
        if (text == null || text.isEmpty()) {
            return new Redaction(text, 0);
        }
        int[] count = {0};
        String result = replace(PRIVATE_KEY, text, matcher -> {
            count[0]++;
            // Se conservan los saltos de línea del bloque
            String newlines = "\n".repeat((int) matcher.group().chars().filter(c -> c == '\n').count());
            return "[REDACTED PRIVATE KEY]" + newlines;
        });
        for (Pattern token : TOKENS) {
            result = replace(token, result, matcher -> {
                count[0]++;
                return MASK;
            });
        }
        result = replace(URL_CREDENTIALS, result, matcher -> {
            count[0]++;
            return matcher.group(1) + MASK + matcher.group(3);
        });
        result = replace(SECRET_ASSIGNMENT, result, matcher -> {
            if (matcher.group(3).equals(MASK)) {
                return matcher.group();
            }
            count[0]++;
            return matcher.group(1) + matcher.group(2) + MASK + matcher.group(2);
        });
        return new Redaction(result, count[0]);
    }

    private static String replace(Pattern pattern, String text, java.util.function.Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacer.apply(matcher)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Texto enmascarado y número de secretos encontrados. */
    public record Redaction(String text, int count) {
    }
}
