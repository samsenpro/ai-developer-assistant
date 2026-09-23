package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;

import java.time.Instant;

public record SourceFileResponse(Long id, Long projectId, String path, String filename, ProgrammingLanguage language,
                                 int sizeBytes, int lineCount, String contentHash, String content, Instant createdAt,
                                 Instant updatedAt) {

    public static SourceFileResponse from(SourceFile file) {
        return new SourceFileResponse(file.getId(), file.getProjectId(), file.getPath(), file.getFilename(),
                file.getLanguage(), file.getSizeBytes(), file.getLineCount(), file.getContentHash(), file.getContent(),
                file.getCreatedAt(), file.getUpdatedAt());
    }
}
