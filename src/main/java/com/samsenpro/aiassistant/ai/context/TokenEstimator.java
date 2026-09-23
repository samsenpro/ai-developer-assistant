package com.samsenpro.aiassistant.ai.context;

import org.springframework.stereotype.Component;

/**
 * Estimación de tokens independiente del proveedor: caracteres / chars-per-token, redondeando hacia
 * arriba. Cada modelo tiene su tokenizador; un tokenizador exacto ataría la aplicación a un
 * proveedor, y para decidir si algo cabe basta con una estimación conservadora (el código tiene
 * más símbolos que la prosa y produce más tokens por carácter).
 */
@Component
public class TokenEstimator {

    private final double charsPerToken;

    public TokenEstimator(ContextProperties properties) {
        this.charsPerToken = properties.charsPerToken();
    }

    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / charsPerToken);
    }
}
