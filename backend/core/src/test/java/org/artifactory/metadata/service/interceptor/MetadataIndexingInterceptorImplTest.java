package org.artifactory.metadata.service.interceptor;

import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.md.PropertiesInfo;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataIndexingInterceptorImplTest {

    private enum BeforeDeleteNoMarkScenario {
        FOLDER,
        NOT_LEAD_ARTIFACT,
        NOT_MUTABLE_VFS_FILE,
        MUTABLE_VFS_FILE_ORIGINALLY_NEW
    }

    private enum AfterDeleteNoCallScenario {
        FOLDER,
        NOT_LEAD_ARTIFACT,
        NOT_MUTABLE_VFS_FILE,
        ORIGINAL_PROPERTIES_NOT_AVAILABLE
    }

    private MetadataIndexingInterceptorImpl interceptor;

    @Mock
    private MetadataServiceIndexHandler packageMetadataServiceIndexHandler;
    @Mock
    private LeadArtifactFacade leadArtifactFacade;
    @Mock
    private VfsItem fsItem;
    @Mock
    private MutableVfsFile mutableVfsFile;
    @Mock
    private FileInfo fileInfo;
    @Mock
    private RepoPath repoPath;
    @Mock
    private MutableStatusHolder statusHolder;
    @Mock
    private DeleteContext deleteContext;
    @Mock
    private PropertiesInfo originalProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        interceptor = new MetadataIndexingInterceptorImpl();
        interceptor.setPackageMetadataServiceIndexHandler(packageMetadataServiceIndexHandler);
        interceptor.setLeadArtifactFacade(leadArtifactFacade);
    }

    @ParameterizedTest
    @EnumSource(BeforeDeleteNoMarkScenario.class)
    void beforeDelete_shouldNotMarkOriginalProperties_whenSkipped(BeforeDeleteNoMarkScenario scenario) {
        when(fsItem.isFolder()).thenReturn(false);
        when(fsItem.getRepoPath()).thenReturn(repoPath);
        when(mutableVfsFile.getRepoPath()).thenReturn(repoPath);
        when(leadArtifactFacade.isLeadArtifact(repoPath)).thenReturn(true);
        VfsItem item = fsItem;
        switch (scenario) {
            case FOLDER:
                when(fsItem.isFolder()).thenReturn(true);
                break;
            case NOT_LEAD_ARTIFACT:
                when(leadArtifactFacade.isLeadArtifact(repoPath)).thenReturn(false);
                break;
            case NOT_MUTABLE_VFS_FILE:
                when(fsItem.getRepoPath()).thenReturn(repoPath);
                break;
            case MUTABLE_VFS_FILE_ORIGINALLY_NEW:
                when(mutableVfsFile.isOriginallyNew()).thenReturn(true);
                item = mutableVfsFile;
                break;
        }
        interceptor.beforeDelete(item, statusHolder, deleteContext);
        verify(mutableVfsFile, never()).markOriginalProperties();
    }

    @Test
    void beforeDelete_shouldMarkOriginalProperties_whenMutableVfsFileAndNotOriginallyNew() {
        when(mutableVfsFile.isFolder()).thenReturn(false);
        when(mutableVfsFile.getRepoPath()).thenReturn(repoPath);
        when(leadArtifactFacade.isLeadArtifact(repoPath)).thenReturn(true);
        when(mutableVfsFile.isOriginallyNew()).thenReturn(false);

        interceptor.beforeDelete(mutableVfsFile, statusHolder, deleteContext);

        verify(mutableVfsFile, times(1)).markOriginalProperties();
    }

    @ParameterizedTest
    @EnumSource(AfterDeleteNoCallScenario.class)
    void afterDelete_shouldNotCallDeletePackageAsync_whenSkipped(AfterDeleteNoCallScenario scenario) {
        VfsItem item = fsItem;
        when(leadArtifactFacade.isLeadArtifact(repoPath)).thenReturn(true);
        when(mutableVfsFile.getRepoPath()).thenReturn(repoPath);
        when(fsItem.getRepoPath()).thenReturn(repoPath);
        when(fsItem.isFolder()).thenReturn(false);
        switch (scenario) {
            case FOLDER:
                when(fsItem.isFolder()).thenReturn(true);
                break;
            case NOT_LEAD_ARTIFACT:
                when(leadArtifactFacade.isLeadArtifact(repoPath)).thenReturn(false);
                break;
            case NOT_MUTABLE_VFS_FILE:
                break;
            case ORIGINAL_PROPERTIES_NOT_AVAILABLE:
                when(mutableVfsFile.isFolder()).thenReturn(false);
                when(mutableVfsFile.getInfo()).thenReturn(fileInfo);
                when(mutableVfsFile.isOriginalPropertiesAvailable()).thenReturn(false);
                item = mutableVfsFile;
                break;
        }
        interceptor.afterDelete(item, statusHolder, deleteContext);
        verify(packageMetadataServiceIndexHandler, never()).deletePackageAsync(Mockito.any(), Mockito.any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void afterDelete_shouldCallDeletePackageAsync_withOriginalPropertiesOrNull(boolean getOriginalPropertiesThrows) {
        Map<String, Collection<String>> propsMap = Map.of("build.name", List.of("my-build"));
        when(mutableVfsFile.isFolder()).thenReturn(false);
        when(mutableVfsFile.getRepoPath()).thenReturn(repoPath);
        when(mutableVfsFile.getInfo()).thenReturn(fileInfo);
        when(leadArtifactFacade.isLeadArtifact(repoPath)).thenReturn(true);
        when(mutableVfsFile.isOriginalPropertiesAvailable()).thenReturn(true);
        if (getOriginalPropertiesThrows) {
            when(mutableVfsFile.getOriginalProperties())
                    .thenThrow(new IllegalStateException("No original properties"));
        } else {
            when(mutableVfsFile.getOriginalProperties()).thenReturn(originalProperties);
            when(originalProperties.asMap()).thenReturn(propsMap);
        }

        interceptor.afterDelete(mutableVfsFile, statusHolder, deleteContext);

        if (getOriginalPropertiesThrows) {
            verify(packageMetadataServiceIndexHandler, times(1)).deletePackageAsync(eq(fileInfo), isNull());
        } else {
            verify(packageMetadataServiceIndexHandler, times(1)).deletePackageAsync(eq(fileInfo), eq(propsMap));
        }
    }
}
