package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.analysis.dto.IssueCategory;
import com.samsenpro.aiassistant.ai.analysis.dto.ReviewResult;
import com.samsenpro.aiassistant.ai.analysis.dto.Severity;
import com.samsenpro.aiassistant.ai.context.BuiltContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewHandlerTest {

    private static final String SERVICE = "src/main/java/com/shop/UserService.java";
    private static final String REPOSITORY = "src/main/java/com/shop/UserRepository.java";

    private final ReviewHandler handler = new ReviewHandler();

    @Test
    void keepsValidLocationsAndResolvesBareFilenames() {
        ReviewResult result = review(issue(SERVICE, 10), issue("UserRepository.java", 3));

        List<String> warnings = new ArrayList<>();
        ReviewResult processed = handler.postProcess(result, input(), warnings);

        assertThat(processed.issues()).extracting(ReviewResult.Issue::file, ReviewResult.Issue::line)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(SERVICE, 10),
                        org.assertj.core.groups.Tuple.tuple(REPOSITORY, 3));
        assertThat(warnings).isEmpty();
    }

    @Test
    void neverReturnsInventedLineNumbers() {
        ReviewResult result = review(issue(SERVICE, 999), issue(SERVICE, 0), issue(null, 5));

        List<String> warnings = new ArrayList<>();
        ReviewResult processed = handler.postProcess(result, input(), warnings);

        assertThat(processed.issues()).extracting(ReviewResult.Issue::line).containsOnlyNulls();
        assertThat(warnings).singleElement().asString().contains("3 issue(s)").contains("line");
    }

    @Test
    void removesTheLocationOfIssuesInFilesThatWereNotReviewed() {
        ReviewResult result = review(issue("src/Invented.java", 4));

        List<String> warnings = new ArrayList<>();
        ReviewResult processed = handler.postProcess(result, input(), warnings);

        assertThat(processed.issues().getFirst().file()).isNull();
        assertThat(processed.issues().getFirst().line()).isNull();
        assertThat(warnings).singleElement().asString().contains("not part of the review");
    }

    @Test
    void sortsIssuesFromMostToLeastSevere() {
        ReviewResult result = new ReviewResult("s", List.of(
                new ReviewResult.Issue(Severity.LOW, IssueCategory.CODE_SMELL, SERVICE, 1, "a", "b"),
                new ReviewResult.Issue(Severity.CRITICAL, IssueCategory.SECURITY, SERVICE, 2, "a", "b"),
                new ReviewResult.Issue(Severity.MEDIUM, IssueCategory.BUG, SERVICE, 3, "a", "b")), List.of());

        assertThat(handler.postProcess(result, input(), new ArrayList<>()).issues())
                .extracting(ReviewResult.Issue::severity)
                .containsExactly(Severity.CRITICAL, Severity.MEDIUM, Severity.LOW);
    }

    @Test
    void mergesPartialReviewsWithoutDuplicates() {
        ReviewResult first = new ReviewResult("first part", List.of(issue(SERVICE, 1), issue(SERVICE, 2)),
                List.of("add tests"));
        ReviewResult second = new ReviewResult("second part", List.of(issue(SERVICE, 2), issue(SERVICE, 30)),
                List.of("add tests", "use records"));

        ReviewResult merged = handler.merge(List.of(first, second));

        assertThat(merged.summary()).contains("Part 1/2: first part").contains("Part 2/2: second part");
        assertThat(merged.issues()).extracting(ReviewResult.Issue::line).containsExactly(1, 2, 30);
        assertThat(merged.recommendations()).containsExactly("add tests", "use records");
    }

    private static OperationInput input() {
        BuiltContext context = new BuiltContext(List.of(), List.of(), Map.of(SERVICE, 40, REPOSITORY, 12), List.of(),
                false, "ctx-000000000000");
        return new OperationInput(null, null, List.of(), context, "ctx-000000000000");
    }

    private static ReviewResult review(ReviewResult.Issue... issues) {
        return new ReviewResult("summary", List.of(issues), List.of());
    }

    private static ReviewResult.Issue issue(String file, Integer line) {
        return new ReviewResult.Issue(Severity.HIGH, IssueCategory.BUG, file, line, "description", "recommendation");
    }
}
