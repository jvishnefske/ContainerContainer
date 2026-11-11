package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.debianview;

import com.jfrog.ph.acl.service.PackageRepoService;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.debian.DebianAddon;
import org.artifactory.addon.debian.DebianInfo;
import org.artifactory.addon.debian.DebianMetadataInfo;
import org.artifactory.addon.debian.DscInfo;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.debian.DebianArtifactMetadataInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Priyanshu Gupta
 */
@ExtendWith(MockitoExtension.class)
class DebianViewServiceTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private PackageRepoService repoService;
    @Mock
    private ArtifactoryRestRequest<DebianArtifactMetadataInfo> artifactoryRequest;
    @Mock
    private RestResponse<DebianArtifactMetadataInfo> artifactoryResponse;
    @Mock
    private DebianArtifactMetadataInfo debianArtifactInfoModel;
    @Mock
    private RepoPath repoPath;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private DebianAddon debianAddon;
    @Mock
    private DebianMetadataInfo debianMetadataInfo;
    @Mock
    private ArtifactoryContext artifactoryContext;

    @InjectMocks
    private DebianViewService debianViewService;

    private MockedStatic<ContextHelper> contextHelperMockedStatic;
    private MockedStatic<InternalRepoPathFactory> internalRepoPathFactoryMockedStatic;

    private static final String TEST_REPO_KEY = "debian-local";

    @BeforeEach
    void setUp() {
        contextHelperMockedStatic = Mockito.mockStatic(ContextHelper.class);
        contextHelperMockedStatic.when(ContextHelper::get).thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        internalRepoPathFactoryMockedStatic = Mockito.mockStatic(InternalRepoPathFactory.class);
        internalRepoPathFactoryMockedStatic.when(() -> InternalRepoPathFactory.create(anyString(), anyString()))
                .thenReturn(repoPath);

        when(artifactoryRequest.getImodel()).thenReturn(debianArtifactInfoModel);
        when(debianArtifactInfoModel.getRepoKey()).thenReturn(TEST_REPO_KEY);
    }

    @AfterEach
    void tearDown() {
        contextHelperMockedStatic.close();
        internalRepoPathFactoryMockedStatic.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"some/path/file.deb", "some/path/package.ipk"})
    void executeCallsFetchDebianMetaDataAndSetsInfoSuccessfully(String filePath) {
        when(debianArtifactInfoModel.getPath()).thenReturn(filePath);
        when(repoService.isVirtual(TEST_REPO_KEY)).thenReturn(false);
        when(authorizationService.canRead(repoPath)).thenReturn(true);
        when(addonsManager.addonByType(DebianAddon.class)).thenReturn(debianAddon);
        when(debianAddon.getDebianMetadataInfo(repoPath)).thenReturn(debianMetadataInfo);

        DebianInfo mockDebInfo = Mockito.mock(DebianInfo.class);
        List<String> mockDebDependencies = List.of("a", "b");
        String mockFailReason = "Mock Fail Reason";

        when(debianMetadataInfo.getDebianInfo()).thenReturn(mockDebInfo);
        when(debianMetadataInfo.getDebianDependencies()).thenReturn(mockDebDependencies);
        when(debianMetadataInfo.getFailReason()).thenReturn(mockFailReason);

        debianViewService.execute(artifactoryRequest, artifactoryResponse);
        verify(artifactoryResponse).iModel(debianArtifactInfoModel);
        verify(debianArtifactInfoModel).setDebianInfo(mockDebInfo);
        verify(debianArtifactInfoModel).setDebianDependencies(mockDebDependencies);
        verify(debianArtifactInfoModel).setIndexFailureReason(mockFailReason);
        verify(debianArtifactInfoModel).clearRepoData();
        verify(debianArtifactInfoModel, never()).setDscInfo(any());
    }

    @Test
    void executeCallsFetchDebianMetaDataForDscFileSuccessfully() {
        when(debianArtifactInfoModel.getPath()).thenReturn("some/path/sourcepackage.dsc");
        when(repoService.isVirtual(TEST_REPO_KEY)).thenReturn(false);
        when(authorizationService.canRead(repoPath)).thenReturn(true);
        when(addonsManager.addonByType(DebianAddon.class)).thenReturn(debianAddon);
        when(debianAddon.getDebianSourceMetadataInfo(repoPath, true)).thenReturn(debianMetadataInfo);
        when(debianMetadataInfo.getDscInfo()).thenReturn(new DscInfo());

        debianViewService.execute(artifactoryRequest, artifactoryResponse);
        verify(artifactoryResponse).iModel(debianArtifactInfoModel);
        verify(debianArtifactInfoModel).setDscInfo(any(DscInfo.class));
        verify(debianArtifactInfoModel).clearRepoData();
    }
}