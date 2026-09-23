package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param language opcional: si se indica debe coincidir con la extensión del archivo
 */
public record SourceFileRequest(
        @Schema(example = "src/main/java/com/shop/UserService.java")
        @NotBlank @Size(max = 500)
        String path,

        @Schema(example = "package com.shop;\n\npublic class UserService {\n}\n")
        @NotNull
        String content,

        @Schema(example = "JAVA", nullable = true)
        ProgrammingLanguage language) {
}
