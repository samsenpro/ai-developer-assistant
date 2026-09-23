package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Archivo de código de un proyecto. En esta versión el contenido vive en PostgreSQL (no en disco):
 * los archivos son pequeños (limitados por configuración) y así se borran junto con el proyecto.
 */
@Entity
@Table(name = "source_files")
public class SourceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgrammingLanguage language;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SourceFile() {
    }

    public SourceFile(Long projectId, ValidatedFile file, Instant now) {
        this.projectId = projectId;
        this.createdAt = now;
        apply(file, now);
    }

    public void apply(ValidatedFile file, Instant now) {
        this.path = file.path();
        this.filename = file.filename();
        this.language = file.language();
        this.content = file.content();
        this.sizeBytes = file.sizeBytes();
        this.lineCount = file.lineCount();
        this.contentHash = file.contentHash();
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getPath() {
        return path;
    }

    public String getFilename() {
        return filename;
    }

    public ProgrammingLanguage getLanguage() {
        return language;
    }

    public String getContent() {
        return content;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public int getLineCount() {
        return lineCount;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
