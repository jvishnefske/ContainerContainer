package org.artifactory.repo.interceptor;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import ch.qos.logback.classic.Level;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildInfoInterceptorTest {

    private static final String BUILD_INFO_REPO = "test-build-info";
    private static final String BUILD_NAME = "UNO_CI";
    private static final String BUILD_NUMBER = "42";
    // Two build start times that differ, reproducing the legacy Jenkins-plugin build.timestamp mismatch:
    // the file path is keyed by the DB start time, while the manifest carries a different start time.
    private static final long DB_STARTED_MILLIS = 1742380344069L;
    private static final long MANIFEST_STARTED_MILLIS = 1742380344070L;

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private AuthorizationService authService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private MutableStatusHolder statusHolder;

    private BuildInfoInterceptor buildInfoInterceptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        buildInfoInterceptor =
                new BuildInfoInterceptor(repositoryService, buildFacade, authService, securityService, addonsManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Reset any per-test log level override back to the default.
        interceptorLogger().setLevel(null);
        mocks.close();
    }

    private static ch.qos.logback.classic.Logger interceptorLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BuildInfoInterceptor.class);
    }

    @Test
    void testAssertDeleteRepoAllowed_DefaultBuildRepo() {
        String repoKey = "default-build-repo";
        when(buildFacade.getBuildInfoRepoForDefaultProject()).thenReturn(repoKey);

        buildInfoInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);

        verify(statusHolder).error("Deletion of Build Info repository " + repoKey + " is not allowed.", 403,
                getLogger());
    }

    private static Logger getLogger() {
        Logger log = LoggerFactory.getLogger(BuildInfoInterceptor.class);
        return log;
    }

    @Test
    void testAssertDeleteRepoAllowed_BuildInfoRepo_NotAdmin() {
        String repoKey = "build-info-repo";
        when(buildFacade.getBuildInfoRepoForDefaultProject()).thenReturn("default-build-repo");
        when(buildFacade.isBuildInfoRepo(repoKey)).thenReturn(true);
        when(securityService.isAdmin()).thenReturn(false);

        buildInfoInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);

        verify(statusHolder).error(
                "Deletion of Build-Info repository '" + repoKey + "' is restricted only for super admin.", 403,
                getLogger());
    }

    @Test
    void testAssertDeleteRepoAllowed_BuildInfoRepo_Admin() {
        String repoKey = "build-info-repo";
        when(buildFacade.getBuildInfoRepoForDefaultProject()).thenReturn("default-build-repo");
        when(buildFacade.isBuildInfoRepo(repoKey)).thenReturn(true);
        when(securityService.isAdmin()).thenReturn(true);

        buildInfoInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);

        verify(statusHolder, never()).error(anyString(), anyInt(), any());
    }

    @Test
    void testAssertDeleteRepoAllowed_NotBuildInfoRepo() {
        String repoKey = "some-other-repo";
        when(buildFacade.getBuildInfoRepoForDefaultProject()).thenReturn("default-build-repo");
        when(buildFacade.isBuildInfoRepo(repoKey)).thenReturn(false);

        buildInfoInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);

        verify(statusHolder, never()).error(anyString(), anyInt(), any());
    }

    /**
     * RTDEV-91276: when the build-info file path doesn't match the manifest layout, the DB record must still be purged
     * by the coordinates encoded in the path (not silently left behind), otherwise build retention keeps re-finding
     * and re-deleting the same build run on every execution.
     */
    @Test
    void testAfterDelete_wrongPath_purgesDbRecordByPathCoordinates() {
        // Enable DEBUG so the mismatch log line is exercised as well.
        interceptorLogger().setLevel(Level.DEBUG);
        String wrongPath = relativePath(DB_STARTED_MILLIS);
        VfsItem<ItemInfo> fsItem = mockBuildInfoFile(wrongPath);
        DeleteContext ctx = mockDeleteContext();
        // Manifest start time differs from the path time -> isBuildUnderWrongPath is true.
        mockBuildModelFromFile(wrongPath, MANIFEST_STARTED_MILLIS);
        BuildRun buildRun = mockBuildRun(DB_STARTED_MILLIS);
        when(buildFacade.searchBuildsByNameAndNumberInternal(BUILD_NAME, BUILD_NUMBER, BUILD_INFO_REPO))
                .thenReturn(Set.of(buildRun));

        buildInfoInterceptor.afterDelete(fsItem, statusHolder, ctx);

        // The matching-by-path DB record is deleted; the layout-based delete is NOT used for the mismatched build.
        verify(buildFacade).deleteBuild(buildRun, BUILD_INFO_REPO, statusHolder);
        verify(buildFacade, never()).deleteBuildInternal(any(), anyString(), any());
    }

    @Test
    void testAfterDelete_missingBuildInfoBinary_purgesDbRecordByPathCoordinates() {
        String path = relativePath(DB_STARTED_MILLIS);
        VfsItem<ItemInfo> fsItem = mockBuildInfoFile(path);
        DeleteContext ctx = mockDeleteContext();
        // The build-info binary cannot be parsed -> fall back to deleting by path coordinates.
        when(buildFacade.getBuildModelFromFile(new BuildRepoPath(BUILD_INFO_REPO, path)))
                .thenThrow(new NotFoundException("binary missing"));
        BuildRun buildRun = mockBuildRun(DB_STARTED_MILLIS);
        when(buildFacade.searchBuildsByNameAndNumberInternal(BUILD_NAME, BUILD_NUMBER, BUILD_INFO_REPO))
                .thenReturn(Set.of(buildRun));

        buildInfoInterceptor.afterDelete(fsItem, statusHolder, ctx);

        verify(buildFacade).deleteBuild(buildRun, BUILD_INFO_REPO, statusHolder);
        verify(buildFacade, never()).deleteBuildInternal(any(), anyString(), any());
    }

    @Test
    void testAfterDelete_unparseablePath_skipsDbRecordDeletion() {
        // A .json build-info file whose name has no "-<millis>" suffix -> coordinates cannot be extracted.
        String unparseablePath = BUILD_NAME + "/nocoordinates.json";
        VfsItem<ItemInfo> fsItem = mockBuildInfoFile(unparseablePath);
        DeleteContext ctx = mockDeleteContext();
        when(buildFacade.getBuildModelFromFile(new BuildRepoPath(BUILD_INFO_REPO, unparseablePath)))
                .thenThrow(new NotFoundException("binary missing"));

        buildInfoInterceptor.afterDelete(fsItem, statusHolder, ctx);

        verify(buildFacade, never()).searchBuildsByNameAndNumberInternal(anyString(), anyString(), anyString());
        verify(buildFacade, never()).deleteBuild(any(BuildRun.class), anyString(), any());
        verify(buildFacade, never()).deleteBuildInternal(any(), anyString(), any());
    }

    @Test
    void testAfterDelete_wrongPath_doesNotDeleteBuildRunWithDifferentStartedDate() {
        String wrongPath = relativePath(DB_STARTED_MILLIS);
        VfsItem<ItemInfo> fsItem = mockBuildInfoFile(wrongPath);
        DeleteContext ctx = mockDeleteContext();
        mockBuildModelFromFile(wrongPath, MANIFEST_STARTED_MILLIS);
        // A build run sharing name+number but a different start time must be left untouched.
        BuildRun otherBuildRun = mockBuildRun(MANIFEST_STARTED_MILLIS);
        when(buildFacade.searchBuildsByNameAndNumberInternal(BUILD_NAME, BUILD_NUMBER, BUILD_INFO_REPO))
                .thenReturn(Set.of(otherBuildRun));

        buildInfoInterceptor.afterDelete(fsItem, statusHolder, ctx);

        verify(buildFacade, never()).deleteBuild(any(BuildRun.class), anyString(), any());
        verify(buildFacade, never()).deleteBuildInternal(any(), anyString(), any());
    }

    @Test
    void testAfterDelete_correctPath_deletesViaBuildInternal() {
        String correctPath = relativePath(MANIFEST_STARTED_MILLIS);
        VfsItem<ItemInfo> fsItem = mockBuildInfoFile(correctPath);
        DeleteContext ctx = mockDeleteContext();
        BuildWrapper buildWrapper = mockBuildModelFromFile(correctPath, MANIFEST_STARTED_MILLIS);

        buildInfoInterceptor.afterDelete(fsItem, statusHolder, ctx);

        // Path matches the layout -> delete straight through the interceptor, no path-coordinate fallback.
        verify(buildFacade).deleteBuildInternal(buildWrapper.getBuild(), BUILD_INFO_REPO, statusHolder);
        verify(buildFacade, never()).deleteBuild(any(BuildRun.class), anyString(), any());
    }

    @Test
    void testAfterDelete_avoidBuildDeleteInterceptor_doesNothing() {
        VfsItem<ItemInfo> fsItem = mockBuildInfoFile(relativePath(DB_STARTED_MILLIS));
        DeleteContext ctx = mock(DeleteContext.class);
        when(ctx.isAvoidBuildDeleteInterceptor()).thenReturn(true);

        buildInfoInterceptor.afterDelete(fsItem, statusHolder, ctx);

        verify(buildFacade, never()).deleteBuild(any(BuildRun.class), anyString(), any());
        verify(buildFacade, never()).deleteBuildInternal(any(), anyString(), any());
    }

    private static String relativePath(long startedMillis) {
        return generateBuildJsonRepoPath(BUILD_NAME, BUILD_NUMBER, formatBuildTime(startedMillis), BUILD_INFO_REPO)
                .getRelativePath();
    }

    @SuppressWarnings("unchecked")
    private VfsItem<ItemInfo> mockBuildInfoFile(String relativePath) {
        VfsItem<ItemInfo> fsItem = mock(VfsItem.class);
        when(fsItem.isFile()).thenReturn(true);
        when(fsItem.getRepoKey()).thenReturn(BUILD_INFO_REPO);
        when(fsItem.getPath()).thenReturn(relativePath);
        RepoPath repoPath = mock(RepoPath.class);
        lenient().when(repoPath.toPath()).thenReturn(BUILD_INFO_REPO + "/" + relativePath);
        lenient().when(fsItem.getRepoPath()).thenReturn(repoPath);
        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(BuildInfo);
        when(repositoryService.localOrFederatedRepoConfigByKey(BUILD_INFO_REPO)).thenReturn(localConfig);
        return fsItem;
    }

    private DeleteContext mockDeleteContext() {
        DeleteContext ctx = mock(DeleteContext.class);
        when(ctx.isAvoidBuildDeleteInterceptor()).thenReturn(false);
        return ctx;
    }

    private BuildWrapper mockBuildModelFromFile(String relativePath, long startedMillis) {
        Build build = mock(Build.class);
        when(build.getName()).thenReturn(BUILD_NAME);
        when(build.getNumber()).thenReturn(BUILD_NUMBER);
        when(build.getStarted()).thenReturn(formatBuildTime(startedMillis));
        BuildWrapper buildWrapper = new BuildWrapper(build, BUILD_INFO_REPO);
        when(buildFacade.getBuildModelFromFile(new BuildRepoPath(BUILD_INFO_REPO, relativePath)))
                .thenReturn(buildWrapper);
        return buildWrapper;
    }

    private static BuildRun mockBuildRun(long startedMillis) {
        BuildRun buildRun = mock(BuildRun.class);
        lenient().when(buildRun.getStartedDate()).thenReturn(new Date(startedMillis));
        return buildRun;
    }
}