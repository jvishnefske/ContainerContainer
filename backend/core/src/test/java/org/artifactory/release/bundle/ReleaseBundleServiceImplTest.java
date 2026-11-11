package org.artifactory.release.bundle;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jfrog.distribution.handler.acl.bridge.DistributionHandlerBridgeToModule;
import com.jfrog.distribution.handler.acl.model.actions.TargetBundleActionsBaseModel;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.concurrent.BasicFuture;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.component.ComponentDetailsFetcher;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseArtifact;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.bundle.BundleNameAndRepo;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.release.bundle.exception.ReleaseBundleDeletionException;
import org.artifactory.release.bundle.interceptor.ReleaseBundleInterceptors;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.RepositoryServiceImpl;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.artifactory.storage.db.bundle.model.DBBundleResult;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.jfrog.model.api.distribution.handler.actions.TargetBundleAction;
import org.jfrog.storage.StorageException;
import org.joda.time.DateTime;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.releaseBundleRetryDeleteSharedArtifacts;
import static org.artifactory.release.bundle.ReleaseBundleServiceImpl.COULD_NOT_DELETE_RB_ERROR_MSG;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_DISTRIBUTION_REPO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * @author Eli Skoran 04/05/2020
 */
@Test
public class ReleaseBundleServiceImplTest {

    @Mock
    ArtifactBundlesDao bundlesDao;
    @Mock
    BundleBlobsDao blobsDao;
    @Mock
    NodesDao nodesDao;
    @Mock
    RepositoryServiceImpl repoService;
    @Mock
    PropertiesService propertiesService;
    @Mock
    ComponentDetailsFetcher componentDetailsFetcher;
    @Mock
    AuthorizationService authorizationService;
    @Mock
    SecurityService securityService;
    @Mock
    InternalReleaseBundleService internalReleaseBundleService;
    @Mock
    FileService fileService;
    @Mock
    ReleaseBundleInterceptors releaseBundleInterceptors;
    MeterRegistry metric = new SimpleMeterRegistry();
    @Mock
    DistributionHandlerBridgeToModule bridgeToModule;

    @Captor
    ArgumentCaptor<TargetBundleActionsBaseModel> actionsBaseModelCaptor;

    private AutoCloseable closable;

    private final String bundleName = "rb_name";
    private final String bundleVersion = "rb_version";
    private final long bundleId = 111L;
    private final String bundleSignature = "hf75jwg659klfhw8";
    private final String bundleSourceServiceId = "jfrt4876av35lf";
    private final String currentUser = "dummyUser";

    private ReleaseBundleServiceImpl service;

    @BeforeMethod
    public void setup() throws SQLException {
        closable = MockitoAnnotations.openMocks(this);
        metric.clear();

        service = new ReleaseBundleServiceImpl(
                bundlesDao,
                blobsDao,
                nodesDao,
                repoService,
                propertiesService,
                componentDetailsFetcher,
                authorizationService,
                securityService,
                internalReleaseBundleService,
                fileService,
                releaseBundleInterceptors,
                metric,
                bridgeToModule);

        service.executorService = new SyncedExecutorService();
        service.initialTimeBetweenRetries = 1;
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties properties = Mockito.mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
        when(properties.getBooleanProperty(releaseBundleRetryDeleteSharedArtifacts)).thenReturn(false);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(bundlesDao,
                blobsDao,
                nodesDao,
                repoService,
                propertiesService,
                componentDetailsFetcher,
                authorizationService,
                internalReleaseBundleService,
                fileService,
                releaseBundleInterceptors,
                bridgeToModule);
        closable.close();
        CurrentActivity.ACTOR.remove();
    }

    public void testCopyBundleArtifactsAsync_basic() throws SQLException {
        when(propertiesService.setProperties(any(), any(), anyBoolean())).thenReturn(true);
        when(nodesDao.getNodeId(any())).thenReturn(111111L);
        when(bundlesDao.create(any(BundleNode.class))).thenReturn(1);
        when(bundlesDao.tryToSetBundleStatus(any(), any(), any(), any())).thenReturn(BundleTransactionStatus.COMPLETE);

        service.copyBundleArtifactsAsync(getTestModel(), "dummyRepo", getTestMapping());

        verify(propertiesService, times(2)).setProperties(any(), any(), anyBoolean());
        verify(nodesDao, times(2)).getNodeId(any());
        verify(bundlesDao, times(2)).create(any(BundleNode.class));
        verify(bundlesDao, times(1)).tryToSetBundleStatus(any(), any(),
                any(), eq(BundleTransactionStatus.COMPLETE));
        verify(repoService, times(2))
                .copy(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(componentDetailsFetcher, times(2)).calcComponentDetails(any());

    }

    public void testCopyBundleArtifactsAsync_failStore() throws SQLException {
        when(propertiesService.setProperties(any(), any(), anyBoolean())).thenReturn(true);
        when(nodesDao.getNodeId(any())).thenReturn(111111L);
        when(bundlesDao.create(any(BundleNode.class))).thenThrow(new SQLException(
                "MySQLTransactionRollbackException: Lock wait timeout exceeded; try restarting transaction"));
        when(bundlesDao.tryToSetBundleStatus("platform", "7.5.1", BundleType.SOURCE, BundleTransactionStatus.FAILED))
                .thenReturn(BundleTransactionStatus.FAILED);

        service.copyBundleArtifactsAsync(getTestModel(), "dummyRepo", getTestMapping());

        verify(propertiesService, times(1)).setProperties(any(), any(), anyBoolean());
        verify(nodesDao, times(1)).getNodeId(any());
        verify(bundlesDao, times(1)).create(any(BundleNode.class));
        verify(bundlesDao, times(1))
                .tryToSetBundleStatus("platform", "7.5.1", BundleType.SOURCE, BundleTransactionStatus.FAILED);
        verify(repoService).copy(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(componentDetailsFetcher).calcComponentDetails(any());
    }

    @Test
    public void testIsReleaseBundleExist() throws SQLException {
        when(bundlesDao.isReleaseBundleExist("rb1")).thenReturn(true);
        Assert.assertTrue(service.isReleaseBundleExistByName("rb1"));
        verify(bundlesDao).isReleaseBundleExist("rb1");
    }

    @Test
    public void testIsReleaseBundleExist_error() throws SQLException {
        when(bundlesDao.isReleaseBundleExist("rb1")).thenThrow(new SQLException());
        assertThrows(StorageException.class, () -> service.isReleaseBundleExistByName("rb1"));
        verify(bundlesDao).isReleaseBundleExist("rb1");
    }

    @Test
    public void testCreateBundleNodes() throws SQLException {
        List<RepoPath> repoPaths = Lists.newArrayList(
                new RepoPathImpl("repo1", "my/path/file1"),
                new RepoPathImpl("repo1", "my/path/file2"));
        long bundleNodeId = 555;
        Node node1 = Mockito.mock(Node.class);
        Node node2 = Mockito.mock(Node.class);
        when(node1.getNodeId()).thenReturn(1L);
        when(node2.getNodeId()).thenReturn(2L);
        Map<NodePath, Node> map = new HashMap<>();
        List<NodePath> nodePaths = NodePath.fromRepoPaths(repoPaths);
        map.put(nodePaths.get(0), node1);
        map.put(nodePaths.get(1), node2);
        when(nodesDao.get(NodePath.fromRepoPaths(repoPaths))).thenReturn(map);
        service.createBundleNodes(bundleNodeId, repoPaths);
        List<BundleNode> bundleNodesToCreate = new ArrayList<>();
        for (Map.Entry<NodePath, Node> entry : map.entrySet()) {
            BundleNode bundleNode = new BundleNode();
            bundleNode.setBundleId(bundleNodeId);
            bundleNode.setNodeId(entry.getValue().getNodeId());
            bundleNode.setRepoPath(entry.getKey().toRepoPath().toPath());
            bundleNodesToCreate.add(bundleNode);
        }
        verify(nodesDao).get(anyList());
        verify(bundlesDao).createBulk(bundleNodesToCreate);
    }

    public void testCopyBundleArtifactsAsync_failStoreWithRetries() throws SQLException {
        when(propertiesService.setProperties(any(), any(), anyBoolean())).thenReturn(true);
        when(nodesDao.getNodeId(any())).thenReturn(111111L);
        when(bundlesDao.create(any(BundleNode.class))).thenThrow(new SQLException(
                "MySQLTransactionRollbackException: Lock wait timeout exceeded; try restarting transaction"));
        // fail for the first few tries, then succeed..
        when(bundlesDao.tryToSetBundleStatus("platform", "7.5.1", BundleType.SOURCE, BundleTransactionStatus.FAILED))
                .thenThrow(new SQLException(
                        "MySQLTransactionRollbackException: Lock wait timeout exceeded; try restarting transaction"))
                .thenThrow(new SQLException(
                        "MySQLTransactionRollbackException: Lock wait timeout exceeded; try restarting transaction"))
                .thenThrow(new SQLException(
                        "MySQLTransactionRollbackException: Lock wait timeout exceeded; try restarting transaction"))
                .thenReturn(BundleTransactionStatus.FAILED);

        service.copyBundleArtifactsAsync(getTestModel(), "dummyRepo", getTestMapping());

        verify(propertiesService, times(1)).setProperties(any(), any(), anyBoolean());
        verify(nodesDao, times(1)).getNodeId(any());
        verify(bundlesDao, times(1)).create(any(BundleNode.class));
        verify(bundlesDao, times(4))
                .tryToSetBundleStatus("platform", "7.5.1", BundleType.SOURCE, BundleTransactionStatus.FAILED);
        verify(repoService).copy(any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(componentDetailsFetcher).calcComponentDetails(any());
    }

    public void testCleanOrphanArtifactsNoArtifactsToDelete() throws SQLException {
        when(bundlesDao.getOrphanRBVRepoPathsFromIntransit()).thenReturn(new HashSet<>());
        when(bundlesDao.getUnidentifiedRepoPathsOnInTransit()).thenReturn(new HashSet<>());
        service.cleanOrphanAndUnidentifiedArtifacts();
        verify(bundlesDao, times(1)).getOrphanRBVRepoPathsFromIntransit();
        verify(bundlesDao, times(1)).getUnidentifiedRepoPathsOnInTransit();
        verify(repoService, times(0)).delete(any());
    }

    @Test
    public void testSetTargetBundleKeep_happyFlow() throws SQLException {
        // setup
        String bundleName = "b1";
        String bundleVersion = "v1";

        when(bundlesDao.setKeepBundle(bundleName, bundleVersion, true)).thenReturn(1);

        // test
        boolean success = service.setTargetBundleKeep(bundleName, bundleVersion, true);

        // verify
        verify(bundlesDao, times(1)).setKeepBundle(bundleName, bundleVersion, true);

        assertTrue(success);
    }

    @Test
    public void testSetTargetBundleKeep_errorHandling() throws SQLException {
        // setup
        String bundleName = "b1";
        String bundleVersion = "v1";

        when(bundlesDao.setKeepBundle(bundleName, bundleVersion, true)).thenThrow(
                new SQLException("sql something"));

        // test
        assertThrows(StorageException.class, () -> service.setTargetBundleKeep(bundleName, bundleVersion, true));

        // verify
        verify(bundlesDao, times(1)).setKeepBundle(bundleName, bundleVersion, true);

    }

    @Test
    public void testGetTargetBundleKeep_happyFlow() throws SQLException {
        // setup
        String bundleName = "b1";
        String bundleVersion = "v1";

        when(bundlesDao.isKeepBundle(bundleName, bundleVersion)).thenReturn(true);

        // test
        boolean isKeep = service.getTargetBundleKeep(bundleName, bundleVersion);

        // verify
        verify(bundlesDao, times(1)).isKeepBundle(bundleName, bundleVersion);

        assertTrue(isKeep);
    }

    @Test
    public void testGetTargetBundleKeep_errorHandling() throws SQLException {
        // setup
        String bundleName = "b1";
        String bundleVersion = "v1";

        when(bundlesDao.isKeepBundle(bundleName, bundleVersion)).thenThrow(
                new SQLException("sql something"));

        // test
        assertThrows(StorageException.class, () -> service.getTargetBundleKeep(bundleName, bundleVersion));

        // verify
        verify(bundlesDao, times(1)).isKeepBundle(bundleName, bundleVersion);

    }

    @Test
    public void testGetBundleSourceServiceId_happyFlow() throws SQLException {
        // setup
        String bundleName = "rb_target";
        String bundleVersion = "v1";
        String expectedServiceId = "serviceId";

        when(bundlesDao.getSourceServiceId(bundleName, bundleVersion)).thenReturn(expectedServiceId);

        // test
        String serviceId = service.getBundleSourceServiceId(bundleName, bundleVersion);

        // verify
        verify(bundlesDao, times(1)).getSourceServiceId(bundleName, bundleVersion);

        assertEquals(expectedServiceId, serviceId);
    }

    @Test
    public void testGetBundleSourceServiceId_notFound() throws SQLException {
        // setup
        String bundleName = "rb_target";
        String bundleVersion = "v1";

        when(bundlesDao.getSourceServiceId(bundleName, bundleVersion)).thenReturn(null);

        // test
        String serviceId = service.getBundleSourceServiceId(bundleName, bundleVersion);

        // verify
        verify(bundlesDao, times(1)).getSourceServiceId(bundleName, bundleVersion);

        assertNull(serviceId);
    }

    @Test
    public void testGetBundleSourceServiceId_errorHandling() throws SQLException {
        // setup
        String bundleName = "rb_target";
        String bundleVersion = "v3";

        when(bundlesDao.getSourceServiceId(bundleName, bundleVersion)).thenThrow(new SQLException());

        // test
        assertThrows(StorageException.class, () -> service.getBundleSourceServiceId(bundleName, bundleVersion));

        // verify
        verify(bundlesDao, times(1)).getSourceServiceId(bundleName, bundleVersion);

    }

    @Test
    public void testGetBundleSourceServiceId_nonNull() {
        assertThrows(NullPointerException.class, () -> service.getBundleSourceServiceId(null, ""));
        assertThrows(NullPointerException.class, () -> service.getBundleSourceServiceId("", null));
        assertThrows(NullPointerException.class, () -> service.getBundleSourceServiceId(null, null));
    }

    @Test(dataProvider = "OrphanArtifactsTestDataProvider")
    public void testCleanOrphanArtifactsWithArtifactsToDelete(boolean hasWarning, boolean hasErrors,
            boolean expectGetMsg) throws SQLException {
        RepoPath repoPathToDelete1 = RepoPathFactory.create("_intransit/path/to/dir/artifact_to_delete_1.ext");
        RepoPath repoPathToDelete2 = RepoPathFactory.create("_intransit/path/to/dir/artifact_to_delete_2.ext");

        Set<RepoPath> orphanRepoPathsToDelete = new HashSet<>();
        orphanRepoPathsToDelete.add(repoPathToDelete1);

        Set<RepoPath> invalidRepoPathsToDelete = new HashSet<>();
        invalidRepoPathsToDelete.add(repoPathToDelete2);

        MoveMultiStatusHolder statusHolder = Mockito.mock(MoveMultiStatusHolder.class);
        when(bundlesDao.getOrphanRBVRepoPathsFromIntransit()).thenReturn(orphanRepoPathsToDelete);
        when(bundlesDao.getUnidentifiedRepoPathsOnInTransit()).thenReturn(invalidRepoPathsToDelete);
        when(repoService.delete(orphanRepoPathsToDelete, true, false)).thenReturn(statusHolder);
        when(statusHolder.hasWarnings()).thenReturn(hasWarning);
        when(statusHolder.hasErrors()).thenReturn(hasErrors);

        service.cleanOrphanAndUnidentifiedArtifacts();
        verify(bundlesDao, times(1)).getOrphanRBVRepoPathsFromIntransit();
        verify(repoService, times(1)).delete(orphanRepoPathsToDelete, true, false);

        verify(bundlesDao, times(1)).getUnidentifiedRepoPathsOnInTransit();
        invalidRepoPathsToDelete.addAll(orphanRepoPathsToDelete);
        verify(repoService, times(1)).delete(invalidRepoPathsToDelete, true, false);
        verify(statusHolder, times(expectGetMsg ? 1 : 0)).getStatusMsg();
    }


    @Test
    public void testDeleteNotExistingReleaseBundle() throws SQLException {
        when(bundlesDao.getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET))
                .thenReturn(null);
        assertThrows(NotFoundException.class,
                () -> service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET));
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);

        verifyReportedMetric(0, null, null);
    }

    @Test
    public void testDeleteReleaseBundleExceptionWhileReadingFromDB() throws SQLException {
        String errorMessage = "Error deleting release bundle";
        when(bundlesDao.getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET))
                .thenThrow(new SQLException(errorMessage));
        assertThrows(StorageException.class,
                () -> service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET));
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestFailed(any(),
                eq("Error deleting release bundle. See logs for further details"));

        verifyReportedMetric(0, null, null);
    }

    @Test
    public void testDeleteReleaseBundleNoRepoDeletePermission() throws SQLException {
        prepareMocksForReleaseBundleDeletion(false, false);
        assertThrows(ForbiddenWebAppException.class,
                () -> service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET));
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestFailed(any(),
                eq("Delete release bundle " + bundleName + ":" + bundleVersion + " is forbidden"));
        verify(authorizationService).canDelete(any());
        verify(bridgeToModule).isRetentionFeatureEnabled();

        verifyReportedMetric(0, null, null);
    }

    @Test
    public void testDeleteReleaseBundleSuccessfully() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);

        // test
        service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET);

        // verify
        verifyMocksForDeleteReleaseBundleMethod();

        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.EXTERNAL_USER);
    }

    @Test
    public void testDeleteReleaseBundleSuccessfullyWhenRetentionFeatureIsDisabled() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(false);

        // test
        service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET);

        // verify
        verifyMocksForDeleteReleaseBundleMethod();

        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.EXTERNAL_USER);
    }

    public void testDeleteSourceReleaseBundle() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.DISTRIBUTION_SERVICE);
        DBArtifactsBundle artifactsBundle = getSimpleDbArtifactsBundle(false);
        artifactsBundle.setType(BundleType.SOURCE);

        when(bundlesDao.getArtifactsBundle(bundleName, bundleVersion, BundleType.SOURCE))
                .thenReturn(artifactsBundle);
        when(bundlesDao.getArtifactsBundle(bundleId)).thenReturn(artifactsBundle);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDelete(any())).thenReturn(true);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);
        when(bridgeToModule.isActionsFeatureEnabled()).thenReturn(true);

        // test
        service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.SOURCE);

        // verify
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.SOURCE);
        verify(bridgeToModule).isRetentionFeatureEnabled();
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestStarted(any());
        verify(internalReleaseBundleService).deleteReleaseBundleInternal(any(), any(), any());
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestCompleted(any());
        verify(bundlesDao).getArtifactsBundle(bundleId);
        verify(bundlesDao).getBundleNodes(bundleId);
        verify(authorizationService).canDelete(any());
        verify(bridgeToModule).isActionsFeatureEnabled();

        verifyReportedMetric(1, BundleType.SOURCE, ReleaseBundleActors.DISTRIBUTION_SERVICE);
    }

    @Test
    public void testTryDeleteReleaseBundleMarkedAsKeep() throws SQLException {
        // setup
        prepareMocksForReleaseBundleDeletion(true, true);
        String expectedMessage = "Release Bundle " + bundleName + "/" + bundleVersion +
                " cannot be deleted because it was marked as 'Keep'. " +
                "To delete the Release Bundle, clear the 'Keep' selection.";
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);

        // test
        ReleaseBundleDeletionException exception = expectThrows(ReleaseBundleDeletionException.class,
                () -> service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET));

        // verify
        assertEquals(expectedMessage, exception.getMessage());
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);
        verify(bridgeToModule).isRetentionFeatureEnabled();

        verifyReportedMetric(0, null, null);
    }

    @Test
    public void testTryDeleteReleaseBundleMarkedAsKeepWhenRetentionFeatureIsDisabled() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        prepareMocksForReleaseBundleDeletion(true, true);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(false);

        // test
        service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET);

        // verify
        verifyMocksForDeleteReleaseBundleMethod();

        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.EXTERNAL_USER);
    }

    @Test
    public void testDeleteBundleSuccessfully() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.RETENTION_SERVICE);
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);

        // test
        service.deleteBundle(bundleId);

        // verify
        verifyMocksForDeleteBundleMethod();

        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.RETENTION_SERVICE);
    }

    @Test
    public void testDeleteBundleSuccessfullyWhenRetentionFeatureIsDisabled() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(false);

        // test
        service.deleteBundle(bundleId);

        // verify
        verifyMocksForDeleteBundleMethod();

        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.EXTERNAL_USER);
    }

    @Test
    public void testDeleteBundleMarkedAsKeep() throws SQLException {
        // setup
        prepareMocksForReleaseBundleDeletion(true, true);
        String expectedMessage = "Release Bundle " + bundleName + "/" + bundleVersion +
                " cannot be deleted because it was marked as 'Keep'. " +
                "To delete the Release Bundle, clear the 'Keep' selection.";
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);

        // test
        ReleaseBundleDeletionException exception = expectThrows(ReleaseBundleDeletionException.class,
                () -> service.deleteBundle(bundleId));

        // verify
        assertEquals(expectedMessage, exception.getMessage());
        verify(bundlesDao).getArtifactsBundle(bundleId);
        verify(bridgeToModule).isRetentionFeatureEnabled();

        verifyReportedMetric(0, null, null);
    }

    @Test
    public void testDeleteBundleMarkedAsKeepWhenRetentionFeatureIsDisabled() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        prepareMocksForReleaseBundleDeletion(true, true);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(false);

        // test
        service.deleteBundle(bundleId);

        // verify
        verifyMocksForDeleteBundleMethod();

        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.EXTERNAL_USER);
    }

    @Test
    public void testGetAllCompletedBundles() throws SQLException {
        List<DBBundleResult> dbResults = List.of(
                new DBBundleResult("bundle1", "repo1"),
                new DBBundleResult("bundle2", "repo1")
        );

        List<BundleNameAndRepo> expectedResult = List.of(
                new BundleNameAndRepo("bundle1", "repo1"),
                new BundleNameAndRepo("bundle2", "repo1")
        );

        when(bundlesDao.getAllCompletedBundles(null)).thenReturn(dbResults);

        List<BundleNameAndRepo> allCompletedBundles = service.getAllCompletedBundles(null);
        verify(bundlesDao).getAllCompletedBundles(null);

        assertEquals(expectedResult, allCompletedBundles);
    }

    @Test
    public void testGetAllCompletedBundles_errorHandling() throws SQLException {
        when(bundlesDao.getAllCompletedBundles(null)).thenThrow(new SQLException("my Postgres is dead"));

        assertThrows(org.artifactory.storage.StorageException.class, () -> service.getAllCompletedBundles(null));
        verify(bundlesDao).getAllCompletedBundles(null);
    }

    @Test
    public void testDeleteReleaseBundleFailedWhileDeleting() throws SQLException {
        prepareMocksForReleaseBundleDeletion(true, false);
        doThrow(new SQLException()).when(internalReleaseBundleService)
                .deleteReleaseBundleInternal(any(), any(), any());
        assertThrows(StorageException.class,
                () -> service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET));
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestStarted(any());
        verify(internalReleaseBundleService).deleteReleaseBundleInternal(any(), any(), any());
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestFailed(any(), eq(COULD_NOT_DELETE_RB_ERROR_MSG));
        verify(bundlesDao).getBundleNodes(bundleId);
        verify(bundlesDao).getArtifactsBundle(bundleId);
        verify(authorizationService).canDelete(any());
        verify(bridgeToModule).isRetentionFeatureEnabled();

        verifyReportedMetric(0, null, null);
    }

    @Test
    public void testDeleteAtTargetBundlesAreNotWrittenWhenFeatureIsDisabled() throws SQLException {
        // setup
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isActionsFeatureEnabled()).thenReturn(false);

        // test
        service.deleteReleaseBundle(bundleName, bundleVersion, BundleType.TARGET);

        // verify
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);
        verify(bundlesDao).getBundleNodes(bundleId);
        verify(authorizationService).canDelete(any());
        verify(bridgeToModule).isRetentionFeatureEnabled();
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestStarted(any());
        verify(internalReleaseBundleService).deleteReleaseBundleInternal(any(), any(), any());
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestCompleted(any());
        verify(bundlesDao).getArtifactsBundle(bundleId);
        verify(bridgeToModule).isActionsFeatureEnabled();
        verify(securityService, times(0)).currentUsername();
        verify(bridgeToModule, times(0)).writeBundleLocallyDeletedFromTarget(any());
        verifyReportedMetric(1, BundleType.TARGET, ReleaseBundleActors.EXTERNAL_USER);
    }

    @Test
    public void shouldNotDeleteBundleWithoutDeletePermission() throws SQLException {
        // Setup
        prepareMocksForReleaseBundleDeletion(false, false);
        String repoPath = "path1";
        List<BundleNode> bundleNodes = List.of(new BundleNode(1L, 1L, repoPath, bundleId, "originalFileDetails"));
        ArtifactsBundleModel bundleModel =
                new ArtifactsBundleModel(bundleId, bundleName, bundleVersion, BundleTransactionStatus.COMPLETE,
                        BundleType.TARGET, "storingRepo");
        when(bundlesDao.getBundleNodes(bundleId)).thenReturn(bundleNodes);

        String expectedErrorMessage = String.format(
                "HTTP 403 Delete release bundle %s:%s is forbidden for user: '%s' " +
                        "because user does not have delete permission on the following paths: %s",
                bundleModel.getName(), bundleModel.getVersion(), securityService.currentUsername(), List.of(repoPath));

        // Test
        ForbiddenWebAppException exception =
                expectThrows(ForbiddenWebAppException.class, () -> service.deleteBundle(bundleModel));
        assertEquals(expectedErrorMessage, exception.getMessage());

        // Verify
        verify(bundlesDao).getBundleNodes(bundleId);
        verify(authorizationService).canDelete(any());
    }

    @Test(dataProvider = "filterNodesIdsTestDataProvider")
    public void filterNodesIds_shouldCategorizeNodesCorrectly(Map<Long, List<Long>> nodeIdsToBundleIds,
            Set<Long> nodeIdsWithArtifacts, Set<Long> nodeIdsWithArtifactsSharedBundles) throws SQLException {
        // Setup
        List<BundleNode> bundleNodes = List.of(new BundleNode(1L, 1L, "repo/path1", 1L, "details1"),
                new BundleNode(2L, 2L, "repo/path2", 1L, "details2"));

        when(bundlesDao.getAllBundlesRelatedToNode(1L)).thenReturn(nodeIdsToBundleIds.get(1L));
        when(bundlesDao.getAllBundlesRelatedToNode(2L)).thenReturn(nodeIdsToBundleIds.get(2L));

        Set<Long> nodeIdsWithArtifactsBelongingOnlyToThisBundle = Sets.newHashSet();
        Set<Long> nodeIdsWithArtifactsBelongingToVariousBundles = Sets.newHashSet();

        // Test
        service.filterNodesIds(bundleNodes, nodeIdsWithArtifactsBelongingOnlyToThisBundle,
                nodeIdsWithArtifactsBelongingToVariousBundles);

        // Verify
        verify(bundlesDao, times(2)).getAllBundlesRelatedToNode(anyLong());
        assertEquals(nodeIdsWithArtifacts, nodeIdsWithArtifactsBelongingOnlyToThisBundle);
        assertEquals(nodeIdsWithArtifactsSharedBundles, nodeIdsWithArtifactsBelongingToVariousBundles);
    }

    @Test
    public void testFilterNodesIds_errorHandling() throws SQLException {
        // Setup
        List<BundleNode> bundleNodes = List.of(new BundleNode(1L, 1L, "repo/path1", 1L, "details1"),
                new BundleNode(2L, 2L, "repo/path2", 1L, "details2"));

        when(bundlesDao.getAllBundlesRelatedToNode(1L)).thenThrow(new SQLException("sql something"));

        // Test
        StorageException e = expectThrows(StorageException.class,
                () -> service.filterNodesIds(bundleNodes, new HashSet<>(), new HashSet<>()));
        assertEquals(e.getMessage(), "Could not delete Release Bundle");

        // Verify
        verify(bundlesDao).getAllBundlesRelatedToNode(1L);
    }


    @Test
    public void shouldNotRetryDeleteSharedArtifacts_whenSessionIsNull() throws SQLException {
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);
        when(releaseBundleRetryDeleteSharedArtifacts.getBoolean()).thenReturn(true);
        StorageSessionHolder.setSession(null);

        // test
        service.deleteBundle(bundleId);

        // verify
        verifyMocksForDeleteBundleMethod();
    }

    @Test
    public void shouldNotRetryDeleteSharedArtifacts_whenSessionIsNotNull() throws SQLException {
        prepareMocksForReleaseBundleDeletion(true, false);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);
        when(releaseBundleRetryDeleteSharedArtifacts.getBoolean()).thenReturn(true);

        DeleteReleaseBundleArtifactsTransactionResource resource =
                mock(DeleteReleaseBundleArtifactsTransactionResource.class);
        StorageSession session = mock(StorageSession.class);
        when(session.getOrCreateResource(any())).thenReturn(resource);
        StorageSessionHolder.setSession(session);

        // test
        service.deleteBundle(bundleId);

        // verify
        verifyMocksForDeleteBundleMethod();
        verify(resource, times(1)).init(any(), any(), any(), any());
    }

    @Test
    public void testReportBundleLocallyDeletedFromTarget() {
        TargetBundleAction action = TargetBundleAction.DELETE_AT_TARGET;
        String bundleName = "rbv2atw";
        String bundleVersion = "3.0.0";
        String sourceServiceId = "serviceId";
        String signature = "signature";
        String createdBy = "dist-service";
        String repo = DEFAULT_RBV2_DISTRIBUTION_REPO;
        CurrentActivity.ACTOR.put(ReleaseBundleActors.EXTERNAL_USER);
        when(securityService.currentUsername()).thenReturn(createdBy);
        when(bridgeToModule.isActionsFeatureEnabled()).thenReturn(true);

        TargetBundleActionsBaseModel actionsBaseModel = new TargetBundleActionsBaseModel(action, bundleName,
                bundleVersion, sourceServiceId, DigestUtils.sha256Hex(signature), createdBy, repo);

        service.reportBundleLocallyDeletedFromTarget(repo, bundleName, bundleVersion, sourceServiceId, signature);

        verify(bridgeToModule, times(1)).isActionsFeatureEnabled();
        verify(bridgeToModule, times(1)).writeBundleLocallyDeletedFromTarget(actionsBaseModel);
    }

    private void prepareMocksForReleaseBundleDeletion(boolean canDelete, boolean keep)
            throws SQLException {
        DBArtifactsBundle artifactsBundle = getSimpleDbArtifactsBundle(keep);
        when(bundlesDao.getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET))
                .thenReturn(artifactsBundle);
        when(bundlesDao.getArtifactsBundle(bundleId)).thenReturn(artifactsBundle);
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDelete(any())).thenReturn(canDelete);
        when(bridgeToModule.isRetentionFeatureEnabled()).thenReturn(true);
        when(securityService.currentUsername()).thenReturn(currentUser);
        when(bridgeToModule.isActionsFeatureEnabled()).thenReturn(true);
    }

    @NonNull
    private DBArtifactsBundle getSimpleDbArtifactsBundle(boolean keep) {
        DBArtifactsBundle artifactsBundle = new DBArtifactsBundle();
        artifactsBundle.setName(bundleName);
        artifactsBundle.setVersion(bundleVersion);
        String repoKey = "repo_key";
        artifactsBundle.setStoringRepo(repoKey);
        artifactsBundle.setDateCreated(new DateTime());
        artifactsBundle.setStatus(BundleTransactionStatus.INPROGRESS);
        artifactsBundle.setId(bundleId);
        artifactsBundle.setType(BundleType.TARGET);
        artifactsBundle.setKeep(keep);
        artifactsBundle.setSignature(bundleSignature);
        artifactsBundle.setSourceServiceId(bundleSourceServiceId);
        return artifactsBundle;
    }

    @DataProvider(name = "filterNodesIdsTestDataProvider")
    private Object[][] getFilterNodesIdsTestDataProvider() {
        return new Object[][] {{Map.of(1L, List.of(1L), 2L, List.of(1L)), Set.of(1L, 2L), Set.of()}, {Map.of(1L,
                List.of(1L), 2L, List.of(1L, 2L)), Set.of(1L), Set.of(2L)}};
    }

    @DataProvider(name = "OrphanArtifactsTestDataProvider")
    private Object[][] getOrphanArtifactsTestDataProvider() {
        return new Object[][] {{true, true, true}, {true, false, true}, {false, true, true}, {false, false, false}};
    }

    private ReleaseBundleModel getTestModel() {
        ReleaseArtifact artifact1 = new ReleaseArtifact();
        artifact1.setRepoPath("dummyRepo/file1.txt");
        artifact1.setChecksum("111aaa");

        ReleaseArtifact artifact2 = new ReleaseArtifact();
        artifact2.setRepoPath("dummyRepo/file2.txt");
        artifact2.setChecksum("222bbb");

        ReleaseBundleModel model = new ReleaseBundleModel();
        model.setName("platform");
        model.setVersion("7.5.1");
        model.setArtifacts(Lists.newArrayList(
                artifact1,
                artifact2
        ));

        return model;
    }

    private Map<String, String> getTestMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("dummyRepo/file1.txt", "dummyRepo/file1.txt");
        mapping.put("dummyRepo/file2.txt", "dummyRepo/file2.txt");

        return mapping;
    }

    private void verifyMocksForDeleteReleaseBundleMethod() throws SQLException {
        verify(bundlesDao).getArtifactsBundle(bundleName, bundleVersion, BundleType.TARGET);
        verify(bundlesDao).getBundleNodes(bundleId);
        verify(authorizationService).canDelete(any());
        verify(securityService).currentUsername();
        verify(bridgeToModule).isRetentionFeatureEnabled();
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestStarted(any());
        verify(internalReleaseBundleService).deleteReleaseBundleInternal(any(), any(), any());
        verify(releaseBundleInterceptors).onDeleteReleaseBundleRequestCompleted(any());
        verify(bundlesDao).getArtifactsBundle(bundleId);
        verify(bridgeToModule).isActionsFeatureEnabled();
        verify(bridgeToModule).writeBundleLocallyDeletedFromTarget(actionsBaseModelCaptor.capture());
        validateCapturedTargetBundleActionsBaseModel();
    }

    private void verifyMocksForDeleteBundleMethod() throws SQLException {
        verify(bundlesDao, times(2)).getArtifactsBundle(bundleId);
        verify(bridgeToModule).isRetentionFeatureEnabled();
        verify(internalReleaseBundleService).deleteReleaseBundleInternal(any(), any(), any());
        verify(bundlesDao).getBundleNodes(bundleId);
        verify(securityService).currentUsername();
        verify(bridgeToModule).isActionsFeatureEnabled();
        verify(bridgeToModule).writeBundleLocallyDeletedFromTarget(actionsBaseModelCaptor.capture());
        validateCapturedTargetBundleActionsBaseModel();
    }

    private void validateCapturedTargetBundleActionsBaseModel() {
        TargetBundleActionsBaseModel capturedModel = actionsBaseModelCaptor.getValue();
        assertEquals(capturedModel.getBundleName(), bundleName);
        assertEquals(capturedModel.getBundleVersion(), bundleVersion);
        assertEquals(capturedModel.getBundleSignatureChecksum(), DigestUtils.sha256Hex(bundleSignature));
        assertEquals(capturedModel.getSourceServiceId(), bundleSourceServiceId);
        assertEquals(capturedModel.getAction(), TargetBundleAction.DELETE_AT_TARGET);
        assertEquals(capturedModel.getCreatedBy(), currentUser);
    }

    private void verifyReportedMetric(double expectedMetricValue,
            @Nullable BundleType expectedBundleType,
            @Nullable String expectedActor) {
        if (expectedMetricValue == 0) {
            assertTrue(metric.getMeters().isEmpty());
        } else {
            Objects.requireNonNull(expectedBundleType);
            Objects.requireNonNull(expectedActor);
            Meter metric = this.metric.getMeters().get(0);
            assertEquals(expectedMetricValue, metric.measure().iterator().next().getValue());
            assertEquals("release.bundle.deletion", metric.getId().getName());
            assertEquals(2, metric.getId().getTags().size());
            assertEquals(expectedActor, metric.getId().getTag("actor"));
            assertEquals(expectedBundleType.name(), metric.getId().getTag("bundle_type"));
        }
    }

    //TODO: extract
    private static class SyncedExecutorService extends ThreadPoolExecutor {

        public SyncedExecutorService() {
            super(0,
                    2,
                    60L,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>());
        }

        @Override
        public Future<?> submit(Runnable task) {
            task.run();
            return new BasicFuture<>(null);
        }
    }
}
