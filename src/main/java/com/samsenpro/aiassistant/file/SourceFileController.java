package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.web.ApiResponse;
import com.samsenpro.aiassistant.common.web.PageResponse;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/files")
@Tag(name = "Files", description = "Archivos de código de un proyecto (el contenido se guarda en PostgreSQL)")
public class SourceFileController {

    private final SourceFileService fileService;

    public SourceFileController(SourceFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un archivo (JSON)",
            description = "Extensiones soportadas: Java, Python, JavaScript, TypeScript, SQL, HTML y CSS. "
                    + "El tamaño máximo se configura con FILE_MAX_SIZE.")
    public ApiResponse<SourceFileResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long projectId,
                                                  @Valid @RequestBody SourceFileRequest request) {
        return ApiResponse.ok(fileService.create(user.id(), projectId, request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir un archivo (multipart)",
            description = "Campo 'file' con el archivo UTF-8; 'path' opcional (por defecto, el nombre del archivo).")
    public ApiResponse<SourceFileResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long projectId,
                                                  @RequestPart("file") MultipartFile file,
                                                  @RequestParam(required = false) String path,
                                                  @RequestParam(required = false) ProgrammingLanguage language) {
        return ApiResponse.ok(fileService.upload(user.id(), projectId, file, path, language));
    }

    @GetMapping
    @Operation(summary = "Listar los archivos de un proyecto", description = "Sin el contenido, ordenados por ruta.")
    public ApiResponse<PageResponse<SourceFileSummary>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                             @PathVariable Long projectId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(fileService.list(user.id(), projectId, page, size));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "Obtener un archivo con su contenido")
    public ApiResponse<SourceFileResponse> get(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable Long projectId, @PathVariable Long fileId) {
        return ApiResponse.ok(fileService.get(user.id(), projectId, fileId));
    }

    @PutMapping("/{fileId}")
    @Operation(summary = "Reemplazar un archivo")
    public ApiResponse<SourceFileResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long projectId, @PathVariable Long fileId,
                                                  @Valid @RequestBody SourceFileRequest request) {
        return ApiResponse.ok(fileService.update(user.id(), projectId, fileId, request));
    }

    @DeleteMapping("/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar un archivo")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user,
                       @PathVariable Long projectId, @PathVariable Long fileId) {
        fileService.delete(user.id(), projectId, fileId);
    }
}
