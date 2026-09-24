package com.samsenpro.aiassistant.ai.parser;

import java.util.List;

/**
 * Resultado validado del modelo.
 *
 * @param ignoredFields campos que el modelo devolvió pero que no forman parte del esquema; se
 *                      descartan (no llegan al cliente) y se informan como aviso
 */
public record ParsedOutput<T>(T value, List<String> ignoredFields) {
}
