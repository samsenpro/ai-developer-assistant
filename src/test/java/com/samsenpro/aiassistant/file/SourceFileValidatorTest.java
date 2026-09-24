package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileValidatorTest {

    private final SourceFileValidator validator =
            new SourceFileValidator(new FileProperties(DataSize.ofBytes(200), 10));

    @ParameterizedTest
    @CsvSource({
            "src/UserService.java, JAVA",
            "app/models.py, PYTHON",
            "web/app.jsx, JAVASCRIPT",
            "web/App.tsx, TYPESCRIPT",
            "db/V1__init.sql, SQL",
            "public/index.html, HTML",
            "public/styles.css, CSS"
    })
    void detectsTheLanguageFromTheExtension(String path, ProgrammingLanguage expected) {
        assertThat(validator.validate(path, "content", null).language()).isEqualTo(expected);
    }

    @Test
    void rejectsUnsupportedExtensionsListingTheSupportedOnes() {
        assertThatThrownBy(() -> validator.validate("main.rb", "puts 1", null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(".java").hasMessageContaining(".py");
    }

    @Test
    void rejectsADeclaredLanguageThatDoesNotMatchTheExtension() {
        assertThatThrownBy(() -> validator.validate("A.java", "class A {}", ProgrammingLanguage.PYTHON))
                .hasMessageContaining("does not match");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd.py", "src/../../secret.java", "/abs/A.java", "C:\\code\\A.java",
            "src//A.java", "src/./A.java", "src/A\u0000.java", "src/<script>.js"})
    void rejectsUnsafePaths(String path) {
        assertThatThrownBy(() -> validator.validate(path, "x", null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void normalizesWindowsSeparatorsAndLeadingDotSlash() {
        assertThat(validator.validate("./src\\main\\A.java", "class A {}", null).path()).isEqualTo("src/main/A.java");
    }

    @Test
    void rejectsFilesAboveTheConfiguredSize() {
        assertThatThrownBy(() -> validator.validate("A.java", "a".repeat(201), null))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsEmptyAndBinaryContent() {
        assertThatThrownBy(() -> validator.validate("A.java", "  \n ", null)).hasMessageContaining("empty");
        assertThatThrownBy(() -> validator.validate("A.java", "class A {\u0000}", null)).hasMessageContaining("binary");
    }

    @Test
    void normalizesLineEndingsAndCountsLines() {
        ValidatedFile file = validator.validate("A.java", "line1\r\nline2\r\nline3", null);

        assertThat(file.content()).isEqualTo("line1\nline2\nline3");
        assertThat(file.lineCount()).isEqualTo(3);
        assertThat(file.contentHash()).hasSize(64);
    }
}
