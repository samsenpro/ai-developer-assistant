package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.dto.TestGenerationResult;
import com.samsenpro.aiassistant.ai.prompt.PromptId;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TestGenerationHandler implements OperationHandler<TestGenerationResult> {

    private final FrameworkDetector frameworkDetector;

    public TestGenerationHandler(FrameworkDetector frameworkDetector) {
        this.frameworkDetector = frameworkDetector;
    }

    @Override
    public AIOperation operation() {
        return AIOperation.GENERATE_TESTS;
    }

    @Override
    public PromptId promptId() {
        return PromptId.TESTS;
    }

    @Override
    public Class<TestGenerationResult> resultType() {
        return TestGenerationResult.class;
    }

    /** Solo lenguajes con una estrategia de tests definida, y uno por petición. */
    @Override
    public void validate(AIOperationCommand command, List<FileSnapshot> files) {
        Set<ProgrammingLanguage> languages = files.stream().map(FileSnapshot::language).collect(Collectors.toSet());
        if (languages.size() > 1) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Select files of a single language to generate tests", Map.of("languages", languages));
        }
        ProgrammingLanguage language = languages.iterator().next();
        if (!language.supportsTestGeneration()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Test generation is supported for Java, Python, JavaScript and TypeScript",
                    Map.of("language", language));
        }
    }

    @Override
    public Map<String, String> variables(OperationInput input) {
        ProgrammingLanguage language = input.files().getFirst().language();
        return Map.of("testStrategy", frameworkDetector.detect(language, input.files()).describe(language));
    }

    @Override
    public TestGenerationResult postProcess(TestGenerationResult result, OperationInput input, List<String> warnings) {
        List<TestGenerationResult.GeneratedTest> tests = result.tests().stream()
                .map(test -> new TestGenerationResult.GeneratedTest(test.filename(),
                        GeneratedCode.cleanCode(test.content()), test.description()))
                .toList();
        return new TestGenerationResult(noneToNull(result.detectedFramework()), result.testFramework(),
                noneToNull(result.mockingLibrary()), tests, result.testedBehaviors(), result.assumptions());
    }

    /** Los modelos a veces devuelven "none" o "none detected" en lugar de null. */
    private static String noneToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip().toLowerCase();
        return normalized.isEmpty() || normalized.equals("none") || normalized.equals("none detected")
                || normalized.equals("null") || normalized.equals("n/a") ? null : value.strip();
    }
}
