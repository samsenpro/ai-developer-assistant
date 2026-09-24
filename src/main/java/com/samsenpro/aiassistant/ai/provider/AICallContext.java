package com.samsenpro.aiassistant.ai.provider;

import com.samsenpro.aiassistant.ai.AIOperation;

/** Quién y para qué se llama al proveedor: se usa para registrar el consumo. */
public record AICallContext(Long userId, Long projectId, AIOperation operation) {
}
