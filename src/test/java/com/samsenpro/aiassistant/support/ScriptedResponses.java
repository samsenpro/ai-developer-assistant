package com.samsenpro.aiassistant.support;

import com.samsenpro.aiassistant.ai.provider.AIPrompt;

/** Respuestas válidas por defecto del proveedor simulado, según la plantilla del prompt. */
public final class ScriptedResponses {

    public static final String REVIEW = """
            {
              "summary": "The service builds SQL by concatenation.",
              "issues": [
                {"severity": "HIGH", "category": "SECURITY", "file": "src/main/java/com/shop/UserService.java",
                 "line": 5, "description": "SQL injection risk", "recommendation": "Use a parameterized query"},
                {"severity": "LOW", "category": "CODE_SMELL", "file": "src/main/java/com/shop/UserService.java",
                 "line": 9999, "description": "Invented line", "recommendation": "n/a"}
              ],
              "recommendations": ["Add unit tests"]
            }
            """;

    public static final String EXPLAIN = """
            {"summary": "Service for users", "purpose": "Manage users",
             "structure": [{"name": "UserService", "kind": "CLASS", "description": "Business logic"}],
             "responsibilities": ["Find users"], "flow": ["Receive email", "Query database"],
             "dependencies": [{"name": "UserRepository", "type": "INTERNAL", "usage": "Persistence"}],
             "keyPoints": ["Uses raw SQL"]}
            """;

    public static final String IMPROVE = """
            {"originalAnalysis": "Readable but insecure",
             "suggestedChanges": [{"goal": "SECURITY", "file": "UserService.java", "description": "Use parameters",
                                   "rationale": "Prevents SQL injection"}],
             "improvedCode": [{"file": "UserService.java", "content": "  1 | class UserService {}"}],
             "explanation": "Safer query"}
            """;

    public static final String TESTS = """
            {"detectedFramework": "none", "testFramework": "JUnit 5", "mockingLibrary": "Mockito",
             "tests": [{"filename": "UserServiceTest.java", "content": "class UserServiceTest {}", "description": "d"}],
             "testedBehaviors": ["finds a user"], "assumptions": []}
            """;

    public static final String DOCUMENTATION = """
            {"title": "UserService docs", "summary": "Documented the service",
             "documents": [{"file": "UserService.java", "format": "java", "content": "/** Users */ class UserService {}"}]}
            """;

    public static final String ERROR_ANALYSIS = """
            {"probableCause": "The user is probably null", "confidence": "MEDIUM",
             "explanation": "findByEmail likely returns null", "possibleFixes": ["Check for null"],
             "prevention": ["Return Optional"], "alternativeCauses": []}
            """;

    public static final String CHAT = "The service uses the repository to load users from the database.";

    private ScriptedResponses() {
    }

    public static String forPrompt(AIPrompt prompt) {
        return switch (prompt.promptId()) {
            case "review" -> REVIEW;
            case "explain" -> EXPLAIN;
            case "improve" -> IMPROVE;
            case "tests" -> TESTS;
            case "documentation" -> DOCUMENTATION;
            case "error-analysis" -> ERROR_ANALYSIS;
            default -> CHAT;
        };
    }
}
