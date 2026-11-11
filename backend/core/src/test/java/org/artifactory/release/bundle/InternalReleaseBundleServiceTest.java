package org.artifactory.release.bundle;

import lombok.NonNull;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eli Skoran 02/09/2020
 */

@Test
public class InternalReleaseBundleServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactBundlesDao bundlesDao;
    @Mock
    private BundleBlobsDao blobsDao;
    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private ReleaseBundleDeleteTemplate releaseBundleDeleteTemplate;
    @Mock
    private ArtifactoryContext context;

    private InternalReleaseBundleService service;

    private ArtifactsBundleModel bundleModel;
    private List<BundleNode> bundleNodes;
    private Set<Long> nodeIdsWithArtifactsToDelete;
    private RepoPath exportedFileRepoPath;
    private RepoPath releaseBundleVersionEmptyFolder;
    private final RepoPath firstRepoPath = RepoPathFactory.create("path/to/repo/file1");
    private final RepoPath secondRepoPath = RepoPathFactory.create("another/path/to/repo/file2");
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() throws SQLException {
        mocks = MockitoAnnotations.openMocks(this);
        // Two mock services defined because of circular dependency innerService
        when(context.getArtifactoryHome()).thenReturn(homeStub);
        StorageSessionHolder.setSession(new SqlStorageSession());
        ArtifactoryContextThreadBinder.bind(context);

        service = new InternalReleaseBundleServiceImpl(
                bundlesDao,
                blobsDao,
                repoService
        );

        bundleModel = new ArtifactsBundleModel(
                111,
                "bundle1",
                "version1",
                BundleTransactionStatus.COMPLETE,
                BundleType.SOURCE,
                "release_bundles"
        );

        bundleNodes = List.of(
                new BundleNode(1234, 234, firstRepoPath.toPath(), 111, "originalDetails"),
                new BundleNode(1235, 235, secondRepoPath.toPath(), 111, "originalDetails")
        );

        nodeIdsWithArtifactsToDelete = Set.of(234L, 235L);

        exportedFileRepoPath = ReleaseBundleUtils.buildExportFileRepoPath("bundle1", "version1", "release_bundles");

        releaseBundleVersionEmptyFolder = RepoPathFactory.create(bundleModel.getStoringRepo(),
                bundleModel.getName() + "/" + bundleModel.getVersion() + "/");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        Mockito.verifyNoMoreInteractions(
                bundlesDao,
                blobsDao,
                repoService,
                authService,
                releaseBundleDeleteTemplate
        );
        mocks.close();
    }

    @Test
    public void testDeleteReleaseBundle_Complete() throws SQLException {
        prepareMocksForTest(bundleModel, firstRepoPath, secondRepoPath, releaseBundleVersionEmptyFolder,
                exportedFileRepoPath, true);
        service.deleteReleaseBundleInternal(bundleModel, bundleNodes, nodeIdsWithArtifactsToDelete);
        verifyExpectedBehavior(bundleModel, firstRepoPath, secondRepoPath, releaseBundleVersionEmptyFolder,
                exportedFileRepoPath, true);
    }

    @Test
    public void testDeleteReleaseBundle_NotExported() throws SQLException {
        prepareMocksForTest(bundleModel, firstRepoPath, secondRepoPath, releaseBundleVersionEmptyFolder,
                exportedFileRepoPath, false);
        service.deleteReleaseBundleInternal(bundleModel, bundleNodes, nodeIdsWithArtifactsToDelete);
        verifyExpectedBehavior(bundleModel, firstRepoPath, secondRepoPath, releaseBundleVersionEmptyFolder,
                exportedFileRepoPath, false);
    }

    @Test
    public void testDeleteTargetReleaseBundle() throws SQLException {
        ArtifactsBundleModel bundleModel = new ArtifactsBundleModel(
                113,
                "bundle2",
                "version2",
                BundleTransactionStatus.COMPLETE,
                BundleType.TARGET,
                "release_bundles"
        );
        List<BundleNode> bundleNodes = List.of(
                new BundleNode(12340, 2340, firstRepoPath.toPath(), 113, "originalDetails"),
                new BundleNode(12351, 2351, secondRepoPath.toPath(), 113, "originalDetails")
        );

        Set<Long> nodeIdsWithArtifactsToDelete = Set.of(2340L, 2351L);

        RepoPath exportedFileRepoPath =
                ReleaseBundleUtils.buildExportFileRepoPath("bundle2", "version2", "release_bundles");

        RepoPath releaseBundleVersionEmptyFolder = RepoPathFactory.create(bundleModel.getStoringRepo(),
                bundleModel.getName() + "/" + bundleModel.getVersion() + "/");

        prepareMocksForTest(bundleModel, firstRepoPath, secondRepoPath, releaseBundleVersionEmptyFolder,
                exportedFileRepoPath, false);
        service.deleteReleaseBundleInternal(bundleModel, bundleNodes, nodeIdsWithArtifactsToDelete);
        verifyExpectedBehavior(bundleModel, firstRepoPath, secondRepoPath, releaseBundleVersionEmptyFolder,
                exportedFileRepoPath, false);
    }

    private void prepareMocksForTest(@NonNull ArtifactsBundleModel bundleModel, @NonNull RepoPath firstRepoPath,
            @NonNull RepoPath secondRepoPath, @NonNull RepoPath releaseBundleVersionEmptyFolder,
            @NonNull RepoPath exportedFileRepoPath, boolean exportedFile) throws SQLException {

        boolean isTarget = BundleType.TARGET.equals(bundleModel.getType());

        doNothing().when(bundlesDao).deleteBundleNodes(bundleModel.getId());
        when(blobsDao.deleteBlob(bundleModel.getId())).thenReturn(true);
        when(bundlesDao.deleteArtifactsBundle(bundleModel.getId())).thenReturn(true);

        when(repoService.exists(firstRepoPath)).thenReturn(true);
        when(repoService.undeploy(firstRepoPath, true, true, isTarget))
                .thenReturn(new BasicStatusHolder());

        when(repoService.exists(secondRepoPath)).thenReturn(true);
        when(repoService.undeploy(secondRepoPath, true, true, isTarget))
                .thenReturn(new BasicStatusHolder());

        when(repoService.exists(exportedFileRepoPath)).thenReturn(exportedFile);
        if (exportedFile) {
            when(repoService.undeploy(exportedFileRepoPath)).thenReturn(new BasicStatusHolder());
        }

        when(repoService.exists(releaseBundleVersionEmptyFolder)).thenReturn(true);
        when(repoService.hasChildren(releaseBundleVersionEmptyFolder)).thenReturn(false);

        when(repoService.exists(firstRepoPath.getParent())).thenReturn(true);
        when(repoService.hasChildren(firstRepoPath.getParent())).thenReturn(false);

        when(repoService.exists(secondRepoPath.getParent())).thenReturn(true);
        when(repoService.hasChildren(secondRepoPath.getParent())).thenReturn(false);

        when(repoService.exists(Objects.requireNonNull(firstRepoPath.getParent()).getParent())).thenReturn(true);
        when(repoService.hasChildren(firstRepoPath.getParent().getParent())).thenReturn(false);

        when(repoService.exists(Objects.requireNonNull(secondRepoPath.getParent()).getParent())).thenReturn(true);
        when(repoService.hasChildren(secondRepoPath.getParent().getParent())).thenReturn(false);

        when(repoService.exists(
                Objects.requireNonNull(secondRepoPath.getParent().getParent()).getParent())).thenReturn(true);
        when(repoService.hasChildren(secondRepoPath.getParent().getParent().getParent())).thenReturn(false);
    }

    private void verifyExpectedBehavior(@NonNull ArtifactsBundleModel bundleModel, @NonNull RepoPath firstRepoPath,
            @NonNull RepoPath secondRepoPath, @NonNull RepoPath releaseBundleVersionEmptyFolder,
            @NonNull RepoPath exportedFileRepoPath, boolean fileExported) throws SQLException {
        boolean isTarget = BundleType.TARGET.equals(bundleModel.getType());

        // verify
        verify(bundlesDao).deleteBundleNodes(bundleModel.getId());
        verify(blobsDao).deleteBlob(bundleModel.getId());
        verify(bundlesDao).deleteArtifactsBundle(bundleModel.getId());

        verify(repoService).exists(firstRepoPath);
        verify(repoService).undeploy(firstRepoPath, true, true, isTarget);
        verify(repoService).exists(secondRepoPath);
        verify(repoService).undeploy(secondRepoPath, true, true, isTarget);

        //delete empty folders from target repositories
        verify(repoService).exists(firstRepoPath.getParent());
        verify(repoService).hasChildren(firstRepoPath.getParent());
        verify(repoService).undeploy(firstRepoPath.getParent(), true, true, true);

        verify(repoService).exists(secondRepoPath.getParent());
        verify(repoService).hasChildren(secondRepoPath.getParent());
        verify(repoService).undeploy(secondRepoPath.getParent(), true, true, true);

        verify(repoService).exists(Objects.requireNonNull(firstRepoPath.getParent()).getParent());
        verify(repoService).hasChildren(firstRepoPath.getParent().getParent());
        verify(repoService).undeploy(firstRepoPath.getParent().getParent(), true, true, true);

        verify(repoService).exists(Objects.requireNonNull(secondRepoPath.getParent()).getParent());
        verify(repoService).hasChildren(secondRepoPath.getParent().getParent());
        verify(repoService).undeploy(secondRepoPath.getParent().getParent(), true, true, true);

        verify(repoService).exists(Objects.requireNonNull(secondRepoPath.getParent().getParent()).getParent());
        verify(repoService).hasChildren(secondRepoPath.getParent().getParent().getParent());
        verify(repoService).undeploy(secondRepoPath.getParent().getParent().getParent(), true, true, true);

        verify(repoService).exists(exportedFileRepoPath);
        if (fileExported) {
            verify(repoService).undeploy(exportedFileRepoPath);
        }

        //delete empty folders from release_bundles repository
        verify(repoService).exists(releaseBundleVersionEmptyFolder);
        verify(repoService).hasChildren(releaseBundleVersionEmptyFolder);
        verify(repoService).undeploy(releaseBundleVersionEmptyFolder, true, true, true);

    }
}
