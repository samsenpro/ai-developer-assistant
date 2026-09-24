package com.samsenpro.aiassistant.ai.context;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.file.SourceFileService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decide qué código se envía al modelo, sin superar el presupuesto de tokens
 * ({@code ai.context.max-source-tokens}):
 * <ol>
 *     <li>los archivos seleccionados por el usuario se incluyen siempre completos;</li>
 *     <li>si sobra presupuesto, se añaden archivos relacionados (dependencias directas y de segundo
 *     nivel), completos si caben, como resumen estructural (firmas) si no, u omitidos;</li>
 *     <li>si los seleccionados no caben y la operación lo admite (revisión), se dividen en varias
 *     partes que se analizan por separado; si no lo admite, CONTEXT_TOO_LARGE.</li>
 * </ol>
 * Todo el contenido pasa antes por el enmascarado de secretos y la detección de prompt injection.
 */
@Component
public class ContextBuilder {

    /** Mismo largo que un boundary real, para que las estimaciones de tokens no cambien al sustituirlo. */
    private static final String PLACEHOLDER = "ctx-############";
    private static final Pattern PLACEHOLDER_MARKER =
            Pattern.compile("(<<<(?:END )?(?:FILE|DATA) id=)" + Pattern.quote(PLACEHOLDER));

    private final SourceFileService fileService;
    private final RelatedFileFinder relatedFileFinder;
    private final CodeOutline codeOutline;
    private final SecretRedactor secretRedactor;
    private final PromptInjectionDetector injectionDetector;
    private final UntrustedContentRenderer renderer;
    private final TokenEstimator tokenEstimator;
    private final ContextProperties properties;

    public ContextBuilder(SourceFileService fileService, RelatedFileFinder relatedFileFinder, CodeOutline codeOutline,
                          SecretRedactor secretRedactor, PromptInjectionDetector injectionDetector,
                          UntrustedContentRenderer renderer, TokenEstimator tokenEstimator,
                          ContextProperties properties) {
        this.fileService = fileService;
        this.relatedFileFinder = relatedFileFinder;
        this.codeOutline = codeOutline;
        this.secretRedactor = secretRedactor;
        this.injectionDetector = injectionDetector;
        this.renderer = renderer;
        this.tokenEstimator = tokenEstimator;
        this.properties = properties;
    }

    /**
     * @param projectId      proyecto (ya autorizado) al que pertenecen los archivos
     * @param selected       archivos elegidos por el usuario
     * @param includeRelated buscar y añadir archivos relacionados
     * @param allowSplit     la operación admite dividir el código en varias partes
     * @param extraTexts     otros textos no fiables del prompt (trazas, preguntas...), que entran en el
     *                       cálculo del boundary
     */
    public BuiltContext build(Long projectId, List<FileSnapshot> selected, boolean includeRelated, boolean allowSplit,
                              List<String> extraTexts) {
        List<String> warnings = new ArrayList<>();
        List<String> boundaryInputs = new ArrayList<>(extraTexts);
        List<BuiltContext.ContextFile> files = new ArrayList<>();
        Map<String, Integer> lineCounts = new LinkedHashMap<>();
        boolean[] injection = {false};

        List<Prepared> primary = selected.stream()
                .map(file -> prepare(file, warnings, boundaryInputs, injection))
                .toList();
        List<String> primaryBlocks = primary.stream()
                .map(prepared -> renderFull(prepared, "PRIMARY"))
                .toList();
        int budget = properties.maxSourceTokens();
        int primaryTokens = primaryBlocks.stream().mapToInt(tokenEstimator::estimate).sum();

        List<String> chunks;
        if (primaryTokens <= budget) {
            StringBuilder source = new StringBuilder();
            primaryBlocks.forEach(block -> source.append(block).append('\n'));
            primary.forEach(prepared -> {
                files.add(new BuiltContext.ContextFile(prepared.file().id(), prepared.file().path(), "PRIMARY", "FULL", 0));
                lineCounts.put(prepared.file().path(), prepared.file().lineCount());
            });
            if (includeRelated) {
                addRelated(projectId, selected, budget - primaryTokens, source, files, lineCounts, warnings,
                        boundaryInputs, injection);
            }
            chunks = List.of(source.toString());
        } else if (allowSplit) {
            chunks = split(primary, files, lineCounts, warnings);
        } else {
            throw new ApiException(ErrorCode.CONTEXT_TOO_LARGE,
                    "The selected files need about %d tokens but the limit is %d. Select fewer or smaller files."
                            .formatted(primaryTokens, budget),
                    Map.of("estimatedTokens", primaryTokens, "maxSourceTokens", budget,
                            "files", primary.stream().collect(Collectors.toMap(
                                    prepared -> prepared.file().path(),
                                    prepared -> tokenEstimator.estimate(renderFull(prepared, "PRIMARY")),
                                    (a, b) -> a, LinkedHashMap::new))));
        }

        // El boundary depende de todo el contenido no fiable de la petición (incluidos los archivos
        // relacionados), así que se calcula al final y sustituye al marcador provisional
        String boundary = renderer.boundary(boundaryInputs);
        List<BuiltContext.Chunk> finalChunks = chunks.stream()
                .map(chunk -> replacePlaceholder(chunk, boundary))
                .map(chunk -> new BuiltContext.Chunk(chunk, tokenEstimator.estimate(chunk)))
                .toList();
        return new BuiltContext(finalChunks, List.copyOf(files), Map.copyOf(lineCounts), List.copyOf(warnings),
                injection[0], boundary);
    }

    /** Boundary para prompts sin archivos (p. ej. un análisis de error sin código asociado). */
    public String boundaryFor(List<String> texts) {
        return renderer.boundary(texts);
    }

    private void addRelated(Long projectId, List<FileSnapshot> selected, int remainingBudget, StringBuilder source,
                            List<BuiltContext.ContextFile> files, Map<String, Integer> lineCounts,
                            List<String> warnings, List<String> boundaryInputs, boolean[] injection) {
        if (properties.maxRelatedFiles() == 0 || properties.relatedDepth() == 0) {
            return;
        }
        int remaining = remainingBudget;
        List<RelatedFileFinder.RelatedFile> related = relatedFileFinder.find(selected, fileService.index(projectId),
                ids -> fileService.loadSelected(projectId, ids), properties.relatedDepth(), properties.maxRelatedFiles());
        for (RelatedFileFinder.RelatedFile candidate : related) {
            Prepared prepared = prepare(candidate.file(), warnings, boundaryInputs, injection);
            String full = renderFull(prepared, "RELATED");
            int fullTokens = tokenEstimator.estimate(full);
            String mode;
            if (fullTokens <= remaining) {
                source.append(full).append('\n');
                remaining -= fullTokens;
                mode = "FULL";
            } else {
                List<CodeOutline.Line> outline = codeOutline.outline(prepared.file().language(), prepared.content());
                String rendered = outline.isEmpty() ? null : renderer.renderOutline(PLACEHOLDER, prepared.file().path(),
                        prepared.file().language().name(), "RELATED", outline, prepared.file().lineCount());
                int outlineTokens = rendered == null ? Integer.MAX_VALUE : tokenEstimator.estimate(rendered);
                if (outlineTokens <= remaining) {
                    source.append(rendered).append('\n');
                    remaining -= outlineTokens;
                    mode = "OUTLINE";
                } else {
                    mode = "OMITTED";
                    warnings.add("Related file %s was not included because of the context limit."
                            .formatted(prepared.file().path()));
                }
            }
            files.add(new BuiltContext.ContextFile(prepared.file().id(), prepared.file().path(), "RELATED", mode,
                    candidate.depth()));
            if (!mode.equals("OMITTED")) {
                lineCounts.put(prepared.file().path(), prepared.file().lineCount());
            }
        }
    }

    /**
     * Estrategia "split": archivos completos cuando caben en una parte; los que no, por rangos de
     * líneas (cortando preferentemente en una línea en blanco) conservando la numeración original.
     */
    private List<String> split(List<Prepared> primary, List<BuiltContext.ContextFile> files,
                               Map<String, Integer> lineCounts, List<String> warnings) {
        int budget = properties.maxSourceTokens();
        List<String> pieces = new ArrayList<>();
        for (Prepared prepared : primary) {
            String full = renderFull(prepared, "PRIMARY");
            lineCounts.put(prepared.file().path(), prepared.file().lineCount());
            if (tokenEstimator.estimate(full) <= budget) {
                pieces.add(full);
                files.add(new BuiltContext.ContextFile(prepared.file().id(), prepared.file().path(), "PRIMARY", "FULL", 0));
            } else {
                pieces.addAll(splitFile(prepared, budget));
                files.add(new BuiltContext.ContextFile(prepared.file().id(), prepared.file().path(), "PRIMARY", "PARTIAL", 0));
            }
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;
        for (String piece : pieces) {
            int tokens = tokenEstimator.estimate(piece);
            if (currentTokens > 0 && currentTokens + tokens > budget) {
                chunks.add(current.toString());
                current.setLength(0);
                currentTokens = 0;
            }
            current.append(piece).append('\n');
            currentTokens += tokens;
        }
        if (currentTokens > 0) {
            chunks.add(current.toString());
        }
        if (chunks.size() > properties.maxChunks()) {
            throw new ApiException(ErrorCode.CONTEXT_TOO_LARGE,
                    "The selected code would need %d parts but at most %d are allowed. Select fewer or smaller files."
                            .formatted(chunks.size(), properties.maxChunks()),
                    Map.of("requiredParts", chunks.size(), "maxParts", properties.maxChunks(),
                            "maxSourceTokens", budget));
        }
        warnings.add("The selected code exceeds the context limit and was analyzed in %d parts; related files were not included."
                .formatted(chunks.size()));
        return chunks;
    }

    private List<String> splitFile(Prepared prepared, int budgetTokens) {
        FileSnapshot file = prepared.file();
        String[] lines = prepared.content().split("\n", -1);
        int lineCount = prepared.content().endsWith("\n") ? lines.length - 1 : lines.length;
        // Margen para la cabecera del bloque y para el prefijo de número de línea de cada línea
        int maxChars = (int) (budgetTokens * properties.charsPerToken() * 0.9);
        int prefix = Math.max(3, Integer.toString(lineCount).length()) + 3;

        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < lineCount) {
            int chars = 0;
            int end = start;
            int lastBlank = -1;
            while (end < lineCount && chars + lines[end].length() + prefix + 1 <= maxChars) {
                chars += lines[end].length() + prefix + 1;
                if (lines[end].isBlank()) {
                    lastBlank = end;
                }
                end++;
            }
            if (end == start) {
                throw new ApiException(ErrorCode.CONTEXT_TOO_LARGE,
                        "Line %d of %s is too long to be analyzed".formatted(start + 1, file.path()),
                        Map.of("file", file.path(), "line", start + 1));
            }
            // Cortar en una línea en blanco si está en la parte final del fragmento
            if (end < lineCount && lastBlank > start + (end - start) * 6 / 10) {
                end = lastBlank + 1;
            }
            String content = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
            pieces.add(renderer.renderFile(PLACEHOLDER, file.path(), file.language().name(), "PRIMARY", "PARTIAL",
                    content, start + 1));
            start = end;
        }
        return pieces;
    }

    private Prepared prepare(FileSnapshot file, List<String> warnings, List<String> boundaryInputs, boolean[] injection) {
        boundaryInputs.add(file.content());
        List<Integer> suspicious = injectionDetector.scan(file.content());
        if (!suspicious.isEmpty()) {
            injection[0] = true;
            warnings.add("%s (line%s %s) contains text that looks like instructions for an AI model. It was treated as data, not as instructions."
                    .formatted(file.path(), suspicious.size() > 1 ? "s" : "",
                            suspicious.stream().limit(5).map(String::valueOf).collect(Collectors.joining(", "))));
        }
        String content = file.content();
        if (properties.redactSecrets()) {
            SecretRedactor.Redaction redaction = secretRedactor.redact(content);
            if (redaction.count() > 0) {
                warnings.add("%d possible secret(s) in %s were masked before sending the code to the AI provider."
                        .formatted(redaction.count(), file.path()));
            }
            content = redaction.text();
        }
        return new Prepared(file, content);
    }

    private String renderFull(Prepared prepared, String role) {
        return renderer.renderFile(PLACEHOLDER, prepared.file().path(), prepared.file().language().name(), role, "FULL",
                prepared.content(), 1);
    }

    private static String replacePlaceholder(String text, String boundary) {
        return PLACEHOLDER_MARKER.matcher(text).replaceAll("$1" + Matcher.quoteReplacement(boundary));
    }

    private record Prepared(FileSnapshot file, String content) {
    }
}
