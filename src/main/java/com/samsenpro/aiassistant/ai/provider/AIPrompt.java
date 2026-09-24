package com.samsenpro.aiassistant.ai.provider;

import com.samsenpro.aiassistant.ai.AIOperation;

/**
 * Petición al proveedor ya construida. Las instrucciones del sistema y el contenido del usuario
 * van siempre en mensajes separados: el código analizado nunca forma parte del mensaje de sistema.
 *
 * @param promptVersion  versión de la plantilla (forma parte del hash de caché)
 * @param maxOutputTokens límite de tokens de salida que se pide al proveedor
 */
public record AIPrompt(AIOperation operation, String promptId, String promptVersion, String system, String user,
                       int maxOutputTokens) {

    @Override
    public String toString() {
        // El contenido puede incluir código del usuario: no debe acabar en logs por accidente
        return "AIPrompt[operation=" + operation + ", promptId=" + promptId + ", version=" + promptVersion
                + ", systemChars=" + system.length() + ", userChars=" + user.length()
                + ", maxOutputTokens=" + maxOutputTokens + "]";
    }
}
