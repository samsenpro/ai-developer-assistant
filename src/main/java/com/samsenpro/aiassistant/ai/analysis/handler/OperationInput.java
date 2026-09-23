package com.samsenpro.aiassistant.ai.analysis.handler;

import com.samsenpro.aiassistant.ai.analysis.AIOperationCommand;
import com.samsenpro.aiassistant.ai.context.BuiltContext;
import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.project.Project;

import java.util.List;

/**
 * Todo lo que un handler necesita para construir sus variables de prompt y validar el resultado.
 *
 * @param project null solo en un análisis de error sin proyecto
 * @param context null si la operación no incluye archivos
 */
public record OperationInput(AIOperationCommand command, Project project, List<FileSnapshot> files,
                             BuiltContext context, String boundary) {
}
