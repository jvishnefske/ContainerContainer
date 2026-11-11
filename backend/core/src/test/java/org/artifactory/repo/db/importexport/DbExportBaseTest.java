package org.artifactory.repo.db.importexport;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DbExportBaseTest {

    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private FileService fileService;
    @Mock
    private ArtifactoryBinaryService binaryService;
    @Mock
    private ArtifactoryStorageContext storageContext;
    @Mock
    private MutableStatusHolder status;
    @Mock
    private ImportExportAccumulator accumulator;
    @Mock
    private ExportSettings settings;

    private MockedStatic<StorageContextHelper> storageContextHelper;
    private TestDbExportBase testDbExportBase;

    @BeforeEach
    void setUp() {
        storageContextHelper = mockStatic(StorageContextHelper.class);
        storageContextHelper.when(StorageContextHelper::get).thenReturn(storageContext);
        when(storageContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(storageContext.beanForType(FileService.class)).thenReturn(fileService);
        when(storageContext.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        when(settings.getStatusHolder()).thenReturn(status);
        testDbExportBase = new TestDbExportBase(accumulator);
        testDbExportBase.setExportSettings(settings);
    }

    @AfterEach
    void tearDown() {
        storageContextHelper.close();
    }

    @Test
    void exportFile_shouldLogWarningOnly_whenBinaryNotFoundExceptionThrownAndRepositoryIsFederated() {
        // Arrange
        FileInfo fileInfo = mock(FileInfo.class);
        RepoPath repoPath = mock(RepoPath.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        File targetParentDir = new File("target");
        when(fileInfo.getRepoKey()).thenReturn("federated-repo");
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(fileInfo.getSha1()).thenReturn("abc123");
        when(fileInfo.getName()).thenReturn("test-file.txt");
        when(fileInfo.getSize()).thenReturn(1024L);
        when(repoPath.toString()).thenReturn("federated-repo:path/to/file");
        when(fileService.exists(repoPath)).thenReturn(true);
        when(repositoryConfigService.getRepoConfigByKey("federated-repo")).thenReturn(repoConfig);
        when(repoConfig.isFederated()).thenReturn(true);
        when(binaryService.getBinary(anyString(), anyMap())).thenThrow(new BinaryNotFoundException("Binary not found"));
        // Act
        testDbExportBase.exportFile(fileInfo, targetParentDir, Collections.emptySet());
        // Assert
        verify(status).warn(
                eq("Skipping federated item 'federated-repo:path/to/file' - binary not yet available (sha1: abc123)"),
                any());
        verify(accumulator, never()).accumulateSkippedFile();
    }

    @Test
    void exportFile_shouldLogErrorSkipFile_whenBinaryNotFoundExceptionThrownAndRepositoryIsNotFederated() {
        // Arrange
        FileInfo fileInfo = mock(FileInfo.class);
        RepoPath repoPath = mock(RepoPath.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        File targetParentDir = new File("target");
        File expectedTargetFile = new File(targetParentDir, "test-file.txt");
        when(fileInfo.getRepoKey()).thenReturn("non-federated-repo");
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(fileInfo.getSha1()).thenReturn("def456");
        when(fileInfo.getName()).thenReturn("test-file.txt");
        when(fileInfo.getSize()).thenReturn(2048L);
        when(repoPath.toString()).thenReturn("non-federated-repo:path/to/file");
        when(fileService.exists(repoPath)).thenReturn(true);
        when(repositoryConfigService.getRepoConfigByKey("non-federated-repo")).thenReturn(repoConfig);
        when(repoConfig.isFederated()).thenReturn(false);
        when(binaryService.getBinary(anyString(), anyMap())).thenThrow(new BinaryNotFoundException("Binary not found"));
        // Act
        testDbExportBase.exportFile(fileInfo, targetParentDir, Collections.emptySet());
        // Assert
        verify(status).error(
                eq("Failed to export '" + expectedTargetFile.getAbsolutePath() + "' due to:Binary not found"),
                any(BinaryNotFoundException.class),
                any());
        verify(accumulator).accumulateSkippedFile();
    }

    @Test
    void exportFile_shouldLogErrorAndAccumulateSkippedFile_whenBinaryNotFoundExceptionThrownAndRepoConfigIsNull() {
        // Arrange
        FileInfo fileInfo = mock(FileInfo.class);
        RepoPath repoPath = mock(RepoPath.class);
        File targetParentDir = new File("target");
        File expectedTargetFile = new File(targetParentDir, "test-file.txt");
        when(fileInfo.getRepoKey()).thenReturn("unknown-repo");
        when(fileInfo.getRepoPath()).thenReturn(repoPath);
        when(fileInfo.getSha1()).thenReturn("ghi789");
        when(fileInfo.getName()).thenReturn("test-file.txt");
        when(fileInfo.getSize()).thenReturn(512L);
        when(repoPath.toString()).thenReturn("unknown-repo:path/to/file");
        when(fileService.exists(repoPath)).thenReturn(true);
        when(repositoryConfigService.getRepoConfigByKey("unknown-repo")).thenReturn(null);
        when(binaryService.getBinary(anyString(), anyMap())).thenThrow(new BinaryNotFoundException("Binary not found"));
        // Act
        testDbExportBase.exportFile(fileInfo, targetParentDir, Collections.emptySet());
        // Assert
        verify(status).error(
                eq("Failed to export '" + expectedTargetFile.getAbsolutePath() + "' due to:Binary not found"),
                any(BinaryNotFoundException.class),
                any());
        verify(accumulator).accumulateSkippedFile();
    }

    /**
     * Concrete test implementation of DbExportBase for testing purposes
     */
    static class TestDbExportBase extends DbExportBase {
        TestDbExportBase(ImportExportAccumulator accumulator) {
            super(accumulator);
        }
    }
}
