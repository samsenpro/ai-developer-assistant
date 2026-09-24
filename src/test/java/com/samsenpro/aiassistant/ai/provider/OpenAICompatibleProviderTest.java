package com.samsenpro.aiassistant.ai.provider;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.samsenpro.aiassistant.ai.AIOperation;
import com.samsenpro.aiassistant.ai.context.ContextProperties;
import com.samsenpro.aiassistant.ai.context.TokenEstimator;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proveedor real (Spring AI + cliente HTTP + manejo de errores y reintentos de la aplicación)
 * contra una API compatible con OpenAI simulada con WireMock: sin Internet ni coste.
 */
class OpenAICompatibleProviderTest {

    private static final String COMPLETIONS = "/v1/chat/completions";
    private static final AIPrompt PROMPT = new AIPrompt(AIOperation.REVIEW, "review", "1.0.0",
            "You are a reviewer", "Review this code", 800);

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void sendsSystemAndUserMessagesSeparatelyAndReadsContentUsageAndModel() {
        api.stubFor(post(COMPLETIONS).willReturn(okJson(completion("{\\\"summary\\\":\\\"ok\\\"}", "stop",
                "\"usage\": {\"prompt_tokens\": 120, \"completion_tokens\": 30, \"total_tokens\": 150}"))));

        AIResponse response = provider(true).generateStructured(PROMPT);

        assertThat(response.content()).isEqualTo("{\"summary\":\"ok\"}");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(30);
        assertThat(response.tokensEstimated()).isFalse();
        assertThat(response.model()).isEqualTo("gpt-test-2026-01-01");
        assertThat(response.provider()).isEqualTo("openai");
        assertThat(response.finishReason()).isEqualTo("STOP");
        api.verify(postRequestedFor(urlEqualTo(COMPLETIONS))
                .withHeader("Authorization", equalTo("Bearer test-key"))
                .withRequestBody(matchingJsonPath("$.model", equalTo("gpt-test")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[0].content", equalTo("You are a reviewer")))
                .withRequestBody(matchingJsonPath("$.messages[1].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.max_tokens", equalTo("800")))
                .withRequestBody(matchingJsonPath("$.response_format.type", equalTo("json_object"))));
    }

    @Test
    void freeTextGenerationDoesNotRequestJsonMode() {
        api.stubFor(post(COMPLETIONS).willReturn(okJson(completion("Hello", "stop", null))));

        provider(true).generate(PROMPT);

        api.verify(postRequestedFor(urlEqualTo(COMPLETIONS))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("response_format")));
    }

    @Test
    void estimatesTokensWhenTheProviderDoesNotReportUsage() {
        api.stubFor(post(COMPLETIONS).willReturn(okJson(completion("{}", "stop", null))));

        AIResponse response = provider(true).generateStructured(PROMPT);

        assertThat(response.tokensEstimated()).isTrue();
        assertThat(response.inputTokens()).isPositive();
    }

    @Test
    void reportsTruncatedResponses() {
        api.stubFor(post(COMPLETIONS).willReturn(okJson(completion("{\\\"summary\\\": \\\"cut", "length", null))));

        assertThat(provider(true).generateStructured(PROMPT).truncated()).isTrue();
    }

    @Test
    void mapsProviderRateLimitWithRetryAfterAndDoesNotRetry() {
        api.stubFor(post(COMPLETIONS).willReturn(aResponse().withStatus(429).withHeader("Retry-After", "17")
                .withBody("{\"error\": {\"message\": \"Rate limit reached\"}}")));

        assertThatThrownBy(() -> provider(true).generateStructured(PROMPT))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.code()).isEqualTo(ErrorCode.AI_RATE_LIMIT);
                    assertThat(api.retryAfter()).isEqualTo(Duration.ofSeconds(17));
                });
        api.verify(1, postRequestedFor(urlEqualTo(COMPLETIONS)));
    }

    @Test
    void retriesServerErrorsAndThenReportsTheProviderAsUnavailable() {
        api.stubFor(post(COMPLETIONS).willReturn(aResponse().withStatus(503).withBody("overloaded")));

        assertThatThrownBy(() -> provider(true).generateStructured(PROMPT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        // max-attempts = 2
        api.verify(2, postRequestedFor(urlEqualTo(COMPLETIONS)));
    }

    @Test
    void recoversWhenARetryAfterAServerErrorSucceeds() {
        api.stubFor(post(COMPLETIONS).inScenario("flaky").whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500)).willSetStateTo("recovered"));
        api.stubFor(post(COMPLETIONS).inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson(completion("{}", "stop", null))));

        assertThat(provider(true).generateStructured(PROMPT).content()).isEqualTo("{}");
    }

    @Test
    void timesOutWithoutRetrying() {
        api.stubFor(post(COMPLETIONS).willReturn(okJson(completion("{}", "stop", null)).withFixedDelay(1_500)));

        assertThatThrownBy(() -> provider(true).generateStructured(PROMPT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.AI_PROVIDER_TIMEOUT);
        api.verify(1, postRequestedFor(urlEqualTo(COMPLETIONS)));
    }

    @Test
    void invalidApiKeyIsReportedAsProviderUnavailable() {
        api.stubFor(post(COMPLETIONS).willReturn(aResponse().withStatus(401)
                .withBody("{\"error\": {\"message\": \"Incorrect API key provided\"}}")));

        assertThatThrownBy(() -> provider(true).generateStructured(PROMPT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }

    @Test
    void contextLengthErrorsFromTheProviderAreReportedAsContextTooLarge() {
        api.stubFor(post(COMPLETIONS).willReturn(aResponse().withStatus(400)
                .withBody("{\"error\": {\"code\": \"context_length_exceeded\"}}")));

        assertThatThrownBy(() -> provider(true).generateStructured(PROMPT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.CONTEXT_TOO_LARGE);
    }

    @Test
    void unreachableProviderIsReportedAsUnavailable() {
        OpenAICompatibleProvider provider = provider("http://localhost:1", true);

        assertThatThrownBy(() -> provider.generateStructured(PROMPT))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.AI_PROVIDER_UNAVAILABLE);
    }

    private OpenAICompatibleProvider provider(boolean jsonMode) {
        return provider(api.baseUrl(), jsonMode);
    }

    private OpenAICompatibleProvider provider(String baseUrl, boolean jsonMode) {
        AIProviderProperties properties = new AIProviderProperties("openai", null, Duration.ofMillis(800),
                Duration.ofMillis(500), 2, 2, new AIProviderProperties.OpenAi("openai", "gpt-test", 0.2, jsonMode));
        AIProviderConfig config = new AIProviderConfig();
        RestClient.Builder restClient = RestClient.builder();
        config.aiProviderTimeouts(properties).customize(restClient);
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey("test-key")
                .restClientBuilder(restClient)
                .responseErrorHandler(config.responseErrorHandler())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-test").build())
                .retryTemplate(config.retryTemplate(properties))
                .build();
        ContextProperties contextProperties = new ContextProperties(1000, 2000, 4.0, 10, 5, 2, 4, true);
        return new OpenAICompatibleProvider(chatModel, properties, new TokenEstimator(contextProperties));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private static String completion(String content, String finishReason, String usage) {
        return """
                {
                  "id": "chatcmpl-1",
                  "object": "chat.completion",
                  "created": 1,
                  "model": "gpt-test-2026-01-01",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "%s"}, "finish_reason": "%s"}
                  ]%s
                }
                """.formatted(content, finishReason, usage == null ? "" : ", " + usage);
    }
}
