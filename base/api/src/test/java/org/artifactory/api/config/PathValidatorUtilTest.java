package org.artifactory.api.config;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.api.config.PathValidatorUtil.isArtifactoryHomePath;
import static org.artifactory.api.config.PathValidatorUtil.isBackupPathInAllowList;
import static org.artifactory.api.config.PathValidatorUtil.isExportImportValidPath;
import static org.artifactory.api.config.PathValidatorUtil.isExportPathInAllowList;
import static org.artifactory.api.config.PathValidatorUtil.isImportPathInAllowList;
import static org.artifactory.api.config.PathValidatorUtil.isImportValidPath;
import static org.artifactory.api.config.PathValidatorUtil.isInsideAllowedPaths;
import static org.artifactory.api.config.PathValidatorUtil.isOrContainsArtifactoryHomePath;
import static org.artifactory.api.config.PathValidatorUtil.isPathForArchiveIsValid;
import static org.artifactory.common.ConstantValues.allowExportImportInsecurePaths;
import static org.artifactory.common.ConstantValues.backupAllowedPaths;
import static org.artifactory.common.ConstantValues.exportAllowedPaths;
import static org.artifactory.common.ConstantValues.importAllowedPaths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * @author Hezi Cohen
 */
class PathValidatorUtilTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void setUp() {
        getOrCreateArtifactoryHomeStub().setProperty(allowExportImportInsecurePaths,
                allowExportImportInsecurePaths.getDefValue());
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "");
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "");
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "");
    }

    @Test
    void testIsValidPathAllowFlag() {
        getOrCreateArtifactoryHomeStub().setProperty(allowExportImportInsecurePaths, "true");
        assertTrue(isExportImportValidPath("\\\\127.0.0.1\\my_dir"));
    }

    @Test
    void testIsValidPathHomePath() {
        assertFalse(
                isExportImportValidPath(
                        getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath()));
        assertTrue(
                isExportImportValidPath(
                        getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath(), true));
    }

    @ParameterizedTest
    @MethodSource("pathsToValidate")
    void testIsValidPath(String pathToValidate, boolean isValid) {
        assertEquals(isExportImportValidPath(pathToValidate), isValid);
    }

    private static Stream<Arguments> pathsToValidate() {
        return Stream.of(
                Arguments.of("\\\\127.0.0.1\\my_dir", false),
                Arguments.of("${shared_drive}/my_dir", false),
                Arguments.of("//my_dir", false),
                Arguments.of("//shared_drive\\my_dir", false),
                Arguments.of("/shared_drive\\my_dir", true),
                Arguments.of("/shared_drive/my_dir", true),
                Arguments.of("shared_drive\\my_dir", true),
                Arguments.of("shared_drive/${another_dir}", true)
        );
    }

    @Test
    void testIsArtifactoryHomePathIsHome() {
        String homePath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile() + "/tmp";
        assertTrue(isArtifactoryHomePath(new File(homePath)));
    }

    @Test
    void testIsArtifactoryHomePathIsNotHome() {
        String homePath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile().getParent() + "/tmp";
        assertFalse(isArtifactoryHomePath(new File(homePath)));
    }

    @Test
    void testIsArtifactoryHomePathNull() {
        assertFalse(isArtifactoryHomePath(null));
        assertFalse(isOrContainsArtifactoryHomePath(null));
    }

    @Test
    void testIsArtifactoryHomePathWithDotDotTraversal() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        String parentDir = homeDir.getParent();
        String homeDirName = homeDir.getName();
        String traversalPath = parentDir + "/somedir/../" + homeDirName + "/tmp";
        assertTrue(isArtifactoryHomePath(new File(traversalPath)));
    }

    @Test
    void testIsArtifactoryHomePathWithMultipleDotDotTraversals() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        File parentDir = homeDir.getParentFile();
        String grandParentPath = parentDir.getParent();
        String parentName = parentDir.getName();
        String homeDirName = homeDir.getName();
        String traversalPath = grandParentPath + "/a/b/../../" + parentName + "/" + homeDirName + "/tmp";
        assertTrue(isArtifactoryHomePath(new File(traversalPath)));
    }

    @Test
    void isArtifactoryHomePath_shouldReturnTrue_whenAbsolutePathMatchesButCanonicalDoesNot() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        File subPath = new File(homeDir, "var/data");
        assertTrue(isArtifactoryHomePath(subPath));
    }

    @Test
    void isExportImportValidPath_shouldRejectDirectHomeSubdirectory() {
        String homePath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath();
        assertFalse(isExportImportValidPath(homePath + "/var/"));
    }

    @Test
    void isArtifactoryHomePath_shouldReturnFalse_whenPathIsParentOfHome() {
        // Narrow check (used by backup/export write flows) must NOT reject parent-of-home —
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        assertFalse(isArtifactoryHomePath(homeDir.getParentFile()));
    }

    @Test
    void isOrContainsArtifactoryHomePath_shouldReturnTrue_whenPathIsParentOfHome() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        // Parent directory contains the home directory — broad check (used by import flows)
        // must reject it because importing from the parent walks into the Artifactory home.
        assertTrue(isOrContainsArtifactoryHomePath(homeDir.getParentFile()));
    }

    @Test
    void isExportImportValidPath_shouldAcceptParentOfHomeDirectory() {
        String parentPath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile().getParent();
        assertTrue(isExportImportValidPath(parentPath));
    }

    @Test
    void isOrContainsArtifactoryHomePath_shouldReturnTrue_whenPathIsGrandparentOfHome() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        assertTrue(isOrContainsArtifactoryHomePath(homeDir.getParentFile().getParentFile()));
    }

    @Test
    void isImportValidPath_shouldRejectParentOfHomeDirectory() {
        String parentPath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile().getParent();
        assertFalse(isImportValidPath(parentPath));
    }

    @Test
    void isImportValidPath_shouldRejectGrandparentOfHome() {
        String grandparent = getOrCreateArtifactoryHomeStub().getHomeDir()
                .getAbsoluteFile().getParentFile().getParent();
        assertFalse(isImportValidPath(grandparent));
    }

    @Test
    void isImportValidPath_shouldHonorAllowImportZipFlag() {
        // When the import is uploading a zip into the temp dir under home, callers pass
        // allowImportZipFileFromHomePath=true. Both narrow and broad home checks must be
        // skipped in that case.
        String homePath = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsolutePath();
        assertFalse(isImportValidPath(homePath));
        assertTrue(isImportValidPath(homePath, true));
    }

    @Test
    void isArtifactoryHomePath_shouldReturnFalse_whenPathIsSiblingOfHome() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        // Sibling directory (same parent, different name) should NOT be blocked
        File sibling = new File(homeDir.getParent(), "some-other-app");
        assertFalse(isArtifactoryHomePath(sibling));
        assertFalse(isOrContainsArtifactoryHomePath(sibling));
    }

    @Test
    void isOrContainsArtifactoryHomePath_shouldReturnTrue_whenPathIsRoot() {
        // Root contains the home directory — broad check must block it.
        // Also covers the isSubPath edge case where parent already ends with File.separator.
        assertTrue(isOrContainsArtifactoryHomePath(new File("/")));
    }

    @Test
    void isImportValidPath_shouldRejectRoot() {
        assertFalse(isImportValidPath("/"));
    }

    @Test
    void isArtifactoryHomePath_shouldReturnTrue_whenPathMatchesVarDir() {
        // Simulates importing from the var directory (real path in Docker deployments
        // where /opt/jfrog/artifactory/var is a symlink to /var/opt/jfrog/artifactory).
        File varDir = getOrCreateArtifactoryHomeStub().getVarDir().getAbsoluteFile();
        assertTrue(isArtifactoryHomePath(varDir));
    }

    @Test
    void isOrContainsArtifactoryHomePath_shouldReturnTrue_whenPathIsParentOfVarDir() {
        // Parent of var contains var — broad check must block it (e.g. /var/opt/jfrog/
        // when var is symlinked there in Docker deployments).
        File varDirParent = getOrCreateArtifactoryHomeStub().getVarDir().getAbsoluteFile().getParentFile();
        assertTrue(isOrContainsArtifactoryHomePath(varDirParent));
    }

    @Test
    void isArtifactoryHomePath_shouldReturnTrue_whenPathMatchesDataDir() {
        File dataDir = getOrCreateArtifactoryHomeStub().getDataDir().getAbsoluteFile();
        assertTrue(isArtifactoryHomePath(dataDir));
    }

    @Test
    void isArtifactoryHomePath_shouldReturnTrue_whenPathMatchesEtcDir() {
        File etcDir = getOrCreateArtifactoryHomeStub().getEtcDir().getAbsoluteFile();
        assertTrue(isArtifactoryHomePath(etcDir));
    }

    @Test
    void isExportImportValidPath_shouldRejectExactReportTraversalPayload() {
        assertFalse(isExportImportValidPath("/etc/../opt/jfrog/artifactory/var/"));
    }

    @Test
    void isExportImportValidPath_shouldAcceptGrandparentOfHome() {
        String grandparent = getOrCreateArtifactoryHomeStub().getHomeDir()
                .getAbsoluteFile().getParentFile().getParent();
        assertTrue(isExportImportValidPath(grandparent));
    }

    @Test
    void testIsExportImportValidPathRejectsDotDotTraversal() {
        assertFalse(isExportImportValidPath("/opt/jfrog/../artifactory/var/"));
    }

    @Test
    void testIsExportImportValidPathRejectsBackslashDotDotTraversal() {
        assertFalse(isExportImportValidPath("C:\\some\\path\\..\\secret"));
    }

    @Test
    void testIsExportImportValidPathRejectsMiddleDotDotSegments() {
        assertFalse(isExportImportValidPath("/opt/safe/../unsafe/path"));
    }

    @Test
    void testArchivePathValidation() {
        assertFalse(isPathForArchiveIsValid(null));
        assertFalse(isPathForArchiveIsValid("parent/../child"));
        assertTrue(isPathForArchiveIsValid("parent/child"));
        assertFalse(isPathForArchiveIsValid("//dir"));
    }

    // -- allowlist tests (default empty -> legacy behavior preserved) ----------------------------

    @Test
    void isImportPathInAllowList_returnsTrue_whenAllowlistEmpty() {
        assertTrue(isImportPathInAllowList("/tmp/import"));
        assertTrue(isImportPathInAllowList("/var/backups/artifactory/"));
        assertTrue(isImportPathInAllowList("/anywhere/at/all"));
    }

    @Test
    void isExportPathInAllowList_returnsTrue_whenAllowlistEmpty() {
        assertTrue(isExportPathInAllowList("/tmp/export"));
        assertTrue(isExportPathInAllowList("/var/backups/artifactory/"));
        assertTrue(isExportPathInAllowList("/anywhere/at/all"));
    }

    // -- allowlist tests (configured via ConstantValues / properties / -D) ------------------------

    @Test
    void isImportPathInAllowList_returnsTrue_whenPathIsAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/tmp/,/mnt/backups/");
        assertTrue(isImportPathInAllowList("/tmp/"));
        assertTrue(isImportPathInAllowList("/tmp"));
    }

    @Test
    void isImportPathInAllowList_returnsTrue_whenPathIsInsideAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/tmp/,/mnt/backups/");
        assertTrue(isImportPathInAllowList("/tmp/import-dir"));
        assertTrue(isImportPathInAllowList("/mnt/backups/snapshot-2024"));
    }

    @Test
    void isImportPathInAllowList_returnsFalse_whenPathOutsideAllowedDirs() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/tmp/");
        assertFalse(isImportPathInAllowList("/etc/passwd"));
        assertFalse(isImportPathInAllowList("/var/log"));
    }

    @Test
    void isImportPathInAllowList_rejectsDotDotEscapeOutOfAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/tmp/");
        // /tmp/../etc canonicalizes to /etc -> rejected
        assertFalse(isImportPathInAllowList("/tmp/../etc"));
    }

    @Test
    void isImportPathInAllowList_returnsFalse_whenPathIsAncestorOfAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/mnt/backups/artifactory/");
        // /mnt is broader than the allowed /mnt/backups/artifactory -> rejected
        assertFalse(isImportPathInAllowList("/mnt"));
    }

    @Test
    void isImportPathInAllowList_returnsTrue_whenInsecurePathsKillSwitchOn() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/tmp/");
        getOrCreateArtifactoryHomeStub().setProperty(allowExportImportInsecurePaths, "true");
        assertTrue(isImportPathInAllowList("/etc/passwd"));
    }

    @Test
    void isImportPathInAllowList_returnsTrue_whenAllowImportZipFlag() {
        // upload-zip flow bypasses both home check and allowlist
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, "/mnt/backups/");
        assertTrue(isImportPathInAllowList("/tmp/upload.zip", true));
    }

    @Test
    void isImportPathInAllowList_trimsAndIgnoresBlankEntries() {
        getOrCreateArtifactoryHomeStub().setProperty(importAllowedPaths, " /tmp/ , ,  /mnt/backups/  ");
        assertTrue(isImportPathInAllowList("/tmp/import"));
        assertTrue(isImportPathInAllowList("/mnt/backups/x"));
        assertFalse(isImportPathInAllowList("/etc"));
    }

    @Test
    void isExportPathInAllowList_returnsTrue_whenPathInsideAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "/tmp/,/mnt/backups/");
        assertTrue(isExportPathInAllowList("/tmp/export-dir"));
        assertTrue(isExportPathInAllowList("/mnt/backups/snapshot"));
    }

    @Test
    void isExportPathInAllowList_returnsFalse_whenPathOutsideAllowedDirs() {
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "/tmp/");
        assertFalse(isExportPathInAllowList("/etc/output"));
    }

    @Test
    void isExportPathInAllowList_rejectsDotDotEscape() {
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "/tmp/");
        assertFalse(isExportPathInAllowList("/tmp/../etc"));
    }

    @Test
    void isExportPathInAllowList_returnsFalse_whenPathIsAncestorOfAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "/mnt/backups/artifactory/");
        assertFalse(isExportPathInAllowList("/mnt"));
    }

    @Test
    void isExportPathInAllowList_returnsTrue_whenInsecurePathsKillSwitchOn() {
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "/tmp/");
        getOrCreateArtifactoryHomeStub().setProperty(allowExportImportInsecurePaths, "true");
        assertTrue(isExportPathInAllowList("/etc/output"));
    }

    // -- backup allowlist tests ------------------------------------------------------------------

    @Test
    void isBackupPathInAllowList_returnsTrue_whenAllowlistEmpty() {
        assertTrue(isBackupPathInAllowList("/tmp/backup"));
        assertTrue(isBackupPathInAllowList("/var/backups/artifactory/"));
        assertTrue(isBackupPathInAllowList("/anywhere/at/all"));
    }

    @Test
    void isBackupPathInAllowList_returnsTrue_whenPathInsideAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "/tmp/,/mnt/backups/");
        assertTrue(isBackupPathInAllowList("/tmp/backup-dir"));
        assertTrue(isBackupPathInAllowList("/mnt/backups/snapshot-2026"));
    }

    @Test
    void isBackupPathInAllowList_returnsFalse_whenPathOutsideAllowedDirs() {
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "/tmp/");
        assertFalse(isBackupPathInAllowList("/etc/output"));
        assertFalse(isBackupPathInAllowList("/var/log"));
    }

    @Test
    void isBackupPathInAllowList_rejectsDotDotEscape() {
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "/tmp/");
        // /tmp/../etc canonicalizes to /etc -> rejected
        assertFalse(isBackupPathInAllowList("/tmp/../etc"));
    }

    @Test
    void isBackupPathInAllowList_returnsFalse_whenPathIsAncestorOfAllowedDir() {
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "/mnt/backups/artifactory/");
        assertFalse(isBackupPathInAllowList("/mnt"));
    }

    @Test
    void isBackupPathInAllowList_returnsFalse_evenWhenInsecurePathsKillSwitchOn() {
        // The allow.export.import.insecure.paths switch is historically scoped to import/export
        // and must NOT bypass the backup allowlist. Once a backup allowlist is configured, the
        // only way to write outside it is to explicitly add the destination to the allowlist.
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "/tmp/");
        getOrCreateArtifactoryHomeStub().setProperty(allowExportImportInsecurePaths, "true");
        assertFalse(isBackupPathInAllowList("/etc/output"));
    }

    @Test
    void isBackupPathInAllowList_isIndependentOfExportAllowlist() {
        // backup uses dedicated keys; an export allowlist must not implicitly allow backups
        getOrCreateArtifactoryHomeStub().setProperty(exportAllowedPaths, "/srv/exports/");
        getOrCreateArtifactoryHomeStub().setProperty(backupAllowedPaths, "/mnt/backups/");
        assertFalse(isBackupPathInAllowList("/srv/exports/something"));
        assertTrue(isBackupPathInAllowList("/mnt/backups/something"));
    }

    // -- isInsideAllowedPaths direct helper tests ------------------------------------------------

    @Test
    void isInsideAllowedPaths_returnsTrue_whenListEmpty() {
        assertTrue(isInsideAllowedPaths("/etc/passwd", List.of()));
        assertTrue(isInsideAllowedPaths("/etc/passwd", List.of("", "  ")));
    }

    @Test
    void isInsideAllowedPaths_returnsTrue_whenPathInside() {
        assertTrue(isInsideAllowedPaths("/tmp/sub", List.of("/tmp")));
    }

    @Test
    void isInsideAllowedPaths_returnsFalse_whenPathOutside() {
        assertFalse(isInsideAllowedPaths("/etc/passwd", List.of("/tmp", "/mnt/backups")));
    }

    @Test
    void isInsideAllowedPaths_returnsFalse_whenCandidatePathCannotBeCanonicalized() {
        try (MockedConstruction<File> ignored = mockConstruction(File.class, (mock, context) -> {
            String path = String.valueOf(context.arguments().getFirst());
            if ("/bad-candidate".equals(path)) {
                when(mock.getCanonicalPath()).thenThrow(new IOException("cannot canonicalize candidate"));
            } else {
                when(mock.getCanonicalPath()).thenReturn(path);
            }
        })) {
            assertFalse(isInsideAllowedPaths("/bad-candidate", List.of("/tmp")));
        }
    }

    @Test
    void isInsideAllowedPaths_returnsFalse_whenAllowedPathCannotBeCanonicalized() {
        try (MockedConstruction<File> ignored = mockConstruction(File.class, (mock, context) -> {
            String path = String.valueOf(context.arguments().getFirst());
            if ("/bad-allowed".equals(path)) {
                when(mock.getCanonicalPath()).thenThrow(new IOException("cannot canonicalize allowlist entry"));
            } else {
                when(mock.getCanonicalPath()).thenReturn(path);
            }
        })) {
            assertFalse(isInsideAllowedPaths("/tmp/sub", List.of("/bad-allowed")));
        }
    }

    @Test
    void isInsideAllowedPaths_returnsTrue_whenValidAllowedPathMatchesAfterSkippingBadEntry() {
        try (MockedConstruction<File> ignored = mockConstruction(File.class, (mock, context) -> {
            String path = String.valueOf(context.arguments().getFirst());
            if ("/bad-allowed".equals(path)) {
                when(mock.getCanonicalPath()).thenThrow(new IOException("cannot canonicalize allowlist entry"));
            } else {
                when(mock.getCanonicalPath()).thenReturn(path);
            }
        })) {
            assertTrue(isInsideAllowedPaths("/tmp/sub", List.of("/bad-allowed", "/tmp")));
        }
    }
}
