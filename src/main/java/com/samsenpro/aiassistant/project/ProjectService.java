package com.samsenpro.aiassistant.project;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.web.PageRequests;
import com.samsenpro.aiassistant.common.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final Clock clock;

    public ProjectService(ProjectRepository projectRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    @Transactional
    public ProjectResponse create(Long userId, ProjectRequest request) {
        String name = request.name().trim();
        if (projectRepository.existsByOwnerIdAndName(userId, name)) {
            throw new ApiException(ErrorCode.PROJECT_NAME_TAKEN);
        }
        Project project = projectRepository.save(new Project(userId, name, blankToNull(request.description()),
                request.language(), clock.instant()));
        log.info("Project created: id={} userId={}", project.getId(), userId);
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(Long userId, int page, int size) {
        return PageResponse.of(projectRepository.findByOwnerId(userId,
                PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))), ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long userId, Long projectId) {
        return ProjectResponse.from(requireOwned(userId, projectId));
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, ProjectRequest request) {
        Project project = requireOwned(userId, projectId);
        String name = request.name().trim();
        if (projectRepository.existsByOwnerIdAndNameAndIdNot(userId, name, projectId)) {
            throw new ApiException(ErrorCode.PROJECT_NAME_TAKEN);
        }
        project.update(name, blankToNull(request.description()), request.language(), clock.instant());
        return ProjectResponse.from(project);
    }

    /** Borra el proyecto; archivos, conversaciones, análisis y jobs se borran en cascada en la BD. */
    @Transactional
    public void delete(Long userId, Long projectId) {
        projectRepository.delete(requireOwned(userId, projectId));
        log.info("Project deleted: id={} userId={}", projectId, userId);
    }

    /**
     * Punto único de autorización a nivel de recurso para todo lo que cuelga de un proyecto
     * (archivos, conversaciones, análisis, jobs).
     * <p>
     * Un proyecto de otro usuario devuelve UNAUTHORIZED_RESOURCE (403) sin ningún dato del
     * proyecto. Ver docs/ai-security.md para la alternativa de responder 404.
     */
    @Transactional(readOnly = true)
    public Project requireOwned(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));
        if (!project.isOwnedBy(userId)) {
            log.warn("Access denied: userId={} tried to access projectId={}", userId, projectId);
            throw new ApiException(ErrorCode.UNAUTHORIZED_RESOURCE);
        }
        return project;
    }

    @Transactional
    public void touch(Long projectId) {
        projectRepository.findById(projectId).ifPresent(project -> project.touch(clock.instant()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
