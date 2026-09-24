package com.samsenpro.aiassistant.file;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxSize            tamaño máximo de un archivo (UTF-8)
 * @param maxFilesPerProject número máximo de archivos por proyecto
 */
@Validated
@ConfigurationProperties(prefix = "app.files")
public record FileProperties(@NotNull DataSize maxSize, @Positive int maxFilesPerProject) {
}
