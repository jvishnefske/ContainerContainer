package org.artifactory.repo.system;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.artifactory.repo.system.SystemFolders.SystemFolder;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class SystemFoldersTest {

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testIsSystemFolderTrue(SystemFolder systemFolder) {
        RepoPath systemFolderPath = RepoPathFactory.create("repoKey", systemFolder.getFolderName() + "/");
        assertTrue(SystemFolders.isSystemFolder(systemFolderPath));
    }

    @Test
    void testIsSystemFolderFalse() {
        RepoPath nonSystemFolderPath = RepoPathFactory.create("repoKey", "nonSystemFolder");
        assertFalse(SystemFolders.isSystemFolder(nonSystemFolderPath));
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testIsSystemFolderArtifactNamedAsFolderFalse(SystemFolder systemFolder) {
        // Test with an artifact that has the same name as a system folder but is not a folder
        RepoPath artifactPath = RepoPathFactory.create("repoKey", systemFolder.getFolderName());
        assertFalse(SystemFolders.isSystemFolder(artifactPath));
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testIsUnderSystemFolderTrue(SystemFolder systemFolder) {
        RepoPath systemFolderPath =
                RepoPathFactory.create("repoKey", systemFolder.getFolderName() + "/some/path/artifact.txt");
        assertTrue(SystemFolders.isUnderSystemFolder(systemFolderPath));
    }

    @Test
    void testIsUnderSystemFolderFalse() {
        RepoPath nonSystemFolderPath = RepoPathFactory.create("repoKey", "some/other/path/artifact.txt");
        assertFalse(SystemFolders.isUnderSystemFolder(nonSystemFolderPath));
    }

    @Test
    void testIsUnderSystemFolderRootFalse() {
        RepoPath rootPath = RepoPathFactory.create("repoKey");
        assertFalse(SystemFolders.isUnderSystemFolder(rootPath));
    }

    @Test
    void testIsUnderSystemFolderNullFalse() {
        assertFalse(SystemFolders.isUnderSystemFolder(null));
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testIsUnderSystemFolderArtifactNamedAsFolderFalse(SystemFolder systemFolder) {
        // Test with an artifact that has the same name as a system folder but is not a folder
        RepoPath artifactPath = RepoPathFactory.create("repoKey", systemFolder.getFolderName());
        assertFalse(SystemFolders.isUnderSystemFolder(artifactPath));
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testForPathFindsMatchOnExactFolderName(SystemFolder systemFolder) {
        RepoPath path = RepoPathFactory.create("repo", systemFolder.getFolderName());
        Optional<SystemFolder> result = SystemFolders.forPath(path);
        assertTrue(result.isPresent());
        assertEquals(systemFolder, result.get());
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testForPathFindsMatchOnNestedPath(SystemFolder systemFolder) {
        RepoPath path = RepoPathFactory.create("repo", systemFolder.getFolderName() + "/a/b/file.txt");
        Optional<SystemFolder> result = SystemFolders.forPath(path);
        assertTrue(result.isPresent());
        assertEquals(systemFolder, result.get());
    }

    @Test
    void testForPathReturnsEmptyForNonSystemFolder() {
        RepoPath path = RepoPathFactory.create("repo", "regular/path/artifact.jar");
        assertTrue(SystemFolders.forPath(path).isEmpty());
    }

    @Test
    void testForPathReturnsEmptyForNull() {
        assertTrue(SystemFolders.forPath((RepoPath) null).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testForPathStringFindsMatchOnNestedPath(SystemFolder systemFolder) {
        // The String overload exists so callers with only an in-repo path don't have to
        // allocate a synthetic RepoPath just to perform the lookup.
        String path = systemFolder.getFolderName() + "/a/b/file.txt";
        Optional<SystemFolder> result = SystemFolders.forPath(path);
        assertTrue(result.isPresent());
        assertEquals(systemFolder, result.get());
    }

    @Test
    void testForPathStringReturnsEmptyForNullOrBlank() {
        assertTrue(SystemFolders.forPath((String) null).isEmpty());
        assertTrue(SystemFolders.forPath("").isEmpty());
        assertTrue(SystemFolders.forPath("   ").isEmpty());
    }

    @Test
    void testForPathStringReturnsEmptyForNonSystemFolder() {
        assertTrue(SystemFolders.forPath("regular/path/artifact.jar").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void testForPathStringTolerantOfLeadingSlash(SystemFolder systemFolder) {
        // Some callers build the string from segments and end up with a leading "/".
        // The lookup must work for both "/.evidence/x" and ".evidence/x".
        String withLeadingSlash = "/" + systemFolder.getFolderName() + "/a/file.txt";
        Optional<SystemFolder> result = SystemFolders.forPath(withLeadingSlash);
        assertTrue(result.isPresent());
        assertEquals(systemFolder, result.get());
    }

    @Test
    void testEvidenceIsBackedUp() {
        RepoPath path = RepoPathFactory.create("repo", ".evidence/subject/file.evidence");
        assertTrue(SystemFolders.isBackedUp(path));
    }

    @Test
    void testEvidenceIsFederated() {
        RepoPath path = RepoPathFactory.create("repo", ".evidence/subject/file.evidence");
        assertTrue(SystemFolders.isFederated(path));
    }

    @Test
    void testEvidenceIsBilled() {
        RepoPath path = RepoPathFactory.create("repo", ".evidence/subject/file.evidence");
        assertTrue(SystemFolders.isBilled(path));
    }

    @Test
    void testEvidenceDownloadAllowed() {
        RepoPath path = RepoPathFactory.create("repo", ".evidence/subject/file.evidence");
        assertTrue(SystemFolders.isDownloadAllowed(path));
    }

    @Test
    void testJfrogIsBackedUp() {
        RepoPath path = RepoPathFactory.create("repo", ".jfrog/some/config.json");
        assertTrue(SystemFolders.isBackedUp(path));
    }

    @Test
    void testJfrogIsNotFederated() {
        RepoPath path = RepoPathFactory.create("repo", ".jfrog/some/config.json");
        assertFalse(SystemFolders.isFederated(path));
    }

    @Test
    void testJfrogIsNotBilled() {
        RepoPath path = RepoPathFactory.create("repo", ".jfrog/some/config.json");
        assertFalse(SystemFolders.isBilled(path));
    }

    @Test
    void testJfrogDownloadNotAllowed() {
        RepoPath path = RepoPathFactory.create("repo", ".jfrog/some/config.json");
        assertFalse(SystemFolders.isDownloadAllowed(path));
    }

    @Test
    void testShowInUI_evidence_propertyAbsent_returnsFalse() {
        try (MockedStatic<ArtifactoryHome> ignored = mockStatic(ArtifactoryHome.class)) {
            ignored.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(false);

            assertFalse(SystemFolders.isShowInUI(SystemFolder.EVIDENCE));
        }
    }

    @Test
    void testShowInUI_evidence_propertyFalse_returnsFalse() {
        try (MockedStatic<ArtifactoryHome> ignored = mockStatic(ArtifactoryHome.class)) {
            ignored.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(false);

            assertFalse(SystemFolders.isShowInUI(SystemFolder.EVIDENCE));
        }
    }

    @Test
    void testShowInUI_evidence_propertyTrue_returnsTrue() {
        try (MockedStatic<ArtifactoryHome> ignored = mockStatic(ArtifactoryHome.class)) {
            ignored.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(true);

            assertTrue(SystemFolders.isShowInUI(SystemFolder.EVIDENCE));
        }
    }

    @Test
    void testShowInUI_jfrog_returnsEnumFieldNotProperty() {
        // For folders other than EVIDENCE, the helper must read the enum field directly
        // and must NOT consult the runtime property (so a stray property won't flip JFROG).
        try (MockedStatic<ArtifactoryHome> mockedHome = mockStatic(ArtifactoryHome.class)) {
            mockedHome.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(true); // would flip the result if read incorrectly

            assertFalse(SystemFolders.isShowInUI(SystemFolder.JFROG));
            // Verify the property was NOT consulted for JFROG.
            mockedHome.verifyNoInteractions();
        }
    }

    @Test
    void testNonSystemFolderHelpersFallbackToFalse() {
        RepoPath path = RepoPathFactory.create("repo", "regular/path/artifact.jar");
        assertFalse(SystemFolders.isBackedUp(path));
        assertFalse(SystemFolders.isFederated(path));
        assertFalse(SystemFolders.isBilled(path));
        assertFalse(SystemFolders.isDownloadAllowed(path));
    }

}
