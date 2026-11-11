package org.artifactory.repo;

import com.google.common.cache.CacheBuilder;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.PypiConfiguration;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.GuaveCacheConnectionManagersHolder;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.md.Properties;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.PathOverrideRequestContext;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.artifactory.service.SmartRepoService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.fs.VfsItemProviderFactory;
import org.artifactory.storage.fs.VfsMutableFolderProvider;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.artifactory.api.repo.UpstreamRepoKind.SMART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
class PypiHttpRepoTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "pypi-remote";
    private static final String PYPI_FILES_URL = "https://files.pythonhosted.org";
    private static final String PYPI_REGISTRY_URL = "https://pypi.org";

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private OnlineMonitorService onlineMonitorService;
    @Mock
    private IRequestRateLimiterService rateLimiterService;
    @Mock
    private InternalArtifactoryContext artifactoryContext;
    @Mock
    private LocalCacheRepo localCacheRepo;
    @Mock
    private SmartRepoService smartRepoService;

    private AddonsManager addonsManager;
    private ResearchService researchService;
    private CentralConfigService configService;
    private RemoteRepoConfig repoConfig;
    private PypiHttpRepo pypiHttpRepo;
    private HttpRepoDescriptor descriptor;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        addonsManager = mock(AddonsManager.class);
        FileService fileService = mock(FileService.class);
        VfsItemProviderFactory itemProviderFactory = mock(VfsItemProviderFactory.class);
        VfsMutableFolderProvider folderProvider = mock(VfsMutableFolderProvider.class);
        researchService = mock(ResearchService.class);
        IdleConnectionMonitorService idleConnSvc = new IdleConnectionMonitorServiceImpl(
                new GuaveCacheConnectionManagersHolder());
        DenyListCacheHolderService denyListSvc = mock(DenyListCacheHolderService.class);
        when(denyListSvc.getDenyListCache()).thenReturn(CacheBuilder.newBuilder().build());

        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(artifactoryContext.beanForType(FileService.class)).thenReturn(fileService);
        when(artifactoryContext.beanForType(VfsItemProviderFactory.class)).thenReturn(itemProviderFactory);
        when(artifactoryContext.beanForType(IdleConnectionMonitorService.class)).thenReturn(idleConnSvc);
        when(artifactoryContext.beanForType(DenyListCacheHolderService.class)).thenReturn(denyListSvc);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(mock(AolService.class));
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(mock(HttpConnectionPoolMonitorService.class));
        when(artifactoryContext.beanForType(ArtifactoryTracer.class))
                .thenReturn(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.getHostId()).thenReturn("1");
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haAddon);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(mock(CurationAddon.class));
        EdgeSmartRepoAddon edgeSmartRepoAddon = mock(EdgeSmartRepoAddon.class);
        when(edgeSmartRepoAddon.updateUpstreamRepoKind(REPO_KEY)).thenReturn(SMART);
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);
        when(itemProviderFactory.createFolderProvider(any(), any(), any(), any())).thenReturn(folderProvider);

        repoConfig = RemoteRepoConfig.create(REPO_KEY, RepoType.Pypi);
        repoConfig.getRepoTypeConfig().setUrl(PYPI_FILES_URL);
        configService = mock(CentralConfigService.class);
        when(configService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());

        descriptor = new HttpRepoDescriptor();
        descriptor.setUrl(PYPI_FILES_URL);
        PypiConfiguration pypiConfig = new PypiConfiguration();
        pypiConfig.setPyPIRegistryUrl(PYPI_REGISTRY_URL);
        descriptor.setPypi(pypiConfig);
        when(smartRepoService.isSmartRepo(any())).thenReturn(false);
        when(artifactoryContext.beanForType(SmartRepoService.class)).thenReturn(smartRepoService);

        RemoteRepoOfflineGuardService offlineGuardService = mock(RemoteRepoOfflineGuardService.class);
        when(offlineGuardService.isOfflinedByGuard(any())).thenReturn(false);
        when(artifactoryContext.beanForType(RemoteRepoOfflineGuardService.class)).thenReturn(offlineGuardService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        setStringSystemProperty(ConstantValues.pypiUrlsToDetectPackagesPrefixEnabled, "true");
        setStringSystemProperty(ConstantValues.pypiUrlsToDetectPackagesPrefix, PYPI_FILES_URL);

        pypiHttpRepo = spy(new PypiHttpRepo(descriptor, repoConfig, repositoryService, addonsManager,
                researchService, null, onlineMonitorService, configService, rateLimiterService));
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    // ---- isSupportStrippingPackages ----

    @Test
    void isSupportStrippingPackages_standardConfig_returnsTrue() {
        assertTrue(pypiHttpRepo.isSupportStrippingPackages());
    }

    @Test
    void isSupportStrippingPackages_nullUrl_returnsFalse() {
        PypiHttpRepo repo = createRepo(null, null);
        assertFalse(repo.isSupportStrippingPackages());
    }

    @Test
    void isSupportStrippingPackages_nonPypiUrl_returnsFalse() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        assertFalse(repo.isSupportStrippingPackages());
    }

    @Test
    void isSupportStrippingPackages_featureFlagDisabled_returnsFalse() {
        setStringSystemProperty(ConstantValues.pypiUrlsToDetectPackagesPrefixEnabled, "false");
        PypiHttpRepo repo = createRepo(PYPI_FILES_URL, null);
        assertFalse(repo.isSupportStrippingPackages());
    }

    // ---- normalizePypiCachePath ----

    @ParameterizedTest
    @CsvSource({
            "packages/44/ef/some-file.whl, 44/ef/some-file.whl",
            "packages/ab/cd/pkg.tar.gz, ab/cd/pkg.tar.gz",
            "packages/, ''"
    })
    void normalizePypiCachePath_stripsPackagesPrefix(String input, String expected) {
        assertEquals(expected, pypiHttpRepo.normalizePypiCachePath(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "44/ef/some-file.whl",
            "simple/packages-info/index.html",
            "simple/index.html"
    })
    void normalizePypiCachePath_leavesNonPrefixedPathUnchanged(String path) {
        assertEquals(path, pypiHttpRepo.normalizePypiCachePath(path));
    }

    @Test
    void normalizePypiCachePath_strippingNotSupported_leavesPathUnchanged() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        assertEquals("packages/44/ef/some-file.whl",
                repo.normalizePypiCachePath("packages/44/ef/some-file.whl"));
    }

    // ---- internalGetInfo ----

    @ParameterizedTest
    @CsvSource({
            "packages/44/ef/file.whl, 44/ef/file.whl",
            "44/ef/file.whl, 44/ef/file.whl"
    })
    void internalGetInfo_delegatesToSuperWithNormalizedPath(String inputPath, String expectedPath) {
        doReturn(true).when(pypiHttpRepo).isOffline();
        RepoPath repoPath = InternalRepoPathFactory.create(REPO_KEY, inputPath);

        RepoResource result = pypiHttpRepo.internalGetInfo(repoPath, new NullRequestContext(repoPath));

        assertEquals(expectedPath, result.getRepoPath().getPath());
    }

    @Test
    void internalGetInfo_strippingNotSupported_preservesPackagesPrefix() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        doReturn(true).when(repo).isOffline();
        RepoPath repoPath = InternalRepoPathFactory.create(REPO_KEY, "packages/44/ef/file.whl");

        RepoResource result = repo.internalGetInfo(repoPath, new NullRequestContext(repoPath));

        assertEquals("packages/44/ef/file.whl", result.getRepoPath().getPath());
    }

    // ---- getInfoFromCache ----

    @Test
    void getInfoFromCache_foundAtOriginalPath_returnsDirectly() {
        RepoResource foundResource = mock(RepoResource.class);
        when(foundResource.isFound()).thenReturn(true);
        doReturn(localCacheRepo).when(pypiHttpRepo).getLocalCacheRepo();
        InternalRequestContext context = contextFor("packages/44/ef/file.whl");
        when(localCacheRepo.getInfo(context)).thenReturn(foundResource);

        RepoResource result = pypiHttpRepo.getInfoFromCache(context);

        assertSame(foundResource, result);
        verify(localCacheRepo, times(1)).getInfo(any());
    }

    @Test
    void getInfoFromCache_missAtOriginalPath_retriesNormalized() {
        RepoResource foundResource = stubCacheHitsOnlyNormalizedPath("packages/44/ef/file.whl");

        RepoResource result = pypiHttpRepo.getInfoFromCache(contextFor("packages/44/ef/file.whl"));

        assertSame(foundResource, result);
        verify(localCacheRepo, times(2)).getInfo(any());
    }

    @Test
    void getInfoFromCache_noPackagesPrefix_skipsNormalization() {
        doReturn(localCacheRepo).when(pypiHttpRepo).getLocalCacheRepo();
        InternalRequestContext context = contextFor("44/ef/file.whl");
        when(localCacheRepo.getInfo(context)).thenReturn(unfound("44/ef/file.whl"));

        RepoResource result = pypiHttpRepo.getInfoFromCache(context);

        assertFalse(result.isFound());
        verify(localCacheRepo, times(1)).getInfo(any());
    }

    @Test
    void getInfoFromCache_missAtBothOriginalAndNormalized_returnsUnfound() {
        stubCacheAndKey();
        when(localCacheRepo.getInfo(any(InternalRequestContext.class)))
                .thenReturn(unfound("packages/44/ef/file.whl"));

        RepoResource result = pypiHttpRepo.getInfoFromCache(contextFor("packages/44/ef/file.whl"));

        assertFalse(result.isFound());
        verify(localCacheRepo, times(2)).getInfo(any());
    }

    @Test
    void getInfoFromCache_missAtOriginalPath_preservesOriginalContextState() {
        stubCacheAndKey();

        InternalRequestContext originalCtx = mock(InternalRequestContext.class);
        when(originalCtx.getResourcePath()).thenReturn("packages/44/ef/file.whl");
        when(originalCtx.isForceExpiryCheck()).thenReturn(true);
        when(originalCtx.isFromAnotherArtifactory()).thenReturn(true);
        Properties props = mock(Properties.class);
        when(originalCtx.getProperties()).thenReturn(props);
        Request request = mock(Request.class);
        when(originalCtx.getRequest()).thenReturn(request);

        ArgumentCaptor<InternalRequestContext> captor =
                ArgumentCaptor.forClass(InternalRequestContext.class);
        RepoResource found = mock(RepoResource.class);
        when(found.isFound()).thenReturn(true);
        when(localCacheRepo.getInfo(captor.capture()))
                .thenReturn(unfound("packages/44/ef/file.whl"), found);

        pypiHttpRepo.getInfoFromCache(originalCtx);

        InternalRequestContext normalizedCtx = captor.getAllValues().get(1);
        assertInstanceOf(PathOverrideRequestContext.class, normalizedCtx);
        assertEquals("44/ef/file.whl", normalizedCtx.getResourcePath());
        assertTrue(normalizedCtx.isForceExpiryCheck());
        assertTrue(normalizedCtx.isFromAnotherArtifactory());
        assertSame(props, normalizedCtx.getProperties());
        assertSame(request, normalizedCtx.getRequest());
    }

    @Test
    void getInfoFromCache_strippingNotSupported_skipsNormalization() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        doReturn(localCacheRepo).when(repo).getLocalCacheRepo();
        InternalRequestContext context = contextFor("packages/44/ef/file.whl");
        when(localCacheRepo.getInfo(context)).thenReturn(unfound("packages/44/ef/file.whl"));

        RepoResource result = repo.getInfoFromCache(context);

        assertFalse(result.isFound());
        verify(localCacheRepo, times(1)).getInfo(any());
    }

    // ---- getCachedResource ----

    @Test
    void getCachedResource_foundAtOriginalPath_returnsDirectly() {
        RepoResource foundResource = mock(RepoResource.class);
        when(foundResource.isFound()).thenReturn(true);
        stubCacheAndKey();
        when(localCacheRepo.getInfo(any(NullRequestContext.class))).thenReturn(foundResource);

        RepoResource result = pypiHttpRepo.getCachedResource("packages/44/ef/f.whl");

        assertSame(foundResource, result);
        verify(localCacheRepo, times(1)).getInfo(any(NullRequestContext.class));
    }

    @Test
    void getCachedResource_missAtOriginalPath_retriesNormalized() {
        RepoResource foundResource = stubCacheHitsOnlyNormalizedPath("packages/44/ef/f.whl");

        RepoResource result = pypiHttpRepo.getCachedResource("packages/44/ef/f.whl");

        assertSame(foundResource, result);
        verify(localCacheRepo, times(2)).getInfo(any(NullRequestContext.class));
    }

    @Test
    void getCachedResource_noPackagesPrefix_skipsNormalization() {
        stubCacheAndKey();
        when(localCacheRepo.getInfo(any(NullRequestContext.class))).thenReturn(unfound("44/ef/file.whl"));

        RepoResource result = pypiHttpRepo.getCachedResource("44/ef/file.whl");

        assertFalse(result.isFound());
        verify(localCacheRepo, times(1)).getInfo(any(NullRequestContext.class));
    }

    @Test
    void getCachedResource_missAtBothOriginalAndNormalized_returnsUnfound() {
        stubCacheAndKey();
        when(localCacheRepo.getInfo(any(NullRequestContext.class))).thenReturn(unfound("packages/44/ef/f.whl"));

        RepoResource result = pypiHttpRepo.getCachedResource("packages/44/ef/f.whl");

        assertFalse(result.isFound());
        verify(localCacheRepo, times(2)).getInfo(any(NullRequestContext.class));
    }

    @Test
    void getCachedResource_strippingNotSupported_skipsNormalization() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        doReturn(localCacheRepo).when(repo).getLocalCacheRepo();
        doReturn(REPO_KEY).when(repo).getKey();
        when(localCacheRepo.getInfo(any(NullRequestContext.class)))
                .thenReturn(unfound("packages/44/ef/f.whl"));

        RepoResource result = repo.getCachedResource("packages/44/ef/f.whl");

        assertFalse(result.isFound());
        verify(localCacheRepo, times(1)).getInfo(any(NullRequestContext.class));
    }

    // ---- appendAndGetUrl ----

    @ParameterizedTest
    @CsvSource({
            "44/ef/file.whl, packages/44/ef/file.whl",
            "ab/cd/pkg.tar.gz, packages/ab/cd/pkg.tar.gz"
    })
    void appendAndGetUrl_addsPackagesPrefix_forNormalizedPaths(String input, String expected) {
        doReturn(PYPI_FILES_URL + "/").when(pypiHttpRepo).getUrl();
        String result = pypiHttpRepo.appendAndGetUrl(input, (RequestContext) null);
        assertTrue(result.contains(expected),
                "Expected URL to contain '" + expected + "' but was: " + result);
    }

    @Test
    void appendAndGetUrl_doesNotDoublePrefix_whenAlreadyPresent() {
        doReturn(PYPI_FILES_URL + "/").when(pypiHttpRepo).getUrl();
        String result = pypiHttpRepo.appendAndGetUrl("packages/44/ef/file.whl", (RequestContext) null);
        assertFalse(result.contains("packages/packages/"), "Should not double-prefix: " + result);
        assertTrue(result.contains("packages/44/ef/file.whl"), "Should contain packages/ once: " + result);
    }

    /**
     * The url a package handler resolved already names the file's real location, so it is used as-is and
     * gets no {@code packages/} prefix. Supplied through the alternative-download-url channel, which is how
     * this url reaches the repository in production and, since RTDEV-97315, what permits it to name a
     * destination of its own.
     */
    @Test
    void appendAndGetUrl_doesNotAddPrefix_forAbsoluteUrl() {
        String absoluteUrl = "https://files.pythonhosted.org/44/ef/f.whl";
        doReturn(PYPI_FILES_URL + "/").when(pypiHttpRepo).getUrl();

        String result = pypiHttpRepo.appendAndGetUrl(absoluteUrl, alternativeDownloadUrlContext(absoluteUrl));

        assertEquals(absoluteUrl, result);
    }

    /**
     * The same url arriving as an artifact path instead is appended to the configured origin, so a client
     * cannot use it to redirect the fetch (RTDEV-97315). Asserted on the PyPI subclass as well as the base
     * class because this override consults {@code isAbsolute} on its own to decide the {@code packages/}
     * prefix, and that decision must not quietly reintroduce verbatim use.
     */
    @Test
    void appendAndGetUrl_appendsAbsoluteUrl_whenNotFromAlternativeDownloadChannel() {
        String absoluteUrl = "https://files.pythonhosted.org/44/ef/f.whl";
        doReturn(PYPI_FILES_URL + "/").when(pypiHttpRepo).getUrl();

        String result = pypiHttpRepo.appendAndGetUrl(absoluteUrl, (RequestContext) null);

        assertEquals(PYPI_FILES_URL + "/" + absoluteUrl, result);
    }

    private RequestContext alternativeDownloadUrlContext(String alternativeDownloadUrl) {
        InternalArtifactoryRequest request = mock(InternalArtifactoryRequest.class);
        when(request.getParameter(ArtifactoryHeaders.PARAM_ALTERNATIVE_REMOTE_DOWNLOAD_URL))
                .thenReturn(alternativeDownloadUrl);
        RequestContext context = mock(RequestContext.class);
        when(context.getRequest()).thenReturn(request);
        return context;
    }

    @Test
    void appendAndGetUrl_strippingNotSupported_skipsPackagesPrefix() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        doReturn("https://example.com/repo/").when(repo).getUrl();

        String result = repo.appendAndGetUrl("44/ef/file.whl", (RequestContext) null);
        assertFalse(result.contains("packages/"),
                "Non-PyPI repo should not get packages/ prefix: " + result);
    }

    // ---- getResourceOfflineRepoPath ----

    @ParameterizedTest
    @CsvSource({
            "packages/44/ef/file.whl, 44/ef/file.whl",
            "44/ef/file.whl, 44/ef/file.whl",
            "packages/ab/cd/pkg.tar.gz, ab/cd/pkg.tar.gz"
    })
    void getResourceOfflineRepoPath_normalizesPath(String input, String expected) {
        String result = pypiHttpRepo.getResourceOfflineRepoPath(input, expected);
        assertEquals(expected, result);
    }

    @Test
    void getResourceOfflineRepoPath_strippingNotSupported_preservesPackagesPrefix() {
        PypiHttpRepo repo = createRepo("https://example.com/repo", null);
        String result = repo.getResourceOfflineRepoPath(
                "packages/44/ef/file.whl", "packages/44/ef/file.whl");
        assertEquals("packages/44/ef/file.whl", result);
    }

    // ---- full flow: normalization + URL restoration ----

    @ParameterizedTest
    @CsvSource({
            "packages/44/ef/file.whl, 44/ef/file.whl",
            "44/ef/file.whl, 44/ef/file.whl"
    })
    void normalizeAndRestore_roundTrip(String incomingPath, String expectedCachePath) {
        doReturn(PYPI_FILES_URL + "/").when(pypiHttpRepo).getUrl();

        String cachePath = pypiHttpRepo.normalizePypiCachePath(incomingPath);
        assertEquals(expectedCachePath, cachePath);

        String url = pypiHttpRepo.appendAndGetUrl(cachePath, (RequestContext) null);
        assertTrue(url.endsWith("packages/" + expectedCachePath),
                "URL should have packages/ prefix: " + url);
        assertTrue(url.startsWith(PYPI_FILES_URL), "URL should start with repo base: " + url);
    }

    // ---- helpers ----

    private RepoResource unfound(String path) {
        return new UnfoundRepoResource(InternalRepoPathFactory.create(REPO_KEY, path), "not found");
    }

    private InternalRequestContext contextFor(String path) {
        return new NullRequestContext(InternalRepoPathFactory.create(REPO_KEY, path));
    }

    private void stubCacheAndKey() {
        doReturn(localCacheRepo).when(pypiHttpRepo).getLocalCacheRepo();
        doReturn(REPO_KEY).when(pypiHttpRepo).getKey();
    }

    private PypiHttpRepo createRepo(String url, PypiConfiguration pypiConfig) {
        HttpRepoDescriptor desc = new HttpRepoDescriptor();
        desc.setUrl(url);
        desc.setPypi(pypiConfig);
        RemoteRepoConfig config = RemoteRepoConfig.create(REPO_KEY, RepoType.Pypi);
        config.getRepoTypeConfig().setUrl(url != null ? url : PYPI_FILES_URL);
        return spy(new PypiHttpRepo(desc, config, repositoryService, addonsManager,
                researchService, null, onlineMonitorService, configService,
                rateLimiterService));
    }

    private RepoResource stubCacheHitsOnlyNormalizedPath(String originalPath) {
        RepoResource unfoundRes = unfound(originalPath);
        RepoResource foundResource = mock(RepoResource.class);
        when(foundResource.isFound()).thenReturn(true);
        stubCacheAndKey();
        when(localCacheRepo.getInfo(any(InternalRequestContext.class))).thenAnswer(invocation -> {
            InternalRequestContext ctx = invocation.getArgument(0);
            return ctx.getResourcePath().startsWith("packages/") ? unfoundRes : foundResource;
        });
        return foundResource;
    }

}
