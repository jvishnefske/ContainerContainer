package org.artifactory.ui.rest.service.admin.services.filesystem;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.ui.rest.model.admin.services.filesystem.FileSystemBrowser;
import org.artifactory.ui.rest.model.admin.services.filesystem.FileSystemItem;
import org.artifactory.util.AolUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author oleksandrl
 */
public class BrowseFileSystemServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryRestRequest request;

    private BrowseFileSystemService browseFileSystemService;
    private ArtifactoryRestResponse response;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        browseFileSystemService = new BrowseFileSystemService();
        response = new ArtifactoryRestResponse();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testGetFileOrFoldersListWithoutAllowlistUsesOsRoots() throws IOException {
        File browseDir = Files.createTempDirectory("browse-legacy").toFile();
        try {
            File childDir = new File(browseDir, "child-folder");
            assertTrue(childDir.mkdir());

            FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList(
                    browseDir.getAbsolutePath(), false, Collections.emptyList());

            assertNotNull(browser.getRoots());
            assertEquals(browser.getRoots().size(), File.listRoots().length);
            assertTrue(findItem(browser, "child-folder").isPresent());
            assertTrue(findItem(browser, "child-folder").get().isFolder());
        } finally {
            FileUtils.deleteQuietly(browseDir);
        }
    }

    @Test
    public void testTwoArgOverloadMatchesLegacyBehavior() throws IOException {
        File browseDir = Files.createTempDirectory("browse-two-arg").toFile();
        try {
            File childDir = new File(browseDir, "child-folder");
            assertTrue(childDir.mkdir());

            FileSystemBrowser legacy = browseFileSystemService.getFileOrFoldersList(
                    browseDir.getAbsolutePath(), false);
            FileSystemBrowser explicit = browseFileSystemService.getFileOrFoldersList(
                    browseDir.getAbsolutePath(), false, Collections.emptyList());

            assertEquals(legacy.getRoots(), explicit.getRoots());
            assertEquals(itemNames(legacy), itemNames(explicit));
        } finally {
            FileUtils.deleteQuietly(browseDir);
        }
    }

    @Test
    public void testGetFileOrFoldersListWithAllowlistSetsRootsAndListsChildren() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-scoped").toFile();
        try {
            File subDir = new File(allowedRoot, "visible-dir");
            assertTrue(subDir.mkdir());
            new File(allowedRoot, "ignore.txt").createNewFile();

            List<String> allowed = Arrays.asList("  " + allowedRoot.getAbsolutePath() + "  ", "", null);
            FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList(
                    allowedRoot.getAbsolutePath(), false, allowed);

            assertEquals(browser.getRoots(), List.of(allowedRoot.getAbsolutePath()));
            assertEquals(browser.getFileSystemItems().size(), 1);
            assertEquals(browser.getFileSystemItems().get(0).getFileSystemItemName(), "visible-dir");
            assertTrue(browser.getFileSystemItems().get(0).isFolder());
        } finally {
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testGetFileOrFoldersListReturnsEmptyWhenPathOutsideAllowlist() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-outside").toFile();
        try {
            FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList(
                    "/etc", false, List.of(allowedRoot.getAbsolutePath()));

            assertEquals(browser.getRoots(), List.of(allowedRoot.getAbsolutePath()));
            assertTrue(browser.getFileSystemItems().isEmpty());
        } finally {
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testGetFileOrFoldersListWithBlankPathUsesFirstAllowedRoot() throws IOException {
        File rootA = Files.createTempDirectory("browse-a").toFile();
        File rootB = Files.createTempDirectory("browse-b").toFile();
        try {
            assertTrue(new File(rootA, "from-a").mkdir());
            assertTrue(new File(rootB, "from-b").mkdir());

            List<String> allowed = List.of(rootB.getAbsolutePath(), rootA.getAbsolutePath());
            FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList("", false, allowed);

            assertEquals(browser.getRoots(), List.of(rootA.getAbsolutePath(), rootB.getAbsolutePath()));
            assertTrue(findItem(browser, "from-a").isPresent());
            assertFalse(findItem(browser, "from-b").isPresent());
        } finally {
            FileUtils.deleteQuietly(rootA);
            FileUtils.deleteQuietly(rootB);
        }
    }

    @Test
    public void testGetFileOrFoldersListIncludeZipFiles() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-zip").toFile();
        try {
            assertTrue(new File(allowedRoot, "folder").mkdir());
            new File(allowedRoot, "archive.zip").createNewFile();

            FileSystemBrowser withoutZip = browseFileSystemService.getFileOrFoldersList(
                    allowedRoot.getAbsolutePath(), false, List.of(allowedRoot.getAbsolutePath()));
            FileSystemBrowser withZip = browseFileSystemService.getFileOrFoldersList(
                    allowedRoot.getAbsolutePath(), true, List.of(allowedRoot.getAbsolutePath()));

            assertEquals(withoutZip.getFileSystemItems().size(), 1);
            assertTrue(findItem(withoutZip, "folder").isPresent());

            assertEquals(withZip.getFileSystemItems().size(), 2);
            assertTrue(findItem(withZip, "archive.zip").isPresent());
            assertFalse(findItem(withZip, "archive.zip").get().isFolder());
        } finally {
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testGetFileOrFoldersListSortsFoldersBeforeFiles() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-sort").toFile();
        try {
            new File(allowedRoot, "zebra.zip").createNewFile();
            assertTrue(new File(allowedRoot, "alpha-dir").mkdir());
            assertTrue(new File(allowedRoot, "beta-dir").mkdir());

            FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList(
                    allowedRoot.getAbsolutePath(), true, List.of(allowedRoot.getAbsolutePath()));

            List<String> names = itemNames(browser);
            assertEquals(names, List.of("alpha-dir", "beta-dir", "zebra.zip"));
        } finally {
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testBlankAllowlistEntriesFallBackToOsRoots() throws IOException {
        FileSystemBrowser browser = browseFileSystemService.getFileOrFoldersList(
                "", false, Arrays.asList("", "  ", null));

        assertNotNull(browser.getRoots());
        assertEquals(browser.getRoots().size(), File.listRoots().length);
    }

    @Test
    public void testExecuteWithImportModeScopesToConfiguredAllowlist() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-import-mode").toFile();
        try {
            assertTrue(new File(allowedRoot, "import-child").mkdir());
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(),
                            allowedRoot.getAbsolutePath());

            when(request.getQueryParamByKey("mode")).thenReturn("import");
            when(request.getQueryParamByKey("path")).thenReturn(allowedRoot.getAbsolutePath());
            when(request.getQueryParamByKey("includeZip")).thenReturn("false");

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
                browseFileSystemService.execute(request, response);
            }

            FileSystemBrowser browser = (FileSystemBrowser) response.getIModel();
            assertEquals(browser.getRoots(), List.of(allowedRoot.getAbsolutePath()));
            assertTrue(findItem(browser, "import-child").isPresent());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testExecuteWithExportModeScopesToConfiguredAllowlist() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-export-mode").toFile();
        try {
            assertTrue(new File(allowedRoot, "export-child").mkdir());
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(),
                            allowedRoot.getAbsolutePath());

            when(request.getQueryParamByKey("mode")).thenReturn("export");
            when(request.getQueryParamByKey("path")).thenReturn(allowedRoot.getAbsolutePath());
            when(request.getQueryParamByKey("includeZip")).thenReturn("false");

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
                browseFileSystemService.execute(request, response);
            }

            FileSystemBrowser browser = (FileSystemBrowser) response.getIModel();
            assertEquals(browser.getRoots(), List.of(allowedRoot.getAbsolutePath()));
            assertTrue(findItem(browser, "export-child").isPresent());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testExecuteWithoutModePreservesLegacyOsRoots() throws IOException {
        File browseDir = Files.createTempDirectory("browse-no-mode").toFile();
        try {
            assertTrue(new File(browseDir, "legacy-child").mkdir());

            when(request.getQueryParamByKey("mode")).thenReturn(null);
            when(request.getQueryParamByKey("path")).thenReturn(browseDir.getAbsolutePath());
            when(request.getQueryParamByKey("includeZip")).thenReturn("false");

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
                browseFileSystemService.execute(request, response);
            }

            FileSystemBrowser browser = (FileSystemBrowser) response.getIModel();
            assertEquals(browser.getRoots().size(), File.listRoots().length);
            assertTrue(findItem(browser, "legacy-child").isPresent());
        } finally {
            FileUtils.deleteQuietly(browseDir);
        }
    }

    @Test
    public void testExecuteWithUnknownModePreservesLegacyOsRoots() throws IOException {
        File browseDir = Files.createTempDirectory("browse-unknown-mode").toFile();
        try {
            assertTrue(new File(browseDir, "legacy-child").mkdir());

            // "foo" is not one of import/export/backup -> unscoped legacy behavior
            when(request.getQueryParamByKey("mode")).thenReturn("foo");
            when(request.getQueryParamByKey("path")).thenReturn(browseDir.getAbsolutePath());
            when(request.getQueryParamByKey("includeZip")).thenReturn("false");

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
                browseFileSystemService.execute(request, response);
            }

            FileSystemBrowser browser = (FileSystemBrowser) response.getIModel();
            assertEquals(browser.getRoots().size(), File.listRoots().length);
            assertTrue(findItem(browser, "legacy-child").isPresent());
        } finally {
            FileUtils.deleteQuietly(browseDir);
        }
    }

    @Test
    public void testExecuteWithBackupModeScopesToConfiguredAllowlist() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-backup-mode").toFile();
        try {
            assertTrue(new File(allowedRoot, "backup-child").mkdir());
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(),
                            allowedRoot.getAbsolutePath());

            when(request.getQueryParamByKey("mode")).thenReturn("backup");
            when(request.getQueryParamByKey("path")).thenReturn(allowedRoot.getAbsolutePath());
            when(request.getQueryParamByKey("includeZip")).thenReturn("false");

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
                browseFileSystemService.execute(request, response);
            }

            FileSystemBrowser browser = (FileSystemBrowser) response.getIModel();
            assertEquals(browser.getRoots(), List.of(allowedRoot.getAbsolutePath()));
            assertTrue(findItem(browser, "backup-child").isPresent());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testExecuteWithImportModeReturnsEmptyListingForPathOutsideAllowlist() throws IOException {
        File allowedRoot = Files.createTempDirectory("browse-import-outside").toFile();
        try {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(),
                            allowedRoot.getAbsolutePath());

            when(request.getQueryParamByKey("mode")).thenReturn("import");
            when(request.getQueryParamByKey("path")).thenReturn("/etc");
            when(request.getQueryParamByKey("includeZip")).thenReturn("false");

            try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
                mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
                browseFileSystemService.execute(request, response);
            }

            FileSystemBrowser browser = (FileSystemBrowser) response.getIModel();
            assertEquals(browser.getRoots(), List.of(allowedRoot.getAbsolutePath()));
            assertTrue(browser.getFileSystemItems().isEmpty());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testResolveAllowlistForMode() throws Exception {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "/tmp/import/");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "/tmp/export/");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), "/tmp/backup/");
        try {
            assertEquals(invokeResolveAllowlistForMode("import"), List.of("/tmp/import/"));
            assertEquals(invokeResolveAllowlistForMode("IMPORT"), List.of("/tmp/import/"));
            assertEquals(invokeResolveAllowlistForMode("export"), List.of("/tmp/export/"));
            assertEquals(invokeResolveAllowlistForMode("EXPORT"), List.of("/tmp/export/"));
            assertEquals(invokeResolveAllowlistForMode("backup"), List.of("/tmp/backup/"));
            assertEquals(invokeResolveAllowlistForMode("BACKUP"), List.of("/tmp/backup/"));
            assertTrue(invokeResolveAllowlistForMode(null).isEmpty());
            assertTrue(invokeResolveAllowlistForMode("unknown").isEmpty());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.importAllowedPaths.getPropertyName(), "");
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.exportAllowedPaths.getPropertyName(), "");
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), "");
        }
    }

    private List<String> invokeResolveAllowlistForMode(String mode) throws Exception {
        Method method = BrowseFileSystemService.class.getDeclaredMethod("resolveAllowlistForMode", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(browseFileSystemService, mode);
    }

    private static List<String> itemNames(FileSystemBrowser browser) {
        return browser.getFileSystemItems().stream()
                .map(FileSystemItem::getFileSystemItemName)
                .collect(Collectors.toList());
    }

    private static java.util.Optional<FileSystemItem> findItem(FileSystemBrowser browser, String name) {
        return browser.getFileSystemItems().stream()
                .filter(item -> name.equals(item.getFileSystemItemName()))
                .findFirst();
    }
}
