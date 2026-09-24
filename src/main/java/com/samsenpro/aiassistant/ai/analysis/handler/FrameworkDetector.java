package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detecta (por imports y anotaciones) el framework del código y elige la estrategia de tests
 * adecuada. Es una pista para el modelo, que debe confirmarla: el prompt le pide identificar el
 * framework antes de escribir los tests.
 */
@Component
public class FrameworkDetector {

    public Detection detect(ProgrammingLanguage language, List<FileSnapshot> files) {
        String code = String.join("\n", files.stream().map(FileSnapshot::content).toList());
        return switch (language) {
            case JAVA -> java(code);
            case PYTHON -> python(code);
            case JAVASCRIPT, TYPESCRIPT -> javascript(language, code);
            default -> throw new IllegalArgumentException("No test strategy for " + language);
        };
    }

    private static Detection java(String code) {
        String framework = null;
        String extra = "";
        if (contains(code, "org.springframework")) {
            framework = "Spring Boot";
            extra = " For @RestController classes prefer @WebMvcTest with MockMvc and @MockitoBean;"
                    + " for services, plain unit tests with @ExtendWith(MockitoExtension.class).";
        } else if (contains(code, "io.quarkus")) {
            framework = "Quarkus";
        } else if (contains(code, "io.micronaut")) {
            framework = "Micronaut";
        } else if (contains(code, "jakarta.ws.rs") || contains(code, "javax.ws.rs")) {
            framework = "Jakarta REST";
        }
        return new Detection(framework, "JUnit 5", "Mockito",
                "Use JUnit 5 (org.junit.jupiter) with Mockito for dependencies and AssertJ or JUnit assertions." + extra);
    }

    private static Detection python(String code) {
        String framework = null;
        String extra = "";
        if (matches(code, "^\\s*(from|import)\\s+fastapi")) {
            framework = "FastAPI";
            extra = " For endpoints use fastapi.testclient.TestClient.";
        } else if (matches(code, "^\\s*(from|import)\\s+flask")) {
            framework = "Flask";
            extra = " For routes use app.test_client().";
        } else if (matches(code, "^\\s*(from|import)\\s+django")) {
            framework = "Django";
            extra = " Use pytest-django fixtures when models or the ORM are involved.";
        }
        return new Detection(framework, "pytest", "unittest.mock",
                "Use pytest with plain assert statements, fixtures and unittest.mock (patch, MagicMock) for dependencies."
                        + extra);
    }

    private static Detection javascript(ProgrammingLanguage language, String code) {
        boolean typescript = language == ProgrammingLanguage.TYPESCRIPT;
        String tsNote = typescript ? " Write the tests in TypeScript." : "";
        if (contains(code, "from 'vitest'") || contains(code, "from \"vitest\"")) {
            return new Detection(null, "Vitest", "vi.fn / vi.mock",
                    "The code already uses Vitest: use describe/it/expect from vitest and vi.mock for modules." + tsNote);
        }
        if (contains(code, "@nestjs/")) {
            return new Detection("NestJS", "Jest", "jest.fn / @nestjs/testing",
                    "Use Jest with Test.createTestingModule from @nestjs/testing and mocked providers." + tsNote);
        }
        if (contains(code, "from 'react'") || contains(code, "from \"react\"")) {
            return new Detection("React", "Jest", "jest.fn",
                    "Use Jest with React Testing Library (render, screen, userEvent); test behavior, not implementation."
                            + tsNote);
        }
        if (contains(code, "from 'vue'") || contains(code, "from \"vue\"")) {
            return new Detection("Vue", "Vitest", "vi.fn",
                    "Use Vitest with @vue/test-utils (mount) for components." + tsNote);
        }
        if (contains(code, "@angular/")) {
            return new Detection("Angular", "Jasmine", "jasmine.createSpyObj",
                    "Use Jasmine with Angular TestBed and spies for injected services." + tsNote);
        }
        if (contains(code, "require('express')") || contains(code, "from 'express'") || contains(code, "from \"express\"")
                || contains(code, "require(\"express\")")) {
            return new Detection("Express", "Jest", "jest.fn",
                    "Use Jest with supertest for HTTP routes and jest.mock for modules." + tsNote);
        }
        return new Detection(null, "Jest", "jest.fn",
                "Use Jest (describe/it/expect) and jest.mock / jest.fn for dependencies."
                        + (typescript ? " Write the tests in TypeScript (ts-jest)." : ""));
    }

    private static boolean contains(String code, String text) {
        return code.contains(text);
    }

    private static boolean matches(String code, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(code).find();
    }

    /**
     * @param framework      framework de la aplicación, o null si no se detecta ninguno
     * @param testFramework  framework de tests recomendado
     * @param mockingLibrary librería de mocks recomendada
     * @param guidance       instrucciones para el prompt
     */
    public record Detection(String framework, String testFramework, String mockingLibrary, String guidance) {

        public String describe(ProgrammingLanguage language) {
            return "Language: %s%nDetected application framework (heuristic): %s%nRecommended test framework: %s%nRecommended mocking: %s%nGuidance: %s"
                    .formatted(language.displayName(), framework == null ? "none detected" : framework, testFramework,
                            mockingLibrary, guidance);
        }
    }
}
