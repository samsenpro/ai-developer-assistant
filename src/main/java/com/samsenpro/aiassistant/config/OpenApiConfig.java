package com.samsenpro.aiassistant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI openApi(@Value("${app.version:1.0.0}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Developer Assistant API")
                        .version(version)
                        .description("""
                                Plataforma para desarrolladores que usa LLMs para explicar, revisar, mejorar, documentar \
                                y generar tests de código fuente, analizar errores y conversar sobre un proyecto.

                                Autenticación: `POST /api/auth/login` devuelve un JWT; envíalo como \
                                `Authorization: Bearer <token>` (botón Authorize).

                                Todas las respuestas tienen la forma `{ "success", "data" | "error", "timestamp" }`. \
                                Las operaciones de IA aceptan `Idempotency-Key` y `Cache-Control: no-cache`.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
