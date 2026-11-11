package org.artifactory.repo.service.flexible;

import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Tomer Slutsky
 */
class MoveCopyTargetPathResolverTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getDiffPathTest(boolean skipName) {
        List.of(".", "+", "*", "?", "^", "$", "(", ")", "[", "]", "{", "}", "|").forEach(curr -> {
            String diffPath = MoveCopyTargetPathResolver
                    .getDiffPath(RepoPathFactory.create(TRASH_REPO_KEY, "path" + curr + "with" + curr + "special"),
                            RepoPathFactory.create(TRASH_REPO_KEY, "path" + curr + "with" + curr + "special"),
                            skipName);
            assertEquals("", diffPath);
        });
    }
}