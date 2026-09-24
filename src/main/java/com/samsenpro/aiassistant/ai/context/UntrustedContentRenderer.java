package com.samsenpro.aiassistant.ai.context;

import com.samsenpro.aiassistant.common.Hashing;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Da formato a todo contenido que no es del sistema (código, trazas de error, mensajes del
 * usuario) como un bloque de datos delimitado:
 * <pre>
 * &lt;&lt;&lt;FILE id=ctx-3f9a1c2b7d4e path="src/UserService.java" language=JAVA role=PRIMARY content=FULL lines=1-120&gt;&gt;&gt;
 *    1 | package com.shop;
 * &lt;&lt;&lt;END FILE id=ctx-3f9a1c2b7d4e&gt;&gt;&gt;
 * </pre>
 * El {@code id} depende del contenido completo de la petición: quien escribe el código no puede
 * conocerlo de antemano, así que no puede "cerrar" el bloque para escribir instrucciones fuera de
 * él. Además se neutraliza cualquier {@code <<<} del contenido.
 * <p>
 * Es determinista (mismo contenido → mismo prompt), requisito para que la caché por hash funcione.
 */
@Component
public class UntrustedContentRenderer {

    public String boundary(List<String> contents) {
        return "ctx-" + Hashing.sha256(contents.toArray(String[]::new)).substring(0, 12);
    }

    /** Archivo completo o fragmento, con números de línea originales. */
    public String renderFile(String boundary, String path, String language, String role, String contentMode,
                             String content, int firstLine) {
        String[] lines = content.split("\n", -1);
        int lineCount = content.endsWith("\n") ? lines.length - 1 : lines.length;
        int lastLine = firstLine + lineCount - 1;
        int width = Math.max(3, Integer.toString(lastLine).length());
        StringBuilder out = new StringBuilder();
        out.append("<<<FILE id=").append(boundary)
                .append(" path=\"").append(path).append('"')
                .append(" language=").append(language)
                .append(" role=").append(role)
                .append(" content=").append(contentMode)
                .append(" lines=").append(firstLine).append('-').append(lastLine)
                .append(">>>\n");
        for (int i = 0; i < lineCount; i++) {
            out.append(String.format("%" + width + "d | ", firstLine + i)).append(neutralize(lines[i])).append('\n');
        }
        out.append("<<<END FILE id=").append(boundary).append(">>>\n");
        return out.toString();
    }

    /** Resumen estructural: solo algunas líneas, cada una con su número original. */
    public String renderOutline(String boundary, String path, String language, String role, List<CodeOutline.Line> lines,
                                int totalLines) {
        int width = Math.max(3, Integer.toString(totalLines).length());
        StringBuilder out = new StringBuilder();
        out.append("<<<FILE id=").append(boundary)
                .append(" path=\"").append(path).append('"')
                .append(" language=").append(language)
                .append(" role=").append(role)
                .append(" content=OUTLINE lines=1-").append(totalLines)
                .append(">>>\n");
        int previous = 0;
        for (CodeOutline.Line line : lines) {
            if (line.number() > previous + 1) {
                out.append(" ".repeat(width)).append(" | ...\n");
            }
            out.append(String.format("%" + width + "d | ", line.number())).append(neutralize(line.text())).append('\n');
            previous = line.number();
        }
        if (previous < totalLines) {
            out.append(" ".repeat(width)).append(" | ...\n");
        }
        out.append("<<<END FILE id=").append(boundary).append(">>>\n");
        return out.toString();
    }

    /** Texto libre no fiable (traza de error, mensaje del usuario, historial...). */
    public String renderText(String boundary, String label, String text) {
        return "<<<DATA id=" + boundary + " name=" + label + ">>>\n"
                + neutralize(text == null ? "" : text.strip()) + "\n"
                + "<<<END DATA id=" + boundary + ">>>\n";
    }

    private static String neutralize(String text) {
        return text.replace("<<<", "<​<<");
    }
}
