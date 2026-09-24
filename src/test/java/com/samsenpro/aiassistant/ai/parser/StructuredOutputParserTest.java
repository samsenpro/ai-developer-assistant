package com.samsenpro.aiassistant.ai.parser;

import com.samsenpro.aiassistant.ai.analysis.dto.ErrorAnalysisResult;
import com.samsenpro.aiassistant.ai.analysis.dto.IssueCategory;
import com.samsenpro.aiassistant.ai.analysis.dto.ReviewResult;
import com.samsenpro.aiassistant.ai.analysis.dto.Severity;
import com.samsenpro.aiassistant.ai.analysis.dto.TestGenerationResult;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputParserTest {

    private static final String VALID_REVIEW = """
            {
              "summary": "One problem found",
              "issues": [
                {"severity": "HIGH", "category": "SECURITY", "file": "A.java", "line": 3,
                 "description": "SQL injection", "recommendation": "Use parameters"}
              ],
              "recommendations": ["Add tests"]
            }
            """;

    private final StructuredOutputParser parser =
            new StructuredOutputParser(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void parsesAValidResponse() {
        ParsedOutput<ReviewResult> parsed = parser.parse(VALID_REVIEW, ReviewResult.class, false);

        ReviewResult review = parsed.value();
        assertThat(review.summary()).isEqualTo("One problem found");
        assertThat(review.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.HIGH);
            assertThat(issue.category()).isEqualTo(IssueCategory.SECURITY);
            assertThat(issue.line()).isEqualTo(3);
        });
        assertThat(parsed.ignoredFields()).isEmpty();
    }

    @Test
    void extractsJsonFromMarkdownFencesAndSurroundingText() {
        String fenced = "```json\n" + VALID_REVIEW + "\n```";
        String withProse = "Here is the review you asked for:\n" + VALID_REVIEW + "\nHope it helps!";

        assertThat(parser.parse(fenced, ReviewResult.class, false).value().issues()).hasSize(1);
        assertThat(parser.parse(withProse, ReviewResult.class, false).value().issues()).hasSize(1);
    }

    @Test
    void rejectsInvalidJson() {
        assertInvalid("{\"summary\": \"unterminated", InvalidAIResponseException.Reason.INVALID_JSON, false);
        assertInvalid("I cannot review this code.", InvalidAIResponseException.Reason.INVALID_JSON, false);
        assertInvalid("[1, 2, 3]", InvalidAIResponseException.Reason.INVALID_JSON, false);
    }

    @Test
    void reportsTruncatedResponsesSeparately() {
        assertInvalid("{\"summary\": \"The code", InvalidAIResponseException.Reason.TRUNCATED_RESPONSE, true);
    }

    @Test
    void rejectsEmptyResponses() {
        assertInvalid("", InvalidAIResponseException.Reason.EMPTY_RESPONSE, false);
        assertInvalid("   \n ", InvalidAIResponseException.Reason.EMPTY_RESPONSE, false);
        assertInvalid(null, InvalidAIResponseException.Reason.EMPTY_RESPONSE, false);
    }

    @Test
    void rejectsMissingRequiredFieldsAndListsThem() {
        String missing = """
                {"issues": [{"severity": "HIGH", "category": "BUG", "description": "", "recommendation": "Fix"}]}
                """;

        assertThatThrownBy(() -> parser.parse(missing, ReviewResult.class, false))
                .isInstanceOf(InvalidAIResponseException.class)
                .satisfies(ex -> {
                    InvalidAIResponseException invalid = (InvalidAIResponseException) ex;
                    assertThat(invalid.code()).isEqualTo(ErrorCode.INVALID_AI_RESPONSE);
                    assertThat(invalid.reason()).isEqualTo(InvalidAIResponseException.Reason.SCHEMA_VIOLATION);
                    assertThat(invalid.details().get("violations").toString())
                            .contains("summary").contains("issues[0].description");
                });
    }

    @Test
    void rejectsInvalidEnumValuesThatHaveNoSafeDefault() {
        String badSeverity = VALID_REVIEW.replace("\"HIGH\"", "\"SEVERE\"");

        assertInvalid(badSeverity, InvalidAIResponseException.Reason.SCHEMA_VIOLATION, false);
    }

    @Test
    void mapsUnknownCategoriesToOtherInsteadOfDiscardingTheReview() {
        String unknownCategory = VALID_REVIEW.replace("\"SECURITY\"", "\"STYLE\"");

        assertThat(parser.parse(unknownCategory, ReviewResult.class, false).value().issues().getFirst().category())
                .isEqualTo(IssueCategory.OTHER);
    }

    @Test
    void discardsAndReportsUnexpectedFieldsIncludingNestedOnes() {
        String extra = """
                {
                  "summary": "ok",
                  "confidence": 0.9,
                  "issues": [{"severity": "LOW", "category": "BUG", "description": "d", "recommendation": "r",
                              "cwe": "CWE-89"}]
                }
                """;

        ParsedOutput<ReviewResult> parsed = parser.parse(extra, ReviewResult.class, false);

        assertThat(parsed.ignoredFields()).containsExactlyInAnyOrder("confidence", "issues[0].cwe");
        assertThat(parsed.value().issues()).hasSize(1);
    }

    @Test
    void missingOptionalListsBecomeEmptyAndSingleValuesBecomeLists() {
        String minimal = """
                {"probableCause": "The user is probably null", "confidence": "medium",
                 "explanation": "...", "possibleFixes": "Add a null check"}
                """;

        ErrorAnalysisResult result = parser.parse(minimal, ErrorAnalysisResult.class, false).value();

        assertThat(result.possibleFixes()).containsExactly("Add a null check");
        assertThat(result.prevention()).isEmpty();
        assertThat(result.alternativeCauses()).isEmpty();
    }

    @Test
    void requiresAtLeastOneGeneratedTest() {
        String noTests = """
                {"testFramework": "JUnit 5", "tests": []}
                """;

        assertInvalid(noTests, InvalidAIResponseException.Reason.SCHEMA_VIOLATION, false);
        assertThat(parser.parse("""
                {"testFramework": "JUnit 5", "tests": [{"filename": "ATest.java", "content": "class ATest {}"}]}
                """, TestGenerationResult.class, false).value().tests()).hasSize(1);
    }

    private void assertInvalid(String raw, InvalidAIResponseException.Reason reason, boolean truncated) {
        Class<?> type = raw != null && raw.contains("testFramework") ? TestGenerationResult.class : ReviewResult.class;
        assertThatThrownBy(() -> parser.parse(raw, type, truncated))
                .isInstanceOf(InvalidAIResponseException.class)
                .satisfies(ex -> assertThat(((InvalidAIResponseException) ex).reason()).isEqualTo(reason));
    }
}
