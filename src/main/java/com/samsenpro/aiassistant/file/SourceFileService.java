package com.samsenpro.aiassistant.file;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.web.PageRequests;
import com.samsenpro.aiassistant.common.web.PageResponse;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import com.samsenpro.aiassistant.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SourceFileService {

    private static final Logger log = LoggerFactory.getLogger(SourceFileService.class);

    private final SourceFileRepository fileRepository;
    private final ProjectService projectService;
    private final SourceFileValidator validator;
    private final FileProperties properties;
    private final Clock clock;

    public SourceFileService(SourceFileRepository fileRepository, ProjectService projectService,
                             SourceFileValidator validator, FileProperties properties, Clock clock) {
        this.fileRepository = fileRepository;
        this.projectService = projectService;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public SourceFileResponse create(Long userId, Long projectId, SourceFileRequest request) {
        projectService.requireOwned(userId, projectId);
        if (fileRepository.countByProjectId(projectId) >= properties.maxFilesPerProject()) {
            throw new ApiException(ErrorCode.PROJECT_FILE_LIMIT_REACHED, ErrorCode.PROJECT_FILE_LIMIT_REACHED.defaultMessage(),
                    Map.of("maxFilesPerProject", properties.maxFilesPerProject()));
        }
        ValidatedFile file = validator.validate(request.path(), request.content(), request.language());
        if (fileRepository.existsByProjectIdAndPath(projectId, file.path())) {
            throw new ApiException(ErrorCode.FILE_PATH_TAKEN, ErrorCode.FILE_PATH_TAKEN.defaultMessage(),
                    Map.of("path", file.path()));
        }
        SourceFile saved = saveUnique(new SourceFile(projectId, file, clock.instant()));
        projectService.touch(projectId);
        log.info("File created: id={} projectId={} bytes={}", saved.getId(), projectId, saved.getSizeBytes());
        return SourceFileResponse.from(saved);
    }

    /** Subida multipart: la ruta es opcional y por defecto es el nombre original del archivo. */
    @Transactional
    public SourceFileResponse upload(Long userId, Long projectId, MultipartFile upload, String path,
                                     ProgrammingLanguage language) {
        if (upload.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "The uploaded file is empty");
        }
        if (upload.getSize() > properties.maxSize().toBytes()) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "The file exceeds the maximum allowed size",
                    Map.of("maxBytes", properties.maxSize().toBytes(), "actualBytes", upload.getSize()));
        }
        String effectivePath = path == null || path.isBlank() ? upload.getOriginalFilename() : path;
        return create(userId, projectId, new SourceFileRequest(effectivePath, decodeUtf8(upload), language));
    }

    @Transactional(readOnly = true)
    public PageResponse<SourceFileSummary> list(Long userId, Long projectId, int page, int size) {
        projectService.requireOwned(userId, projectId);
        return PageResponse.of(fileRepository.findSummariesByProjectId(projectId,
                PageRequests.of(page, size, Sort.by("path"))), Function.identity());
    }

    @Transactional(readOnly = true)
    public SourceFileResponse get(Long userId, Long projectId, Long fileId) {
        projectService.requireOwned(userId, projectId);
        return SourceFileResponse.from(require(projectId, fileId));
    }

    @Transactional
    public SourceFileResponse update(Long userId, Long projectId, Long fileId, SourceFileRequest request) {
        projectService.requireOwned(userId, projectId);
        SourceFile existing = require(projectId, fileId);
        ValidatedFile file = validator.validate(request.path(), request.content(), request.language());
        if (fileRepository.existsByProjectIdAndPathAndIdNot(projectId, file.path(), fileId)) {
            throw new ApiException(ErrorCode.FILE_PATH_TAKEN, ErrorCode.FILE_PATH_TAKEN.defaultMessage(),
                    Map.of("path", file.path()));
        }
        // El contentHash cambia con el contenido: los análisis en caché de la versión anterior ya
        // no se reutilizan (su inputHash es distinto)
        existing.apply(file, clock.instant());
        SourceFile saved = saveUnique(existing);
        projectService.touch(projectId);
        return SourceFileResponse.from(saved);
    }

    @Transactional
    public void delete(Long userId, Long projectId, Long fileId) {
        projectService.requireOwned(userId, projectId);
        fileRepository.delete(require(projectId, fileId));
        projectService.touch(projectId);
    }

    /**
     * Archivos seleccionados para una operación de IA, en el orden pedido. La autorización del
     * proyecto la hace quien llama; aquí se garantiza que todos los archivos son de ese proyecto.
     */
    @Transactional(readOnly = true)
    public List<FileSnapshot> loadSelected(Long projectId, Collection<Long> fileIds) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(fileIds));
        Map<Long, SourceFile> found = fileRepository.findByProjectIdAndIdIn(projectId, ids).stream()
                .collect(Collectors.toMap(SourceFile::getId, Function.identity()));
        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            // Un archivo de otro proyecto (propio o ajeno) es, para este proyecto, un archivo inexistente
            throw new ApiException(ErrorCode.FILE_NOT_FOUND, "Some files do not exist in this project",
                    Map.of("fileIds", missing));
        }
        return ids.stream().map(found::get).map(FileSnapshot::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SourceFileSummary> index(Long projectId) {
        return fileRepository.findAllSummariesByProjectId(projectId);
    }

    private SourceFile require(Long projectId, Long fileId) {
        return fileRepository.findByIdAndProjectId(fileId, projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.FILE_NOT_FOUND));
    }

    private SourceFile saveUnique(SourceFile file) {
        try {
            return fileRepository.saveAndFlush(file);
        } catch (DataIntegrityViolationException ex) {
            // Dos peticiones simultáneas con la misma ruta: decide la restricción UNIQUE
            throw new ApiException(ErrorCode.FILE_PATH_TAKEN, ErrorCode.FILE_PATH_TAKEN.defaultMessage(),
                    Map.of("path", file.getPath()));
        }
    }

    private static String decodeUtf8(MultipartFile upload) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(upload.getBytes()))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "The file must be UTF-8 encoded text");
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "The uploaded file could not be read");
        }
    }
}
