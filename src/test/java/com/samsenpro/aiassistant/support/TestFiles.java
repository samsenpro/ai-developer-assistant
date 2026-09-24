package com.samsenpro.aiassistant.support;

import com.samsenpro.aiassistant.common.Hashing;
import com.samsenpro.aiassistant.file.FileSnapshot;
import com.samsenpro.aiassistant.file.SourceFileSummary;
import com.samsenpro.aiassistant.project.ProgrammingLanguage;

import java.time.Instant;

/** Archivos de ejemplo para los tests. */
public final class TestFiles {

    public static final String USER_CONTROLLER = """
            package com.shop.user;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class UserController {

                private final UserService userService;

                public UserController(UserService userService) {
                    this.userService = userService;
                }

                @GetMapping("/users/{id}")
                public User get(Long id) {
                    return userService.find(id);
                }
            }
            """;

    public static final String USER_SERVICE = """
            package com.shop.user;

            import org.springframework.stereotype.Service;

            @Service
            public class UserService {

                private final UserRepository userRepository;

                public UserService(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }

                public User find(Long id) {
                    return userRepository.findById(id).orElseThrow();
                }
            }
            """;

    public static final String USER_REPOSITORY = """
            package com.shop.user;

            import java.util.Optional;

            public interface UserRepository {

                Optional<User> findById(Long id);
            }
            """;

    public static final String ORDER_SERVICE = """
            package com.shop.order;

            public class OrderService {

                public void place() {
                }
            }
            """;

    private TestFiles() {
    }

    public static FileSnapshot snapshot(long id, String path, String content) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        ProgrammingLanguage language = ProgrammingLanguage.fromFilename(filename).orElseThrow();
        int lines = (int) content.chars().filter(c -> c == '\n').count() + (content.endsWith("\n") ? 0 : 1);
        return new FileSnapshot(id, path, filename, language, content, lines, Hashing.sha256(content));
    }

    public static SourceFileSummary summary(FileSnapshot file) {
        return new SourceFileSummary(file.id(), 1L, file.path(), file.filename(), file.language(),
                file.content().length(), file.lineCount(), file.contentHash(), Instant.EPOCH, Instant.EPOCH);
    }
}
