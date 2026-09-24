package com.samsenpro.aiassistant.support;

import com.samsenpro.aiassistant.ai.provider.AIPrompt;
import com.samsenpro.aiassistant.ai.provider.AIProvider;
import com.samsenpro.aiassistant.ai.provider.AIResponse;
import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Proveedor LLM programable para tests: sin red, sin coste y determinista. Cada llamada consume la
 * siguiente respuesta programada; si no queda ninguna, usa la respuesta por defecto.
 * <p>
 * Permite simular todos los casos que exige el contrato: respuesta correcta, JSON inválido,
 * respuesta vacía, timeout, rate limit y proveedor no disponible.
 */
public class FakeAIProvider implements AIProvider {

    private final String name;
    private final ConcurrentLinkedDeque<Function<AIPrompt, AIResponse>> script = new ConcurrentLinkedDeque<>();
    private final List<AIPrompt> prompts = new CopyOnWriteArrayList<>();
    private volatile Function<AIPrompt, AIResponse> defaultBehavior;

    public FakeAIProvider(String name) {
        this.name = name;
        this.defaultBehavior = prompt -> response("{}");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String model() {
        return name + "-model";
    }

    @Override
    public AIResponse generate(AIPrompt prompt) {
        return next(prompt);
    }

    @Override
    public AIResponse generateStructured(AIPrompt prompt) {
        return next(prompt);
    }

    private AIResponse next(AIPrompt prompt) {
        prompts.add(prompt);
        Function<AIPrompt, AIResponse> behavior = script.pollFirst();
        return (behavior == null ? defaultBehavior : behavior).apply(prompt);
    }

    // --- Programación ---

    public FakeAIProvider thenReturn(String content) {
        script.addLast(prompt -> response(content));
        return this;
    }

    public FakeAIProvider thenReturn(AIResponse response) {
        script.addLast(prompt -> response);
        return this;
    }

    public FakeAIProvider thenFail(ErrorCode code) {
        script.addLast(prompt -> {
            throw failure(code);
        });
        return this;
    }

    public FakeAIProvider thenThrow(RuntimeException exception) {
        script.addLast(prompt -> {
            throw exception;
        });
        return this;
    }

    public void byDefault(Function<AIPrompt, AIResponse> behavior) {
        this.defaultBehavior = behavior;
    }

    public void reset() {
        script.clear();
        prompts.clear();
        defaultBehavior = prompt -> response("{}");
    }

    public List<AIPrompt> prompts() {
        return List.copyOf(prompts);
    }

    public int calls() {
        return prompts.size();
    }

    public AIResponse response(String content) {
        return new AIResponse(content, name, model(), 100, 50, false, "STOP");
    }

    public static ApiException failure(ErrorCode code) {
        return switch (code) {
            case AI_PROVIDER_TIMEOUT -> new ApiException(code, code.defaultMessage());
            case AI_RATE_LIMIT -> new ApiException(code, "The AI provider is rate limiting requests, try again later",
                    Map.of("source", "PROVIDER"), Duration.ofSeconds(20), null);
            default -> new ApiException(code, code.defaultMessage());
        };
    }
}
