package org.artifactory.repo.service.trash;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Tests for {@link InternalMetadataNonTrashable}
 *
 * @author Hussein Alshqerat
 */
class InternalMetadataNonTrashableTest {

    private final InternalMetadataNonTrashable nonTrashable = new InternalMetadataNonTrashable();

    @ParameterizedTest
    @ValueSource(strings = {
            ".jfrog",
            ".jfrog/index",
            ".jfrog/org/artifact/1.0/maven-metadata.xml",
            ".jfrog//index"
    })
    void skipTrash_shouldReturnTrue_whenPathIsInternalMetadata(String path) {
        // Act / Assert (config is unused by the delegate; null is fine)
        assertTrue(nonTrashable.skipTrash(null, path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            ".",
            "normal.file",
            ".jfrogextra",
            ".jfrog-other/index",
            "/.jfrog/index",
            "repo/.jfrog/index"
    })
    void skipTrash_shouldReturnFalse_whenPathIsNotInternalMetadata(String path) {
        // Act / Assert
        assertFalse(nonTrashable.skipTrash(null, path));
    }
}
