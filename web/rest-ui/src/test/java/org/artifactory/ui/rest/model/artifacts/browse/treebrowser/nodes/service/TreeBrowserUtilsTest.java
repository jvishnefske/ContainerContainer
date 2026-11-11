package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FolderInfo;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.FolderNode;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.VirtualRemoteFolderNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TreeBrowserUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String VIRTUAL_REPO_KEY = "docker-virtual";
    private static final String REAL_REPO_KEY = "docker-local";
    private static final String FOLDER_PATH = "ubuntu/latest";

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private DockerAddon dockerAddon;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private VirtualRepoConfig repoConfig;
    @Mock
    private VirtualRepo virtualRepo;
    @Mock
    private FolderInfo folderInfo;
    @Mock
    private VirtualRepoConfig virtualRepoConfig;

    private AutoCloseable closable;

    @BeforeEach
    void setUp() {
        closable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.isAddonSupported(any())).thenReturn(true);
        when(addonsManager.addonByType(DockerAddon.class)).thenReturn(dockerAddon);

        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.displayIconsOnVirtualReposEnabled))
                .thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repositoryService.getRepoConfigByKey(any())).thenReturn(repoConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        if (closable != null) {
            closable.close();
        }
    }

    @Test
    void testBuildVirtualItemFolder() {
        final RepoPath realRepoPath = RepoPathFactory.create(REAL_REPO_KEY, FOLDER_PATH);
        when(folderInfo.getRepoPath()).thenReturn(realRepoPath);
        final FolderNode originalNode = new FolderNode(folderInfo, "latest", "local");
        when(virtualRepo.getRepoConfig()).thenReturn(virtualRepoConfig);
        when(virtualRepoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(internalRepositoryService.virtualRepositoryByKey(VIRTUAL_REPO_KEY)).thenReturn(virtualRepo);
        when(repositoryService.getRepoConfigByKey(REAL_REPO_KEY)).thenReturn(virtualRepoConfig);

        final BaseNode result = TreeBrowserUtils.buildVirtualItem(originalNode, VIRTUAL_REPO_KEY);

        assertTrue(result instanceof VirtualRemoteFolderNode);
        assertEquals(VIRTUAL_REPO_KEY, result.getRepoKey());
    }

    @Test
    void testBuildVirtualItemFile() {
        final RepoPath realRepoPath = RepoPathFactory.create(REAL_REPO_KEY, "ubuntu/latest/manifest.json");
        final BaseNode originalNode = new BaseNode(realRepoPath) {
            @Override
            public boolean isFolder() {
                return false;
            }
        };
        originalNode.setText("manifest.json");
        originalNode.setRepoPkgType(RepoType.Docker);

        when(repositoryService.getRepoConfigByKey(REAL_REPO_KEY)).thenReturn(repoConfig);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);

        final BaseNode result = TreeBrowserUtils.buildVirtualItem(originalNode, VIRTUAL_REPO_KEY);

        assertNotNull(result);
        assertEquals(VIRTUAL_REPO_KEY, result.getRepoKey());
    }
}