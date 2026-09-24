package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.dto.DocumentationResult;
import com.samsenpro.aiassistant.ai.analysis.dto.DocumentationType;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.file.FileSnapshot;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DocumentationHandler implements OperationHandler<DocumentationResult> {

    /** Instrucciones concretas por tipo; el resto del prompt está en documentation.txt. */
    private static final Map<DocumentationType, String> GUIDELINES = new EnumMap<>(Map.of(
            DocumentationType.JAVADOC,
            "Return one document per PRIMARY file with the complete original code plus documentation comments in the "
                    + "language convention (JavaDoc for Java, docstrings for Python, JSDoc/TSDoc for JavaScript/TypeScript). "
                    + "Document classes and public methods: purpose, parameters, return value and exceptions. "
                    + "Do not change the code itself. Use the source language as the format.",
            DocumentationType.CLASS,
            "Return one Markdown document per PRIMARY file describing each class or module: responsibility, "
                    + "collaborators, public API and important invariants.",
            DocumentationType.METHOD,
            "Return one Markdown document per PRIMARY file with a section per public method or function: signature, "
                    + "purpose, parameters, return value, errors and an example of use when helpful.",
            DocumentationType.README,
            "Return a single Markdown README document (file null) for the code provided: overview, features, how the "
                    + "main components fit together and how to use them. Do not invent installation steps or configuration.",
            DocumentationType.API,
            "Return a single Markdown document (file null) describing the API exposed by the code (for example HTTP "
                    + "endpoints or public functions): method and path or signature, request and response shapes, "
                    + "status codes and errors. Only include what is present in the code.",
            DocumentationType.ARCHITECTURE,
            "Return a single Markdown document (file null) explaining the architecture: layers, responsibilities, "
                    + "main flows and design decisions visible in the code. A Mermaid diagram is welcome."));

    @Override
    public AIOperation operation() {
        return AIOperation.GENERATE_DOCUMENTATION;
    }

    @Override
    public PromptId promptId() {
        return PromptId.DOCUMENTATION;
    }

    @Override
    public Class<DocumentationResult> resultType() {
        return DocumentationResult.class;
    }

    @Override
    public void validate(AIOperationCommand command, List<FileSnapshot> files) {
        if (command.documentationType() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "The documentation type is required");
        }
    }

    @Override
    public Map<String, String> variables(OperationInput input) {
        DocumentationType type = input.command().documentationType();
        return Map.of("documentationType", type.name(), "documentationGuidelines", GUIDELINES.get(type));
    }

    @Override
    public DocumentationResult postProcess(DocumentationResult result, OperationInput input, List<String> warnings) {
        Map<String, Integer> known = GeneratedCode.knownFiles(input.context());
        List<DocumentationResult.Document> documents = result.documents().stream()
                .map(document -> new DocumentationResult.Document(
                        document.file() == null ? null
                                : GeneratedCode.resolvePath(document.file(), known).orElse(document.file()),
                        document.format(), "markdown".equalsIgnoreCase(document.format())
                                ? GeneratedCode.stripLineNumbers(document.content())
                                : GeneratedCode.cleanCode(document.content())))
                .toList();
        return new DocumentationResult(result.title(), result.summary(), documents);
    }
}
