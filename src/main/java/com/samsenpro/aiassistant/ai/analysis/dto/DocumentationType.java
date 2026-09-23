package com.samsenpro.aiassistant.ai.analysis.dto;

/** Tipo de documentación a generar. */
public enum DocumentationType {
    /** El código con comentarios JavaDoc (o el equivalente del lenguaje: docstrings, JSDoc...). */
    JAVADOC,
    /** Documentación de las clases o módulos. */
    CLASS,
    /** Documentación de cada método o función pública. */
    METHOD,
    README,
    /** Documentación de la API (endpoints, contratos, errores). */
    API,
    /** Explicación de la arquitectura y las responsabilidades. */
    ARCHITECTURE
}
