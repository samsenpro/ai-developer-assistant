package com.samsenpro.aiassistant.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SourceFileRepository extends JpaRepository<SourceFile, Long> {

    /** Listado sin el contenido: no se cargan en memoria cientos de KB para pintar una lista. */
    @Query("""
            select new com.samsenpro.aiassistant.file.SourceFileSummary(
                f.id, f.projectId, f.path, f.filename, f.language, f.sizeBytes, f.lineCount, f.contentHash,
                f.createdAt, f.updatedAt)
            from SourceFile f where f.projectId = :projectId""")
    Page<SourceFileSummary> findSummariesByProjectId(Long projectId, Pageable pageable);

    /** Índice ligero de todos los archivos del proyecto para buscar archivos relacionados. */
    @Query("""
            select new com.samsenpro.aiassistant.file.SourceFileSummary(
                f.id, f.projectId, f.path, f.filename, f.language, f.sizeBytes, f.lineCount, f.contentHash,
                f.createdAt, f.updatedAt)
            from SourceFile f where f.projectId = :projectId order by f.path""")
    List<SourceFileSummary> findAllSummariesByProjectId(Long projectId);

    Optional<SourceFile> findByIdAndProjectId(Long id, Long projectId);

    List<SourceFile> findByProjectIdAndIdIn(Long projectId, Collection<Long> ids);

    boolean existsByProjectIdAndPath(Long projectId, String path);

    boolean existsByProjectIdAndPathAndIdNot(Long projectId, String path, Long id);

    long countByProjectId(Long projectId);
}
