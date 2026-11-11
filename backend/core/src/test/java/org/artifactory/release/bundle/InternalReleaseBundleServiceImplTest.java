package org.artifactory.release.bundle;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.api.security.SecurityService;
import org.artifactory.bundle.BundleType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.fs.session.SqlStorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.artifactory.bundle.BundleTransactionStatus.COMPLETE;
import static org.artifactory.bundle.BundleTransactionStatus.INPROGRESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Omri Ziv
 */

@Test
public class InternalReleaseBundleServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactBundlesDao bundlesDao;
    @Mock
    private BundleBlobsDao blobsDao;
    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private SecurityService securityService;
    @Mock
    private ReleaseBundleDeleteTemplate releaseBundleDeleteTemplate;

    private InternalReleaseBundleServiceImpl service;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() throws SQLException {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(context.getSecurityService()).thenReturn(securityService);
        when(context.getArtifactoryHome()).thenReturn(homeStub);
        StorageSessionHolder.setSession(new SqlStorageSession());
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    private void teardown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testDeleteWithContent() throws SQLException {
        service = spy(new InternalReleaseBundleServiceImpl(bundlesDao, blobsDao,
                repoService));
        service.releaseBundleDeleteTemplate = releaseBundleDeleteTemplate;
        service.deleteReleaseBundleInternal(createArtifactsBundleModel(), createBundleNodes(2),
                createNodeIds(2));
        verify(service, times(1)).deleteBundleContent(any(), any(), any());
        verify(releaseBundleDeleteTemplate, times(1)).deleteBundle(any(), any(), any());
        verify(blobsDao).deleteBlob(1);
        verify(bundlesDao).deleteArtifactsBundle(1);
    }

    @Test
    public void testDeleteWithContentDerby() throws SQLException {
        InternalReleaseBundleServiceImpl mockService = mock(InternalReleaseBundleServiceImpl.class);
        service = spy(new InternalReleaseBundleServiceImpl(bundlesDao, blobsDao,
                repoService));
        service.deleteReleaseBundleInternal(createArtifactsBundleModel(), createBundleNodes(2),
                createNodeIds(2));
        verify(service, times(1))
                .deleteBundleContent(any(), any(), any());
        verify(bundlesDao, times(1))
                .deleteBundleNodes(1);
        verify(blobsDao).deleteBlob(1);
        verify(bundlesDao).deleteArtifactsBundle(1);
    }

    @Test
    public void testDeleteWithContentInProgress() throws SQLException {
        service = spy(new InternalReleaseBundleServiceImpl(bundlesDao, blobsDao,
                repoService));
        service.releaseBundleDeleteTemplate = releaseBundleDeleteTemplate;
        ArtifactsBundleModel artifactsBundleModel = createArtifactsBundleModel();
        artifactsBundleModel.setStatus(INPROGRESS);
        service.deleteReleaseBundleInternal(artifactsBundleModel, createBundleNodes(2),
                createNodeIds(2));
        verify(service, times(1)).deleteBundleContent(any(), any(), any());
        verify(bundlesDao, times(1))
                .deleteBundleNodes(1);
        verify(releaseBundleDeleteTemplate, times(0)).deleteBundle(any(), any(), any());
        verify(blobsDao).deleteBlob(1);
        verify(bundlesDao).deleteArtifactsBundle(1);
    }


    private ArtifactsBundleModel createArtifactsBundleModel() {
        ArtifactsBundleModel artifactsBundleModel = new ArtifactsBundleModel();
        artifactsBundleModel.setId(1);
        artifactsBundleModel.setName("rb_01");
        artifactsBundleModel.setStatus(COMPLETE);
        artifactsBundleModel.setVersion("0.1");
        artifactsBundleModel.setStoringRepo("repo");
        artifactsBundleModel.setType(BundleType.SOURCE);
        return artifactsBundleModel;
    }

    private List<BundleNode> createBundleNodes(int amount) {
        if (amount == 0) {
            return Collections.emptyList();
        }
        return IntStream.rangeClosed(1, amount)
                .mapToObj(number -> {
                    BundleNode bundleNode = new BundleNode();
                    bundleNode.setBundleId(1);
                    bundleNode.setId(number);
                    bundleNode.setRepoPath("a:path_" + number);
                    return bundleNode;
                })
                .collect(Collectors.toList());
    }

    private Set<Long> createNodeIds(int amount) {
        if (amount == 0) {
            return Collections.emptySet();
        }
        return IntStream.rangeClosed(1, amount)
                .mapToObj(number -> (long) amount)
                .collect(Collectors.toSet());
    }

}
