package org.artifactory.repo.db.importexport;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Igor Usenko
 */
class DbRepoImportExportBaseTest extends ArtifactoryHomeBoundJUnitTest {

    private final TestDbRepoImportExportBase subject = new TestDbRepoImportExportBase();

    @Test
    void isStorableFolder_returnsTrue_forEvidenceDir() {
        assertTrue(subject.isStorableFolder(".evidence"));
    }

    @Test
    void isStorableFile_returnsTrue_forEvidenceArtifact() {
        assertTrue(subject.isStorableFile("artifact.evd"));
    }

    static class TestDbRepoImportExportBase extends DbRepoImportExportBase {
        @Override
        protected boolean isStorableFolder(String name) {
            return super.isStorableFolder(name);
        }

        @Override
        protected boolean isStorableFile(String name) {
            return super.isStorableFile(name);
        }
    }
}
