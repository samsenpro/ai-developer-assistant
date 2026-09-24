package com.samsenpro.aiassistant.ai.context;

import com.samsenpro.aiassistant.file.FileSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.samsenpro.aiassistant.support.TestFiles.USER_CONTROLLER;
import static com.samsenpro.aiassistant.support.TestFiles.USER_REPOSITORY;
import static com.samsenpro.aiassistant.support.TestFiles.USER_SERVICE;
import static com.samsenpro.aiassistant.support.TestFiles.snapshot;
import static com.samsenpro.aiassistant.support.TestFiles.summary;
import static org.assertj.core.api.Assertions.assertThat;

class RelatedFileFinderTest {

    private final RelatedFileFinder finder = new RelatedFileFinder();

    @Test
    void respectsDepthAndMaximumNumberOfFiles() {
        FileSnapshot controller = snapshot(1, "UserController.java", USER_CONTROLLER);
        FileSnapshot service = snapshot(2, "UserService.java", USER_SERVICE);
        FileSnapshot repository = snapshot(3, "UserRepository.java", USER_REPOSITORY);
        List<FileSnapshot> all = List.of(controller, service, repository);

        assertThat(find(controller, all, 1, 5)).extracting(related -> related.file().filename())
                .containsExactly("UserService.java");
        assertThat(find(controller, all, 2, 5)).extracting(RelatedFileFinder.RelatedFile::depth)
                .containsExactly(1, 2);
        assertThat(find(controller, all, 2, 1)).hasSize(1);
        assertThat(find(controller, all, 0, 5)).isEmpty();
    }

    @Test
    void understandsPythonAndJavaScriptModuleReferences() {
        FileSnapshot routes = snapshot(1, "app/routes.py", """
                from app.user_service import create_user

                def register(payload):
                    return create_user(payload)
                """);
        FileSnapshot userService = snapshot(2, "app/user_service.py", "def create_user(payload):\n    pass\n");
        FileSnapshot handler = snapshot(3, "src/handler.js", "const users = require('./user-store');\n");
        FileSnapshot store = snapshot(4, "src/user-store.js", "module.exports = {};\n");

        assertThat(find(routes, List.of(routes, userService), 1, 5)).extracting(r -> r.file().path())
                .containsExactly("app/user_service.py");
        assertThat(find(handler, List.of(handler, store), 1, 5)).extracting(r -> r.file().path())
                .containsExactly("src/user-store.js");
    }

    @Test
    void ignoresPartialWordMatchesAndGenericNames() {
        FileSnapshot main = snapshot(1, "Main.java", "class Main { UserServiceImpl impl; String index; }");
        FileSnapshot service = snapshot(2, "UserService.java", "class UserService {}");
        FileSnapshot index = snapshot(3, "index.js", "export default {};");

        assertThat(find(main, List.of(main, service, index), 2, 5)).isEmpty();
    }

    private List<RelatedFileFinder.RelatedFile> find(FileSnapshot selected, List<FileSnapshot> all, int depth, int max) {
        Map<Long, FileSnapshot> byId = all.stream().collect(Collectors.toMap(FileSnapshot::id, Function.identity()));
        return finder.find(List.of(selected), all.stream().map(f -> summary(f)).toList(),
                ids -> ids.stream().map(byId::get).toList(), depth, max);
    }
}
