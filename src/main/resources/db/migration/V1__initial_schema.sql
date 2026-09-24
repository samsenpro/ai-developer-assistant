-- Esquema inicial de ai-developer-assistant.
-- Las relaciones se modelan con columnas de ID (sin asociaciones JPA) y el borrado en cascada
-- lo resuelve la base de datos.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE projects (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(1000),
    language    VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    -- También sirve de índice para listar los proyectos de un usuario
    CONSTRAINT uk_projects_owner_name UNIQUE (owner_id, name)
);

CREATE TABLE source_files (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    path         VARCHAR(500) NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    language     VARCHAR(20)  NOT NULL,
    content      TEXT         NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    line_count   INTEGER      NOT NULL,
    -- SHA-256 del contenido: permite saber si un archivo cambió sin comparar el texto completo
    content_hash VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_source_files_project_path UNIQUE (project_id, path)
);

CREATE TABLE conversations (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_conversations_project ON conversations (project_id, updated_at DESC);

-- Archivos seleccionados como contexto de una conversación
CREATE TABLE conversation_files (
    conversation_id BIGINT NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    file_id         BIGINT NOT NULL REFERENCES source_files (id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, file_id)
);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    token_estimate  INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, id);

CREATE TABLE analysis_results (
    id                  BIGSERIAL PRIMARY KEY,
    -- NULL solo en un análisis de error que no se asocia a ningún proyecto
    project_id          BIGINT REFERENCES projects (id) ON DELETE CASCADE,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    operation           VARCHAR(40)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    -- Hash de la entrada exacta enviada al modelo (prompt + modelo): detecta resultados repetidos
    input_hash          VARCHAR(64)  NOT NULL,
    idempotency_key     VARCHAR(100),
    -- Hash de la petición del cliente: detecta una Idempotency-Key reutilizada con otro cuerpo
    request_fingerprint VARCHAR(64),
    file_ids            JSONB        NOT NULL,
    -- Qué archivos vio el modelo y cómo (completo, resumen, parcial u omitido): decisión del ContextBuilder
    context_files       JSONB        NOT NULL,
    result              JSONB,
    warnings            JSONB        NOT NULL,
    provider            VARCHAR(50),
    model               VARCHAR(100),
    prompt_version      VARCHAR(20)  NOT NULL,
    cached              BOOLEAN      NOT NULL DEFAULT FALSE,
    source_result_id    BIGINT REFERENCES analysis_results (id) ON DELETE SET NULL,
    input_tokens        INTEGER      NOT NULL DEFAULT 0,
    output_tokens       INTEGER      NOT NULL DEFAULT 0,
    error_code          VARCHAR(50),
    error_message       VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL,
    completed_at        TIMESTAMPTZ
);

-- Búsqueda de resultados reutilizables (caché de nivel 2)
CREATE INDEX idx_analysis_results_cache
    ON analysis_results (user_id, input_hash, completed_at DESC)
    WHERE status = 'COMPLETED';
-- Historial de un proyecto
CREATE INDEX idx_analysis_results_project ON analysis_results (project_id, created_at DESC);
CREATE INDEX idx_analysis_results_user ON analysis_results (user_id, created_at DESC);
-- La unicidad la garantiza la base de datos: dos peticiones concurrentes con la misma clave no
-- pueden crear dos análisis
CREATE UNIQUE INDEX uk_analysis_results_idempotency
    ON analysis_results (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE analysis_jobs (
    id                  UUID PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id          BIGINT REFERENCES projects (id) ON DELETE CASCADE,
    operation           VARCHAR(40)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    request             JSONB        NOT NULL,
    idempotency_key     VARCHAR(100),
    request_fingerprint VARCHAR(64)  NOT NULL,
    result_id           BIGINT REFERENCES analysis_results (id) ON DELETE SET NULL,
    error_code          VARCHAR(50),
    error_message       VARCHAR(500),
    correlation_id      VARCHAR(128),
    created_at          TIMESTAMPTZ  NOT NULL,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_analysis_jobs_user ON analysis_jobs (user_id, created_at DESC);
-- Recuperación de jobs al arrancar y límite de jobs pendientes por usuario
CREATE INDEX idx_analysis_jobs_active
    ON analysis_jobs (status, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE UNIQUE INDEX uk_analysis_jobs_idempotency
    ON analysis_jobs (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Una fila por llamada al proveedor (con éxito o no). No se guarda el prompt: solo metadatos.
CREATE TABLE ai_usage (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Si se borra el proyecto se conserva el consumo (el coste ya se produjo)
    project_id       BIGINT REFERENCES projects (id) ON DELETE SET NULL,
    operation        VARCHAR(40) NOT NULL,
    provider         VARCHAR(50) NOT NULL,
    model            VARCHAR(100),
    input_tokens     INTEGER     NOT NULL,
    output_tokens    INTEGER     NOT NULL,
    total_tokens     INTEGER     NOT NULL,
    tokens_estimated BOOLEAN     NOT NULL,
    duration_ms      BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL,
    error_code       VARCHAR(50),
    created_at       TIMESTAMPTZ NOT NULL
);

-- Cálculo de cuotas diarias y resúmenes de consumo por usuario
CREATE INDEX idx_ai_usage_user_created ON ai_usage (user_id, created_at);
