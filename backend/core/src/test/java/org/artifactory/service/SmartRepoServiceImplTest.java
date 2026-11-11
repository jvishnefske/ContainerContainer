package org.artifactory.service;

import org.apache.http.client.methods.HttpHead;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.PropertiesAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.descriptor.delegation.ContentSynchronisationDescriptor;
import org.artifactory.descriptor.delegation.PropertiesContentDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.resource.FileResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.service.SmartRepoServiceImpl.REMOTE_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartRepoServiceImplTest {

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private ResearchService researchService;

    private SmartRepoServiceImpl service;

    private AutoCloseable mocks;

    @Mock
    private HttpRepoDescriptor descriptor;

    @Mock
    private HttpRepo repo;

    @Mock
    private RepoResource repoResource;

    @Mock
    private ContentSynchronisationDescriptor contentSynchronisation;

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private AddonsManager addonManager;

    @Mock
    private PropertiesAddon propertiesAddon;

    @Mock
    private FileInfo fileInfo;

    @Mock
    private RemoteRepoConfig repoConfig;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = Mockito.spy(new SmartRepoServiceImpl());
        ReflectionTestUtils.setField(service, "repositoryService",
                repositoryService);
        ReflectionTestUtils.setField(service, "researchService",
                researchService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        mocks.close();
    }

    private void setupCommonMocks() {
        when(repositoryService.isRemoteRepoOffline(any())).thenReturn(false);
        when(descriptor.getContentSynchronisation()).thenReturn(contentSynchronisation);
    }

    private PropertiesContentDescriptor setupProperties() {
        PropertiesContentDescriptor propertiesContent = mock(PropertiesContentDescriptor.class);
        when(descriptor.getContentSynchronisation().getProperties()).thenReturn(propertiesContent);
        return propertiesContent;
    }

    private void setupRepoPath(String path) {
        RepoPath repoPath = mock(RepoPath.class);
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getPath()).thenReturn(path);
        when(repoPath.getRepoKey()).thenReturn("repoKey");
        when(repositoryService.exists(repoPath)).thenReturn(true);
    }

    private LocalCacheRepo setupLocalCacheRepo(RepoType type) {
        LocalCacheRepo localRepo = mock(LocalCacheRepo.class);
        LocalCacheRepoDescriptor localCacheRepoDescriptor = mock(LocalCacheRepoDescriptor.class);
        when(repo.getLocalCacheRepo()).thenReturn(localRepo);
        when(repo.getLocalCacheRepo().getDescriptor()).thenReturn(localCacheRepoDescriptor);
        when(repo.getLocalCacheRepo().getDescriptor().getType()).thenReturn(type);
        return localRepo;
    }

    private CacheExpiry setupCacheExpiry(boolean isExpirable) {
        CacheExpiry cacheExpiry = mock(CacheExpiry.class);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(CacheExpiry.class)).thenReturn(cacheExpiry);
        when(cacheExpiry.isExpirable(any(), any(), any())).thenReturn(isExpirable);
        return cacheExpiry;
    }

    private void enableContentSynchronisationAndProperties(PropertiesContentDescriptor propertiesContent) {
        when(descriptor.getContentSynchronisation().isEnabled()).thenReturn(true);
        when(propertiesContent.isEnabled()).thenReturn(true);
    }

    private void setUpAddonManager() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonManager);
        when(addonManager.addonByType(PropertiesAddon.class)).thenReturn(propertiesAddon);
        Properties properties = mock(Properties.class);
        when(properties.get(REMOTE_DELETED)).thenReturn(Set.of("value"));
        when(propertiesAddon.getProperties(any(RepoPath.class))).thenReturn(properties);
    }

    @Test
    void testWhenDescriptorNull() {
        Assertions.assertFalse(service.shouldSyncProperties(null, repo, repoResource));
    }

    @Test
    void testWhenRepoNull() {
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, null, repoResource));
    }

    @Test
    void testWhenRemoteRepoOffline() {
        when(repositoryService.isRemoteRepoOffline(any())).thenReturn(true);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testWhenGetContentSynchronisationIsNull() {
        when(repositoryService.isRemoteRepoOffline(any())).thenReturn(false);
        when(descriptor.getContentSynchronisation()).thenReturn(null);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testWhenGetContentSynchronisationOrGetPropertiesAreDisabled() {
        setupCommonMocks();
        PropertiesContentDescriptor propertiesContent = setupProperties();
        when(descriptor.getContentSynchronisation().isEnabled()).thenReturn(false);
        when(propertiesContent.isEnabled()).thenReturn(true);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
        when(descriptor.getContentSynchronisation().isEnabled()).thenReturn(true);
        when(propertiesContent.isEnabled()).thenReturn(false);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testWhenPathDoesntExist() {
        setupCommonMocks();
        PropertiesContentDescriptor propertiesContent = setupProperties();
        enableContentSynchronisationAndProperties(propertiesContent);
        when(repositoryService.exists(repoResource.getRepoPath())).thenReturn(false);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testWhenResourceIsExpirable() {
        setupCommonMocks();
        PropertiesContentDescriptor propertiesContent = setupProperties();
        enableContentSynchronisationAndProperties(propertiesContent);
        setupRepoPath(".npm");
        setupLocalCacheRepo(RepoType.Npm);
        setupCacheExpiry(true);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testWhenPropertiesNotExpired() {
        setupCommonMocks();
        PropertiesContentDescriptor propertiesContent = setupProperties();
        enableContentSynchronisationAndProperties(propertiesContent);
        setupRepoPath(".npm");
        setupLocalCacheRepo(RepoType.Npm);
        setupCacheExpiry(false);
        FileInfo fileInfo = mock(FileInfo.class);
        when(repositoryService.getFileInfo(any())).thenReturn(fileInfo);
        when(fileInfo.getLastUpdated()).thenReturn(System.currentTimeMillis());
        when(repo.getRetrievalCachePeriodSecs()).thenReturn(100L);
        Assertions.assertFalse(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testWhenAllConditionsAreMetAndThereforeShouldSyncProperties() {
        setupCommonMocks();
        PropertiesContentDescriptor propertiesContent = setupProperties();
        enableContentSynchronisationAndProperties(propertiesContent);
        setupRepoPath(".npm");
        setupLocalCacheRepo(RepoType.Npm);
        setupCacheExpiry(false);
        when(repositoryService.getFileInfo(any())).thenReturn(mock(FileInfo.class));
        Assertions.assertTrue(service.shouldSyncProperties(descriptor, repo, repoResource));
    }

    @Test
    void testArtifactAvailableRemotelyReturnFalseWhenIllegalStateExcOccurs() throws IOException {
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repositoryService.remoteRepositoryByKey(repoPath.getRepoKey())).thenReturn(repo);
        when(repo.getUrl()).thenReturn("http://host/name");
        when(repo.executeMethod(any(HttpHead.class))).thenThrow(new IllegalStateException("Test exception"));

        boolean result = service.isArtifactAvailableRemotely(repoResource);

        assertFalse(result);
        verify(repo, times(1)).executeMethod(any(HttpHead.class));
    }

    @Test
    void testArtifactAvailableRemotelyReturnFalseWhenUnknownHostExcOccurs() throws IOException {
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        when(repoResource.getRepoPath()).thenReturn(repoPath);
        when(repositoryService.remoteRepositoryByKey(repoPath.getRepoKey())).thenReturn(repo);
        when(repo.getUrl()).thenReturn("http://invalid-host/name");
        when(repo.executeMethod(any(HttpHead.class))).thenThrow(new UnknownHostException("Test exception"));

        boolean result = service.isArtifactAvailableRemotely(repoResource);

        assertFalse(result);
        verify(repo, times(1)).executeMethod(any(HttpHead.class));
    }

    @Test
    void testUpdatePropertiesAndDoSyncPropertiesReturnFalseWhenIllegalStateExcOccurs() throws IOException {
        setupCommonMocks();
        setupLocalCacheRepo(RepoType.Maven);
        setupCacheExpiry(false);
        setUpAddonManager();
        mockExpiredProperties();
        PropertiesContentDescriptor propertiesContent = setupProperties();
        when(propertiesContent.isEnabled()).thenReturn(true);
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        FileResource file = mock(FileResource.class);
        mockRepositoryService(repoPath);
        mockRepositoryConfigProperties(true);
        when(file.getRepoPath()).thenReturn(repoPath);
        when(file.isFound()).thenReturn(true);
        when(repo.getUrl()).thenReturn("http://host/name");
        when(contentSynchronisation.isEnabled()).thenReturn(true);

        doReturn(false).when(service).isArtifactMarkedAsRemoteDeleted(any());
        when(repo.executeMethod(any(HttpHead.class))).thenThrow(new IllegalStateException("Test exception"));

        boolean result = service.updateProperties(file);

        assertFalse(result);
        verify(repo, times(1)).executeMethod(any(HttpHead.class));
    }

    @Test
    void testUpdatePropertiesAndDoSyncSourceExistenceReturnTrueWhenIllegalStateExcOccurs() throws IOException {
        setupLocalCacheRepo(RepoType.Maven);
        setupCacheExpiry(false);
        setUpAddonManager();
        mockExpiredProperties();
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        FileResource file = mock(FileResource.class);
        mockRepositoryService(repoPath);
        mockRepositoryConfigProperties(false);
        when(file.getRepoPath()).thenReturn(repoPath);
        when(file.isFound()).thenReturn(true);
        when(repo.getUrl()).thenReturn("http://host/name");
        when(contentSynchronisation.isEnabled()).thenReturn(true);

        doReturn(false).when(service).isArtifactMarkedAsRemoteDeleted(any());
        when(repo.executeMethod(any(HttpHead.class))).thenThrow(new IllegalStateException("Test exception"));

        boolean result = service.updateProperties(file);

        assertTrue(result);
        verify(repo, times(1)).executeMethod(any(HttpHead.class));
    }

    @ParameterizedTest
    @MethodSource
    public void testArtifactMarkedAsRemoteDeleted(boolean isSmart, boolean isPropertiesSyncEnable,
            boolean expectedMarkedAsDeleted) {
        setUpAddonManager();
        setupRepoPath("path/to/artifact");
        when(repositoryService.getRemoteRepoConfigByKey(anyString())).thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(isSmart);
        when(repoConfig.isPropertiesSyncEnabled()).thenReturn(isPropertiesSyncEnable);
        boolean artifactAvailableRemotely = service.isArtifactMarkedAsRemoteDeleted(repoResource);
        assertEquals(expectedMarkedAsDeleted, artifactAvailableRemotely);
    }

    @Test
    public void testArtifactMarkedAsRemoteDeletedWhenRepoConfigNotFound() {
        setUpAddonManager();
        setupRepoPath("path/to/artifact");
        when(repositoryService.getRemoteRepoConfigByKey(anyString())).thenReturn(null);
        boolean artifactAvailableRemotely = service.isArtifactMarkedAsRemoteDeleted(repoResource);
        assertFalse(artifactAvailableRemotely);
    }

    @ParameterizedTest
    @MethodSource
    public void testOriginAbsenceDetectionEnabled(boolean isSmart, boolean isOriginAbsenceDetectionEnabled,
            boolean expectedAbsenceDetectionEnabled) {
        setupRepoPath("path/to/artifact");
        when(repositoryService.getRemoteRepoConfigByKey(anyString())).thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(isSmart);
        when(repoConfig.isOriginAbsenceDetectionEnabled()).thenReturn(isOriginAbsenceDetectionEnabled);
        boolean isAbsenceDetectionEnabled = service.isOriginAbsenceDetectionEnabled(repoResource);
        assertEquals(expectedAbsenceDetectionEnabled, isAbsenceDetectionEnabled);
    }

    @Test
    public void testOriginAbsenceDetectionEnabledNotFound() {
        setupRepoPath("path/to/artifact");
        when(repositoryService.getRemoteRepoConfigByKey(anyString())).thenReturn(null);
        boolean isAbsenceDetectionEnabled = service.isOriginAbsenceDetectionEnabled(repoResource);
        assertFalse(isAbsenceDetectionEnabled);
    }

    @ParameterizedTest
    @CsvSource({
            // Bridge URL - extracts full path up to and including /artifactory
            "/bridge-xxx/artifactory/repo-name/, /bridge-xxx/artifactory",
            // Bridge URL with nested path
            "/bridge-jfrog-platform-artifactory-8082/artifactory/" +
                    "npm-remote/package.json, /bridge-jfrog-platform-artifactory-8082/artifactory",
            // Regular URL with /artifactory context
            "/artifactory/repo-name/, /artifactory",
            // Regular URL with custom service name
            "/serviceName/repo-name/, /serviceName",
            // Root-level repo (no service name)
            "/repo-name/, /repo-name"
    })
    public void extractServiceContext_shouldReturnCorrectContext_whenGivenVariousPaths(String path, String expected) {
        assertEquals(expected, service.extractServiceContext(path));
    }

    @ParameterizedTest
    @CsvSource({
            // Null/empty paths - should return "/" + empty = "/"
            "'', /",
            "/, /"
    })
    public void extractServiceContext_shouldReturnSlash_whenPathIsEmptyOrRoot(String path, String expected) {
        assertEquals(expected, service.extractServiceContext(path));
    }

    @ParameterizedTest
    @CsvSource({
            // Bridge URL without /artifactory/ - should fall back to first element
            "/bridge-xxx/repo-name/, /bridge-xxx",
            // Path starting with /api/ (smart-repo pattern) - should return first element
            "/api/npm/npm-remote/, /api"
    })
    public void extractServiceContext_shouldReturnFirstElement_whenBridgeUrlWithoutArtifactory(String path,
                                                                                               String expected) {
        assertEquals(expected, service.extractServiceContext(path));
    }

    @Test
    void testIsSmartRepo_returnsFalseWhenRepoConfigNull() {
        when(repositoryService.getRemoteRepoConfigByKey("repo"))
                .thenReturn(null);
        assertFalse(service.isSmartRepo("repo"));
    }

    @Test
    void testIsSmartRepo_returnsFalseWhenNotSmart() {
        when(repositoryService.getRemoteRepoConfigByKey("repo"))
                .thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(false);
        assertFalse(service.isSmartRepo("repo"));
    }

    @Test
    void testIsSmartRepo_returnsFalseWhenUpstreamIsKnownPublicRegistry() {
        RemoteRepositoryRepoTypeConfig typeConfig =
                mock(RemoteRepositoryRepoTypeConfig.class);
        when(repositoryService.getRemoteRepoConfigByKey("repo"))
                .thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(typeConfig.getUrl())
                .thenReturn("https://repo1.maven.org/maven2/");
        when(researchService.isKnownPublicRegistryUrl(
                "https://repo1.maven.org/maven2/"))
                .thenReturn(true);

        assertFalse(service.isSmartRepo("repo"));
    }

    @Test
    void testIsSmartRepo_returnsTrueWhenSmartAndUrlAllowed() {
        RemoteRepositoryRepoTypeConfig typeConfig =
                mock(RemoteRepositoryRepoTypeConfig.class);
        when(repositoryService.getRemoteRepoConfigByKey("repo"))
                .thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(typeConfig.getUrl())
                .thenReturn("https://my-artifactory.example.com/");
        when(researchService.isKnownPublicRegistryUrl(
                "https://my-artifactory.example.com/"))
                .thenReturn(false);

        assertTrue(service.isSmartRepo("repo"));
    }

    @Test
    void testIsSmartRepo_returnsTrueWhenOnlyAdminBlacklisted() {
        RemoteRepositoryRepoTypeConfig typeConfig =
                mock(RemoteRepositoryRepoTypeConfig.class);
        when(repositoryService.getRemoteRepoConfigByKey("repo"))
                .thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(typeConfig.getUrl())
                .thenReturn("https://my-artifactory.example.com/");
        when(researchService.isKnownPublicRegistryUrl(
                "https://my-artifactory.example.com/"))
                .thenReturn(false);
        when(researchService.isUpstreamUrlBlockedForPropertySync(
                "https://my-artifactory.example.com/"))
                .thenReturn(true);

        assertTrue(service.isSmartRepo("repo"),
                "Admin property-sync blacklist should not affect "
                        + "smart repo determination");
    }

    @Test
    void testIsSmartRepo_stripsCacheSuffix() {
        RemoteRepositoryRepoTypeConfig typeConfig =
                mock(RemoteRepositoryRepoTypeConfig.class);
        when(repositoryService.getRemoteRepoConfigByKey("repo"))
                .thenReturn(repoConfig);
        when(repoConfig.isSmart()).thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(typeConfig.getUrl())
                .thenReturn("https://my-art.example.com/libs/");
        when(researchService.isKnownPublicRegistryUrl(
                "https://my-art.example.com/libs/"))
                .thenReturn(false);

        assertTrue(service.isSmartRepo("repo-cache"),
                "isSmartRepo should strip -cache suffix "
                        + "and look up 'repo'");
        verify(repositoryService)
                .getRemoteRepoConfigByKey("repo");
    }

    private void mockRepositoryService(RepoPath repoPath) {
        when(repositoryService.remoteRepositoryByKey(repoPath.getRepoKey())).thenReturn(repo);
        when(repositoryService.remoteRepoDescriptorByKey(repoPath.getRepoKey())).thenReturn(descriptor);
        when(repositoryService.getRemoteRepoConfigByKey(repoPath.getRepoKey())).thenReturn(repoConfig);
        when(repositoryService.exists(any(RepoPath.class))).thenReturn(true);
        when(repositoryService.getFileInfo(any())).thenReturn(fileInfo);
    }

    private void mockExpiredProperties() {
        service.setEpochTimeProvider(() -> 2000L);
        when(repo.getRetrievalCachePeriodSecs()).thenReturn(1L);
        when(fileInfo.getLastUpdated()).thenReturn(1L);
    }

    private void mockRepositoryConfigProperties(
            boolean isSyncEnabled) {
        RemoteRepositoryRepoTypeConfig typeConfig =
                mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoConfig.isSmart()).thenReturn(true);
        when(repoConfig.isPropertiesSyncEnabled())
                .thenReturn(isSyncEnabled);
        when(repoConfig.isOriginAbsenceDetectionEnabled())
                .thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(typeConfig.getUrl())
                .thenReturn("http://host/name");
        when(researchService.isKnownPublicRegistryUrl(
                "http://host/name")).thenReturn(false);
    }

    private static Stream<Arguments> testArtifactMarkedAsRemoteDeleted() {
        return Stream.of(
                Arguments.of(true, true, true),
                Arguments.of(true, false, false),
                Arguments.of(false, true, false),
                Arguments.of(false, false, false)
        );
    }

    private static Stream<Arguments> testOriginAbsenceDetectionEnabled() {
        return Stream.of(
                Arguments.of(true, true, true),
                Arguments.of(true, false, false),
                Arguments.of(false, true, false),
                Arguments.of(false, false, false)
        );
    }
}