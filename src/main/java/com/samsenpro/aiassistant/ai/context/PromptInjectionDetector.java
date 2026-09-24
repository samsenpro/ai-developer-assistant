package com.samsenpro.aiassistant.ai.context;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detecta en el contenido analizado texto que parece dirigido a un modelo de IA ("ignore previous
 * instructions", "you are now...", suplantación de roles...).
 * <p>
 * No bloquea: un comentario así puede ser legítimo (p. ej. en este mismo proyecto). La defensa
 * real está en cómo se construye el prompt (el código va delimitado como datos no fiables y fuera
 * del mensaje de sistema) y en que la salida se valida contra un esquema. Esta detección sirve para
 * avisar al usuario, reforzar la instrucción al modelo y contar los casos en una métrica.
 */
@Component
public class PromptInjectionDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("ignore\\s+(?:all\\s+|any\\s+)?(?:the\\s+)?(?:previous|prior|above|earlier|preceding|system)\\s+"
                    + "(?:instructions?|prompts?|rules|messages|directions)"),
            Pattern.compile("disregard\\s+(?:all\\s+|any\\s+)?(?:the\\s+)?(?:previous|prior|above|earlier|system|your)\\s+"
                    + "(?:instructions?|prompts?|rules|guidelines)"),
            Pattern.compile("forget\\s+(?:all\\s+)?(?:your|the|previous)\\s+(?:previous\\s+)?(?:instructions?|rules|prompts?)"),
            Pattern.compile("you\\s+are\\s+now\\s+(?:a|an|the|in)\\b"),
            Pattern.compile("(?:reveal|print|show|output|repeat|leak)\\s+(?:your|the)\\s+"
                    + "(?:system\\s+prompt|hidden\\s+prompt|initial\\s+instructions|instructions)"),
            Pattern.compile("\\b(?:new|updated|override)\\s+(?:system\\s+)?instructions?\\s*:"),
            Pattern.compile("</?\\s*(?:system|instructions?)\\s*>"),
            Pattern.compile("^\\s*(?://|#|\\*|--|<!--)?\\s*(?:system|assistant)\\s*:\\s", Pattern.MULTILINE),
            Pattern.compile("(?:do\\s+not|don't|never)\\s+(?:report|mention|flag|include)\\s+(?:any\\s+|this\\s+)?"
                    + "(?:issues?|vulnerabilit(?:y|ies)|bugs?|problems|findings)"),
            Pattern.compile("\\b(?:ai|llm|language\\s+model|assistant|reviewer)s?\\s*[,:]\\s*(?:you\\s+must|please|always)\\b"));

    /** Números de línea (base 1) con texto sospechoso. */
    public List<Integer> scan(String text) {
        List<Integer> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] split = text.split("\n", -1);
        for (int i = 0; i < split.length; i++) {
            String line = split[i].toLowerCase(java.util.Locale.ROOT);
            for (Pattern pattern : PATTERNS) {
                if (pattern.matcher(line).find()) {
                    lines.add(i + 1);
                    break;
                }
            }
        }
        return lines;
    }
}
