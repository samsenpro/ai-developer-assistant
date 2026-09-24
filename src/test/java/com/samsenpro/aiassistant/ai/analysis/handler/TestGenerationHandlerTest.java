package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.analysis.CodeOperationRequest;
import com.samsenpro.aiassistant.ai.analysis.dto.TestGenerationResult;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.samsenpro.aiassistant.support.TestFiles.USER_SERVICE;
import static com.samsenpro.aiassistant.support.TestFiles.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestGenerationHandlerTest {

    private final FrameworkDetector detector = new FrameworkDetector();
    private final TestGenerationHandler handler = new TestGenerationHandler(detector);
    private final AIOperationCommand command = AIOperationCommand.code(AIOperation.GENERATE_TESTS,
            new CodeOperationRequest(1L, List.of(1L), null, null, null));

    @Test
    void rejectsLanguagesWithoutATestStrategy() {
        FileSnapshot sql = snapshot(1, "schema.sql", "CREATE TABLE a (id INT);");

        assertThatThrownBy(() -> handler.validate(command, List.of(sql)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Java, Python, JavaScript and TypeScript");
    }

    @Test
    void rejectsMixedLanguages() {
        assertThatThrownBy(() -> handler.validate(command, List.of(snapshot(1, "A.java", "class A {}"),
                snapshot(2, "b.py", "def b(): pass"))))
                .hasMessageContaining("single language");
    }

    @Test
    void choosesTheTestStrategyFromTheDetectedFramework() {
        assertThat(detector.detect(ProgrammingLanguage.JAVA, List.of(snapshot(1, "S.java", USER_SERVICE))))
                .satisfies(detection -> {
                    assertThat(detection.framework()).isEqualTo("Spring Boot");
                    assertThat(detection.testFramework()).isEqualTo("JUnit 5");
                    assertThat(detection.mockingLibrary()).isEqualTo("Mockito");
                });
        assertThat(detector.detect(ProgrammingLanguage.PYTHON,
                List.of(snapshot(1, "api.py", "from fastapi import FastAPI\napp = FastAPI()\n"))))
                .satisfies(detection -> {
                    assertThat(detection.framework()).isEqualTo("FastAPI");
                    assertThat(detection.testFramework()).isEqualTo("pytest");
                });
        assertThat(detector.detect(ProgrammingLanguage.JAVASCRIPT,
                List.of(snapshot(1, "app.js", "const express = require('express');\n"))).framework())
                .isEqualTo("Express");
        assertThat(detector.detect(ProgrammingLanguage.TYPESCRIPT,
                List.of(snapshot(1, "Button.tsx", "import React from 'react';\n"))).framework()).isEqualTo("React");
        assertThat(detector.detect(ProgrammingLanguage.TYPESCRIPT,
                List.of(snapshot(1, "sum.ts", "import { describe } from 'vitest';\n"))).testFramework())
                .isEqualTo("Vitest");
        assertThat(detector.detect(ProgrammingLanguage.PYTHON, List.of(snapshot(1, "m.py", "def f(): pass\n"))))
                .satisfies(detection -> {
                    assertThat(detection.framework()).isNull();
                    assertThat(detection.describe(ProgrammingLanguage.PYTHON)).contains("none detected");
                });
    }

    @Test
    void normalizesPlaceholderValuesAndStripsCopiedLineNumbers() {
        TestGenerationResult result = new TestGenerationResult("none detected", "JUnit 5", "None",
                List.of(new TestGenerationResult.GeneratedTest("ATest.java", "  1 | class ATest {}\n", "d")),
                List.of(), List.of());

        TestGenerationResult processed = handler.postProcess(result, null, new ArrayList<>());

        assertThat(processed.detectedFramework()).isNull();
        assertThat(processed.mockingLibrary()).isNull();
        assertThat(processed.tests().getFirst().content()).isEqualTo("class ATest {}\n");
    }
}
