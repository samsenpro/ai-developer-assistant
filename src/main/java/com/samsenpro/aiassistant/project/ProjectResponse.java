package com.samsenpro.aiassistant.project;

import java.time.Instant;

public record ProjectResponse(Long id, String name, String description, ProgrammingLanguage language,
                              Instant createdAt, Instant updatedAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getLanguage(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
