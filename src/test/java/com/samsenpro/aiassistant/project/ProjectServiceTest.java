package com.samsenpro.aiassistant.project;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Autorización a nivel de recurso: un usuario nunca opera sobre el proyecto de otro. */
class ProjectServiceTest {

    private static final long OWNER = 1L;
    private static final long OTHER_USER = 2L;

    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final ProjectService service = new ProjectService(repository, Clock.systemUTC());
    private final Project project = new Project(OWNER, "shop", null, ProgrammingLanguage.JAVA, Instant.now());

    @Test
    void theOwnerCanAccessTheProject() {
        when(repository.findById(10L)).thenReturn(Optional.of(project));

        assertThat(service.requireOwned(OWNER, 10L)).isSameAs(project);
    }

    @Test
    void anotherUserGetsUnauthorizedResourceWithoutProjectData() {
        when(repository.findById(10L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.get(OTHER_USER, 10L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.UNAUTHORIZED_RESOURCE);
                    assertThat(ex.getMessage()).doesNotContain("shop");
                });
    }

    @Test
    void anotherUserCannotUpdateOrDeleteTheProject() {
        when(repository.findById(10L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.update(OTHER_USER, 10L,
                new ProjectRequest("hacked", null, ProgrammingLanguage.JAVA)))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.UNAUTHORIZED_RESOURCE);
        assertThatThrownBy(() -> service.delete(OTHER_USER, 10L))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.UNAUTHORIZED_RESOURCE);
        verify(repository, never()).delete(any());
        assertThat(project.getName()).isEqualTo("shop");
    }

    @Test
    void aMissingProjectIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwned(OWNER, 99L))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
    }

    @Test
    void projectNamesAreUniquePerOwner() {
        when(repository.existsByOwnerIdAndName(OWNER, "shop")).thenReturn(true);

        assertThatThrownBy(() -> service.create(OWNER, new ProjectRequest(" shop ", null, ProgrammingLanguage.JAVA)))
                .extracting(ex -> ((ApiException) ex).code()).isEqualTo(ErrorCode.PROJECT_NAME_TAKEN);
    }
}
