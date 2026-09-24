package com.samsenpro.aiassistant.ai.context;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resumen estructural de un archivo sin llamar al LLM: imports, declaraciones de tipos y firmas de
 * métodos/funciones, con sus números de línea originales.
 * <p>
 * Es la estrategia "summarize" del ContextBuilder para archivos relacionados que no caben
 * completos: al modelo le basta con saber qué ofrece una dependencia (sus firmas), no cómo lo
 * implementa. Es determinista, gratuito y no añade latencia.
 */
@Component
public class CodeOutline {

    private static final Map<ProgrammingLanguage, List<Pattern>> PATTERNS = Map.of(
            ProgrammingLanguage.JAVA, List.of(
                    Pattern.compile("^\\s*(package|import)\\s"),
                    Pattern.compile("^\\s*@\\w+"),
                    Pattern.compile("^\\s*(public|protected|private|abstract|final|static|sealed|non-sealed|\\s)*"
                            + "(class|interface|enum|record)\\s+\\w+"),
                    // Firmas de métodos y constructores (no llamadas: tienen modificador o tipo delante)
                    Pattern.compile("^\\s*(public|protected|private|static|final|abstract|default|synchronized|\\s)+"
                            + "[\\w<>\\[\\],.? ]*\\w+\\s*\\([^;]*$"),
                    Pattern.compile("^\\s*(public|protected|private)?\\s*[\\w<>\\[\\],.? ]+\\s+\\w+\\s*\\([^)]*\\)\\s*;")),
            ProgrammingLanguage.PYTHON, List.of(
                    Pattern.compile("^\\s*(import|from)\\s"),
                    Pattern.compile("^\\s*@\\w+"),
                    Pattern.compile("^\\s*(class|def|async\\s+def)\\s+\\w+")),
            ProgrammingLanguage.JAVASCRIPT, jsPatterns(),
            ProgrammingLanguage.TYPESCRIPT, jsPatterns(),
            ProgrammingLanguage.SQL, List.of(
                    Pattern.compile("(?i)^\\s*(create|alter|drop)\\s+(or\\s+replace\\s+)?"
                            + "(table|view|index|unique\\s+index|function|procedure|trigger|sequence|type|schema)")),
            ProgrammingLanguage.HTML, List.of(
                    Pattern.compile("(?i)<(form|script|link|section|main|nav|header|footer|table|template)\\b"),
                    Pattern.compile("(?i)\\sid=\"[^\"]+\"")),
            ProgrammingLanguage.CSS, List.of(
                    Pattern.compile("^\\s*[^\\s{][^{]*\\{\\s*$"),
                    Pattern.compile("^\\s*@(media|import|keyframes|font-face|layer)")));

    private static List<Pattern> jsPatterns() {
        return List.of(
                Pattern.compile("^\\s*(import|export)\\s"),
                Pattern.compile("^\\s*(export\\s+)?(default\\s+)?(abstract\\s+)?(async\\s+)?"
                        + "(function\\*?|class|interface|type|enum)\\s+\\w+"),
                Pattern.compile("^\\s*(export\\s+)?(const|let)\\s+\\w+\\s*=\\s*(async\\s+)?(\\([^)]*\\)|\\w+)\\s*=>"),
                Pattern.compile("^\\s*(public|private|protected|static|async|get|set|\\s)*\\w+\\s*\\([^)]*\\)\\s*(:\\s*[^{]+)?\\{\\s*$"),
                Pattern.compile("(require\\(|module\\.exports)"));
    }

    /**
     * @return las líneas relevantes como pares (número de línea, texto), en orden
     */
    public List<Line> outline(ProgrammingLanguage language, String content) {
        List<Pattern> patterns = PATTERNS.getOrDefault(language, List.of());
        String[] lines = content.split("\n", -1);
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            for (Pattern pattern : patterns) {
                if (pattern.matcher(line).find()) {
                    result.add(new Line(i + 1, line.stripTrailing()));
                    break;
                }
            }
        }
        return result;
    }

    public record Line(int number, String text) {
    }
}
