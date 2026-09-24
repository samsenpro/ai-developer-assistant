package com.samsenpro.aiassistant.ai.context;

import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.file.SourceFileSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Busca archivos del proyecto relacionados con los seleccionados siguiendo sus referencias:
 * <pre>
 * UserController.java ──usa──▶ UserService.java ──usa──▶ UserRepository.java
 *      (profundidad 0)             (1)                         (2)
 * </pre>
 * Un archivo está relacionado si su nombre sin extensión aparece como palabra completa en el
 * código (tipos en Java/TypeScript, módulos en Python/JavaScript: {@code from user_service import},
 * {@code require('./user-service')}). Es un heurístico léxico, sin compilar ni resolver imports: barato,
 * independiente del lenguaje y suficiente para elegir contexto. Los candidatos se ordenan por
 * cercanía y por número de referencias, y se limitan por configuración.
 */
@Component
public class RelatedFileFinder {

    private static final int MIN_NAME_LENGTH = 3;
    /** Nombres demasiado genéricos: aparecerían como palabra en casi cualquier archivo. */
    private static final Set<String> GENERIC_NAMES = Set.of("index", "main", "app", "test", "tests", "types",
            "utils", "util", "config", "constants", "style", "styles");

    public List<RelatedFile> find(List<FileSnapshot> selected, List<SourceFileSummary> projectFiles,
                                  Function<List<Long>, List<FileSnapshot>> loader, int maxDepth, int maxFiles) {
        if (maxDepth <= 0 || maxFiles <= 0) {
            return List.of();
        }
        Set<Long> visited = selected.stream().map(FileSnapshot::id).collect(Collectors.toCollection(HashSet::new));
        List<RelatedFile> found = new ArrayList<>();
        List<FileSnapshot> frontier = selected;

        for (int depth = 1; depth <= maxDepth && found.size() < maxFiles && !frontier.isEmpty(); depth++) {
            List<Candidate> candidates = new ArrayList<>();
            for (SourceFileSummary file : projectFiles) {
                if (visited.contains(file.id()) || !isReferenceable(file.baseName())) {
                    continue;
                }
                int references = countReferences(frontier, file.baseName());
                if (references > 0) {
                    candidates.add(new Candidate(file, references));
                }
            }
            List<Candidate> chosen = candidates.stream()
                    .sorted(Comparator.comparingInt(Candidate::references).reversed()
                            .thenComparing(candidate -> candidate.file().path()))
                    .limit(maxFiles - found.size())
                    .toList();
            if (chosen.isEmpty()) {
                break;
            }
            Map<Long, Integer> referencesById = chosen.stream()
                    .collect(Collectors.toMap(candidate -> candidate.file().id(), Candidate::references));
            List<FileSnapshot> loaded = loader.apply(chosen.stream().map(candidate -> candidate.file().id()).toList());
            for (FileSnapshot file : loaded) {
                visited.add(file.id());
                found.add(new RelatedFile(file, depth, referencesById.getOrDefault(file.id(), 0)));
            }
            frontier = loaded;
        }
        return found;
    }

    private static boolean isReferenceable(String baseName) {
        return baseName.length() >= MIN_NAME_LENGTH && !GENERIC_NAMES.contains(baseName.toLowerCase());
    }

    private static int countReferences(List<FileSnapshot> files, String baseName) {
        Pattern word = Pattern.compile("(?<![\\w-])" + Pattern.quote(baseName) + "(?![\\w-])");
        int count = 0;
        for (FileSnapshot file : files) {
            if (file.baseName().equals(baseName)) {
                continue;
            }
            Matcher matcher = word.matcher(file.content());
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }

    /** Archivo relacionado, a qué distancia de la selección está y cuántas veces se le referencia. */
    public record RelatedFile(FileSnapshot file, int depth, int references) {
    }

    private record Candidate(SourceFileSummary file, int references) {
    }
}
