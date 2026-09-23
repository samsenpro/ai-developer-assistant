package com.samsenpro.aiassistant.project;

import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects", description = "Proyectos del usuario autenticado")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un proyecto")
    public ApiResponse<ProjectResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.create(user.id(), request));
    }

    @GetMapping
    @Operation(summary = "Listar mis proyectos", description = "Paginado, del más reciente al más antiguo.")
    public ApiResponse<PageResponse<ProjectResponse>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(projectService.list(user.id(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un proyecto")
    public ApiResponse<ProjectResponse> get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ApiResponse.ok(projectService.get(user.id(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un proyecto")
    public ApiResponse<ProjectResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                               @Valid @RequestBody ProjectRequest request) {
        return ApiResponse.ok(projectService.update(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar un proyecto", description = "Borra también sus archivos, conversaciones, análisis y jobs.")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        projectService.delete(user.id(), id);
    }
}
