package com.samsenpro.aiassistant.ai.prompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plantilla de prompt versionada, cargada de un archivo de texto con este formato:
 * <pre>
 * # id: review
 * # version: 1.0.0
 * === SYSTEM ===
 * ...
 * === CONTEXT ===
 * ...
 * === OUTPUT FORMAT ===
 * ...
 * </pre>
 * La sección SYSTEM va al mensaje de sistema; el resto, en el orden del archivo y con su título,
 * al mensaje del usuario. Las variables se escriben {@code {{nombre}}}.
 */
public final class PromptTemplate {

    public static final String SYSTEM = "SYSTEM";
    public static final String TASK = "TASK";
    public static final String OUTPUT_FORMAT = "OUTPUT FORMAT";
    private static final List<String> REQUIRED_SECTIONS = List.of(SYSTEM, TASK, OUTPUT_FORMAT);

    private static final Pattern HEADER = Pattern.compile("^#\\s*(\\w+)\\s*:\\s*(.+?)\\s*$");
    private static final Pattern SECTION = Pattern.compile("^===\\s*([A-Z ]+?)\\s*===\\s*$");
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)}}");

    private final String id;
    private final String version;
    private final List<Section> sections;
    private final Set<String> variables;

    private PromptTemplate(String id, String version, List<Section> sections) {
        this.id = id;
        this.version = version;
        this.sections = List.copyOf(sections);
        Set<String> found = new LinkedHashSet<>();
        for (Section section : sections) {
            Matcher matcher = VARIABLE.matcher(section.body());
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
        }
        this.variables = Set.copyOf(found);
    }

    public static PromptTemplate parse(String source, String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        List<Section> sections = new ArrayList<>();
        String current = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            Matcher sectionMatcher = SECTION.matcher(line);
            if (sectionMatcher.matches()) {
                if (current != null) {
                    sections.add(new Section(current, body.toString().strip()));
                }
                current = sectionMatcher.group(1);
                String name = current;
                if (sections.stream().anyMatch(section -> section.name().equals(name))) {
                    throw new IllegalStateException(source + ": duplicate section " + current);
                }
                body.setLength(0);
            } else if (current != null) {
                body.append(line).append('\n');
            } else {
                Matcher header = HEADER.matcher(line);
                if (header.matches()) {
                    headers.put(header.group(1), header.group(2));
                } else if (!line.isBlank()) {
                    throw new IllegalStateException(source + ": unexpected text before the first section");
                }
            }
        }
        if (current != null) {
            sections.add(new Section(current, body.toString().strip()));
        }

        String id = headers.get("id");
        String version = headers.get("version");
        if (id == null || version == null) {
            throw new IllegalStateException(source + ": the '# id:' and '# version:' headers are required");
        }
        for (String required : REQUIRED_SECTIONS) {
            if (sections.stream().noneMatch(section -> section.name().equals(required) && !section.body().isBlank())) {
                throw new IllegalStateException(source + ": missing required section " + required);
            }
        }
        if (!sections.getFirst().name().equals(SYSTEM)) {
            throw new IllegalStateException(source + ": the first section must be " + SYSTEM);
        }
        return new PromptTemplate(id, version, sections);
    }

    /**
     * Sustituye las variables en una sola pasada: si un valor (p. ej. código del usuario) contiene
     * a su vez {@code {{algo}}}, no se interpreta como variable.
     */
    public Rendered render(Map<String, String> values) {
        Set<String> missing = new LinkedHashSet<>(variables);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Prompt " + id + " is missing variables: " + missing);
        }
        String system = null;
        List<String> userParts = new ArrayList<>();
        for (Section section : sections) {
            String rendered = substitute(section.body(), values).strip();
            if (section.name().equals(SYSTEM)) {
                system = rendered;
            } else if (!rendered.isEmpty()) {
                userParts.add("### " + section.name() + "\n" + rendered);
            }
        }
        return new Rendered(system, String.join("\n\n", userParts));
    }

    private static String substitute(String template, Map<String, String> values) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(values.get(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String id() {
        return id;
    }

    public String version() {
        return version;
    }

    public Set<String> variables() {
        return variables;
    }

    public List<String> sectionNames() {
        return sections.stream().map(Section::name).toList();
    }

    public record Rendered(String system, String user) {
    }

    private record Section(String name, String body) {
    }
}
