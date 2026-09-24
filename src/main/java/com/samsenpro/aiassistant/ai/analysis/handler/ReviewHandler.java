package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.dto.ReviewResult;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
public class ReviewHandler implements OperationHandler<ReviewResult> {

    @Override
    public AIOperation operation() {
        return AIOperation.REVIEW;
    }

    @Override
    public PromptId promptId() {
        return PromptId.REVIEW;
    }

    @Override
    public Class<ReviewResult> resultType() {
        return ReviewResult.class;
    }

    /** Los hallazgos de una revisión son independientes entre sí: se puede revisar por partes. */
    @Override
    public boolean supportsSplit() {
        return true;
    }

    @Override
    public ReviewResult merge(List<ReviewResult> parts) {
        String summary = String.join("\n", IntStream.range(0, parts.size())
                .mapToObj(i -> "Part %d/%d: %s".formatted(i + 1, parts.size(), parts.get(i).summary()))
                .toList());
        List<ReviewResult.Issue> issues = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ReviewResult part : parts) {
            for (ReviewResult.Issue issue : part.issues()) {
                // Un mismo hallazgo puede aparecer en dos partes si el contexto se solapa
                if (seen.add(issue.file() + ":" + issue.line() + ":" + issue.description())) {
                    issues.add(issue);
                }
            }
        }
        List<String> recommendations = parts.stream().flatMap(part -> part.recommendations().stream())
                .distinct().toList();
        return new ReviewResult(summary, issues, recommendations);
    }

    /**
     * La IA no debe inventar ubicaciones: un archivo que no se le envió o una línea fuera del rango
     * real del archivo se sustituyen por null y se avisa al usuario.
     */
    @Override
    public ReviewResult postProcess(ReviewResult result, OperationInput input, List<String> warnings) {
        Map<String, Integer> known = GeneratedCode.knownFiles(input.context());
        int unknownFiles = 0;
        int invalidLines = 0;
        List<ReviewResult.Issue> issues = new ArrayList<>();
        for (ReviewResult.Issue issue : result.issues()) {
            Optional<String> path = GeneratedCode.resolvePath(issue.file(), known);
            Integer line = issue.line();
            if (issue.file() != null && path.isEmpty()) {
                unknownFiles++;
                line = null;
            } else if (line != null && (path.isEmpty() || line < 1 || line > known.get(path.get()))) {
                invalidLines++;
                line = null;
            }
            issues.add(issue.withLocation(path.orElse(null), line));
        }
        if (unknownFiles > 0) {
            warnings.add("%d issue(s) referenced files that were not part of the review; their location was removed."
                    .formatted(unknownFiles));
        }
        if (invalidLines > 0) {
            warnings.add("%d issue(s) referenced line numbers outside the file; the line was set to null."
                    .formatted(invalidLines));
        }
        issues.sort(Comparator.comparing(ReviewResult.Issue::severity).reversed());
        return new ReviewResult(result.summary(), issues, result.recommendations());
    }
}
