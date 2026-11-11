package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageRepoDescriptorException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.model.KeyPair;
import com.jfrog.ph.acl.service.PackageArtifactoryConfigService;
import com.jfrog.ph.acl.vcs.configuration.PhVcsGitConfiguration;
import com.jfrog.ph.acl.vcs.configuration.PhVcsGitProvider;
import com.jfrog.ph.acl.vcs.configuration.PhVcsType;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.descriptor.repo.HuggingFaceMlGeneralConfig;
import org.artifactory.descriptor.repo.PypiGeneralConfig;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.VcsConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsType;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.common.repo.PackageType;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.descriptor.repo.vcs.VcsGitProvider.GITHUBENTERPRISE;
import static org.artifactory.descriptor.repo.vcs.VcsGitProvider.GITLAB;
import static org.artifactory.descriptor.repo.vcs.VcsGitProvider.GOOGLESOURCE;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ori Yafe
 */
class PackageArtifactoryConfigServiceImplTest {

    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    private AutoCloseable mockitoAutoCloseable;

    private PackageArtifactoryConfigService packageArtifactoryConfigService;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryHome home = Mockito.mock(ArtifactoryHome.class);
        when(home.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryHome.bind(home);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        packageArtifactoryConfigService =
                new PackageArtifactoryConfigServiceImpl(repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @Test
    void testGetStringFromConfig() throws PackageRepoDescriptorException {
        String repoKey = "local";
        String method = "getTerraformType";
        String value = "MODULE";
        prepareTest(repoKey);
        // no such field
        assertEquals("default",
                packageArtifactoryConfigService.getStringFromRepoConfig(repoKey, "nosuchmethod", "default"));
        assertEquals(value,
                packageArtifactoryConfigService.getStringFromRepoConfig(repoKey, method, "default"));
    }

    @Test
    void testGetIntegerFromConfig() throws PackageRepoDescriptorException {
        String repoKey = "remote-cache";
        String method = "getMaxUniqueTags";
        int value = 1;
        prepareCacheRepo("remote-cache");
        // no such method
        assertEquals(33, packageArtifactoryConfigService.getIntFromRepoConfig(repoKey, "nosuchmethod", 33));
        // casting error
        assertThrows(PackageRepoDescriptorException.class, () ->
                packageArtifactoryConfigService.getIntFromRepoConfig(repoKey, "isForceNugetAuthentication", 33));

        assertEquals(
                value, packageArtifactoryConfigService.getIntFromRepoConfig(repoKey, method, 33));
    }

    private void prepareCacheRepo(String repoKey) {
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        DbCacheRepo dbCacheRepo = mock(DbCacheRepo.class);
        when(dbCacheRepo.getKey()).thenReturn(repoKey);
        when(dbCacheRepo.getRemoteRepo()).thenReturn(remoteRepo);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(dbCacheRepo);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepo.getDescriptor()).thenReturn(remoteRepoDescriptor);
        when(remoteRepoDescriptor.getMaxUniqueTags()).thenReturn(1);
    }

    @Test
    void testGetArtifactoryBaseUrlFromHttpUrl() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        String requestUrl = "https://lala.land.com";
        when(centralConfigDescriptor.getUrlBase()).thenReturn(null);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        mockRequest.setServerName("lala.land.com");
        mockRequest.setScheme("https");
        mockRequest.setServerPort(443);
        String servletContextUrl = packageArtifactoryConfigService.getArtifactoryBaseUrl(mockRequest);
        assertEquals(requestUrl, servletContextUrl);
    }

    @Test
    void testGetArtifactoryBaseUrlFromDescriptorWithoutContextPath() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        String contextPath = "/artifactory";
        String requestUrl = "https://lala.land.com";
        when(artifactorySystemProperties.getProperty(ConstantValues.contextPath)).thenReturn(contextPath);
        when(centralConfigDescriptor.getUrlBase()).thenReturn(requestUrl);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        String servletContextUrl = packageArtifactoryConfigService.getArtifactoryBaseUrl(mockRequest);
        assertEquals(requestUrl + contextPath, servletContextUrl);
    }

    @Test
    void testGetArtifactoryBaseUrlFromDescriptorWithContextPath() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        String requestUrl = "https://lala.land.com/artifactory";
        when(centralConfigDescriptor.getUrlBase()).thenReturn(requestUrl);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("GET", requestUrl);
        String servletContextUrl = packageArtifactoryConfigService.getArtifactoryBaseUrl(mockRequest);
        assertEquals(requestUrl, servletContextUrl);
    }

    @Test
    void testGetBooleanFromConfig() throws PackageRepoDescriptorException {
        String repoKey = "local";
        String method = "isDebianTrivialLayout";
        prepareTest(repoKey);
        // no such method return def value
        assertTrue(packageArtifactoryConfigService.getBooleanFromRepoConfig(repoKey, "nosuchmethod", true));

        // casting error
        assertThrows(PackageRepoDescriptorException.class, () ->
                packageArtifactoryConfigService.getBooleanFromRepoConfig(repoKey, "getDockerTagRetention", true));

        assertFalse(
                packageArtifactoryConfigService.getBooleanFromRepoConfig(repoKey, method, false));
    }

    @Test
    void testGetVcsConfigOfRemoteRepoWhenNotExist() {
        String repoKey = "local";
        when(repositoryService.remoteRepoDescriptorByKey(repoKey)).thenReturn(null);
        try {
            packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
            fail("Expected exception");
        } catch (PackageException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getStatus());
            assertEquals("Remote repository 'local' was not found", e.getPublicReason());
        }
    }

    @Test
    void testGetVcsConfigOfRemoteRepoWhenNoVcs() {
        String repoKey = "local";
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);
        try {
            packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
            fail("Expected exception");
        } catch (PackageException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus());
            assertEquals("Remote repository 'local' does not have VCS configuration", e.getPublicReason());
        }
    }

    @Test
    void testGetVcsConfigOfRemoteRepo() throws PackageRepoDescriptorException {
        String repoKey = "remote";
        String githubUrl = "https://github.com";
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.getGit().setDownloadUrl(githubUrl);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);

        PhVcsGitConfiguration vcsGitConfiguration = packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
        assertEquals(PhVcsGitProvider.GITHUB, vcsGitConfiguration.getProvider());
        assertEquals(githubUrl, vcsGitConfiguration.getDownloadUrl());
    }

    @Test
    void testGetVcsConfigOfNonRemoteRepo() {
        String repoKey = "local";
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(localRepoDescriptor);
        assertThrows(PackageRepoDescriptorException.class,
                () -> packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey));
    }

    @Test
    void testGetVcsConfigOfRemoteRepoWhenCalledForCache() throws PackageRepoDescriptorException {
        String repoKey = "remote-cache";
        String githubUrl = "https://github.com";
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.getGit().setDownloadUrl(githubUrl);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        LocalCacheRepoDescriptor cacheRepoDescriptor = mock(LocalCacheRepoDescriptor.class);
        when(cacheRepoDescriptor.getRemoteRepo()).thenReturn(remoteRepoDescriptor);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(cacheRepoDescriptor);

        PhVcsGitConfiguration vcsGitConfiguration = packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
        assertEquals(PhVcsGitProvider.GITHUB, vcsGitConfiguration.getProvider());
        assertEquals(githubUrl, vcsGitConfiguration.getDownloadUrl());
    }

    @Test
    void testGetVcsConfigOfRemoteRepoGitLab() throws PackageRepoDescriptorException {
        String repoKey = "remote";
        String gitLabUrl = "https://gitlab.com";
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.getGit().setDownloadUrl(gitLabUrl);
        vcs.getGit().setProvider(GITLAB);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);

        PhVcsGitConfiguration vcsGitConfiguration = packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
        assertEquals(PhVcsGitProvider.GITLAB, vcsGitConfiguration.getProvider());
        assertEquals(gitLabUrl, vcsGitConfiguration.getDownloadUrl());
    }

    @Test
    void testGetVcsConfigOfRemoteRepoGitHubEnterprise() throws PackageRepoDescriptorException {
        String repoKey = "remote";
        String gitHubEnterpriseUrl = "https://github.enterprise.com";
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.getGit().setDownloadUrl(gitHubEnterpriseUrl);
        vcs.getGit().setProvider(GITHUBENTERPRISE);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);

        PhVcsGitConfiguration vcsGitConfiguration = packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
        assertEquals(PhVcsGitProvider.GITHUBENTERPRISE, vcsGitConfiguration.getProvider());
        assertEquals(gitHubEnterpriseUrl, vcsGitConfiguration.getDownloadUrl());
    }

    @Test
    void testGetVcsConfigOfRemoteRepoGoogleSource() throws PackageRepoDescriptorException {
        String repoKey = "remote";
        String googleSourceUrl = "https://chromium.googlesource.com";
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.getGit().setDownloadUrl(googleSourceUrl);
        vcs.getGit().setProvider(GOOGLESOURCE);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);

        PhVcsGitConfiguration vcsGitConfiguration = packageArtifactoryConfigService.getVcsConfigOfRemoteRepo(repoKey);
        //assertEquals(GOOGLESOURCE, vcsGitConfiguration.getProvider());
        assertEquals(googleSourceUrl, vcsGitConfiguration.getDownloadUrl());
    }

    @Test
    void testGetVcsTypeOfRemoteRepo() throws PackageRepoDescriptorException {
        String repoKey = "remote";
        VcsConfiguration vcs = new VcsConfiguration();
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);

        PhVcsType phVcsType = packageArtifactoryConfigService.getVcsTypeOfRemoteRepo(repoKey);
        assertEquals(VcsType.GIT.getVal(), phVcsType.getVal());
    }

    @Test
    void testGetVcsTypeOfRemoteRepoWithOutType() {
        String repoKey = "remote";
        VcsConfiguration vcs = new VcsConfiguration();
        vcs.setType(null);
        RemoteRepoDescriptor remoteRepoDescriptor = mock(RemoteRepoDescriptor.class);
        when(remoteRepoDescriptor.getVcs()).thenReturn(vcs);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(remoteRepoDescriptor);
        PackageRepoDescriptorException packageRepoDescriptorException =
                assertThrows(PackageRepoDescriptorException.class,
                        () -> packageArtifactoryConfigService.getVcsTypeOfRemoteRepo(repoKey));
        assertEquals("Remote repository '" + repoKey + "' does not have VCS Type configuration",
                packageRepoDescriptorException.getMessage());
    }

    @Test
    void testGetKeyPairConfigOfLocalRepoWhenNotExist() {
        String repoKey = "local";
        when(repositoryService.localOrFederatedRepositoryByKey(repoKey)).thenReturn(null);
        try {
            packageArtifactoryConfigService.getRepoKeyPair(repoKey);
            fail("Expected exception");
        } catch (PackageException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getStatus());
            assertEquals("Repository 'local' was not found", e.getPublicReason());
        }
    }

    @Test
    void testGetKeyPairConfigOfLocalRepo() throws PackageRepoDescriptorException {
        String repoKey = "local";
        KeyPairDescriptor keyPairDescriptor = new KeyPairDescriptor();
        keyPairDescriptor.setKey("key");
        keyPairDescriptor.setAlias("alias");
        keyPairDescriptor.setKeyType("ECDSA");
        keyPairDescriptor.setPrivateKey("private");
        keyPairDescriptor.setPublicKey("public");
        keyPairDescriptor.setPassphrase("passphrase");

        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(localRepoDescriptor.getPrimaryKeyPairDescriptor()).thenReturn(keyPairDescriptor);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(localRepoDescriptor);

        KeyPair keyPair = packageArtifactoryConfigService.getRepoKeyPair(repoKey);
        assertEquals("ECDSA", keyPair.getKeyType());
        assertEquals("key", keyPair.getKey());
        assertEquals("alias", keyPair.getAlias());
        assertEquals("public", keyPair.getPublicKey());
        assertEquals("private", keyPair.getPrivateKey());
        assertEquals("passphrase", keyPair.getPassphrase());
    }

    @Test
    void testGetKeyPairConfigOfLocalRepoNotExists() throws PackageRepoDescriptorException {
        String repoKey = "local";

        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(localRepoDescriptor.getPrimaryKeyPairDescriptor()).thenReturn(null);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(localRepoDescriptor);

        assertNull(packageArtifactoryConfigService.getRepoKeyPair(repoKey));
    }

    @Test
    void testGetPackageConfig_repoNotFound() {
        String repoKey = "local";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);
        PackageRepoNotFoundException exception = assertThrows(PackageRepoNotFoundException.class,
                () -> packageArtifactoryConfigService.getPackageTypeConfig(repoKey));
        assertEquals("Repository 'local' was not found", exception.getInternalReason());
    }

    @Test
    void testGetPackageConfig() throws PackageRepoNotFoundException {
        String repoKey = "local";
        Map<String, String> expectedMap = Map.of("stan", "bla");
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getPackageTypeConfig()).thenReturn(expectedMap);
        assertEquals(expectedMap, packageArtifactoryConfigService.getPackageTypeConfig(repoKey));
    }

    @Test
    void testGetPackageConfigForCacheRepo() throws PackageRepoNotFoundException {
        String repoKey = "remote-cache";
        Map<String, String> expectedMap = Map.of("stan", "bla");
        LocalCacheRepoConfig repoConfig = mock(LocalCacheRepoConfig.class);
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.getPackageTypeConfig()).thenReturn(expectedMap);
        when(repoConfig.getRemoteRepoConfig()).thenReturn(remoteRepoConfig);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        assertEquals(expectedMap, packageArtifactoryConfigService.getPackageTypeConfig(repoKey));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSetRepoBlackedOut(boolean isBlackedOut) {
        String repoKey = "local";
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        LocalRepositoryRepoTypeConfig localRepositoryRepoTypeConfig = new LocalRepositoryRepoTypeConfig();
        when(repositoryService.getRealRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoTypeConfig()).thenReturn(localRepositoryRepoTypeConfig);
        packageArtifactoryConfigService.setIsRepoBlackedOut(repoKey, isBlackedOut);
        assertEquals(isBlackedOut, repoConfig.getRepoTypeConfig().isBlackedOut());
    }

    private void prepareTest(String repoKey) {
        Repo<RepoDescriptor, ?> repo = mock(Repo.class);
        when(repo.getKey()).thenReturn(repoKey);
        when(repositoryService.repositoryByKey(repoKey)).thenReturn(repo);
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        when(repo.getDescriptor()).thenReturn(localRepoDescriptor);
        when(repo.isReal()).thenReturn(true);
        when(repo.isLocal()).thenReturn(true);
    }

    @Test
    void testGetDockerAccessMethodType() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        ReverseProxyDescriptor reverseProxyDescriptor = mock(ReverseProxyDescriptor.class);
        when(centralConfigDescriptor.getCurrentReverseProxy()).thenReturn(reverseProxyDescriptor);
        when(reverseProxyDescriptor.getDockerReverseProxyMethod()).thenReturn(ReverseProxyMethod.SUBDOMAIN);

        PackageArtifactoryConfigServiceImpl packageArtifactoryConfigServiceImpl =
                new PackageArtifactoryConfigServiceImpl(repositoryService);
        String result = packageArtifactoryConfigServiceImpl.getDockerAccessMethodType();
        assertEquals("subDomain", result);

        when(centralConfigDescriptor.getCurrentReverseProxy()).thenReturn(null);
        result = packageArtifactoryConfigServiceImpl.getDockerAccessMethodType();
        assertNull(result);
    }

    @Test
    void testGetRepoPort() {
        String repoKey = "my-repo";
        int port = 8081;

        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        ReverseProxyDescriptor reverseProxyDescriptor = mock(ReverseProxyDescriptor.class);
        when(centralConfigDescriptor.getCurrentReverseProxy()).thenReturn(reverseProxyDescriptor);
        ReverseProxyRepoConfig repoConfig = mock(ReverseProxyRepoConfig.class);
        when(repoConfig.getPort()).thenReturn(port);
        when(reverseProxyDescriptor.getReverseProxyRepoConfig(repoKey)).thenReturn(repoConfig);
        PackageArtifactoryConfigServiceImpl packageArtifactoryConfigServiceImpl
                = new PackageArtifactoryConfigServiceImpl(repositoryService);
        assertEquals(port, packageArtifactoryConfigServiceImpl.getRepoPort(repoKey));

        when(reverseProxyDescriptor.getReverseProxyRepoConfig(repoKey)).thenReturn(null);
        assertEquals(-1, packageArtifactoryConfigServiceImpl.getRepoPort(repoKey));

        when(centralConfigDescriptor.getCurrentReverseProxy()).thenReturn(null);
        assertEquals(-1, packageArtifactoryConfigServiceImpl.getRepoPort(repoKey));
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnEmptyJson_whenPackageTypeIsNull() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        when(configDescriptor.getRepositoriesGeneralConfig()).thenReturn(repoConfig);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(null);

        assertEquals("{}", result);
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnEmptyJson_whenDescriptorIsNull() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(null);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(PackageType.PYPI);

        assertEquals("{}", result);
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnEmptyJson_whenRepositoriesGeneralConfigIsNull() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        when(configDescriptor.getRepositoriesGeneralConfig()).thenReturn(null);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(PackageType.PYPI);

        assertEquals("{}", result);
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnEmptyJson_whenPackageTypeIsNotSupported() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        when(configDescriptor.getRepositoriesGeneralConfig()).thenReturn(repoConfig);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(PackageType.MAVEN);

        assertEquals("{}", result);
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnEmptyJson_whenConfigIsNull() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        repoConfig.setHuggingfaceml(null);
        when(configDescriptor.getRepositoriesGeneralConfig()).thenReturn(repoConfig);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(PackageType.HUGGINGFACEML);

        assertEquals("{}", result);
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnJson_whenHuggingFaceConfigExists() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        HuggingFaceMlGeneralConfig hfConfig = new HuggingFaceMlGeneralConfig(true, true);
        repoConfig.setHuggingfaceml(hfConfig);
        when(configDescriptor.getRepositoriesGeneralConfig()).thenReturn(repoConfig);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(PackageType.HUGGINGFACEML);

        assertTrue(result.contains("\"xetEnabled\":true"));
    }

    @Test
    void getGlobalPackageConfiguration_shouldReturnJson_whenPypiConfigExists() {
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor configDescriptor = mock(CentralConfigDescriptor.class);
        RepositoriesGeneralConfigDescriptor repoConfig = new RepositoriesGeneralConfigDescriptor();
        PypiGeneralConfig pypiConfig = new PypiGeneralConfig(true, true);
        repoConfig.setPypi(pypiConfig);
        when(configDescriptor.getRepositoriesGeneralConfig()).thenReturn(repoConfig);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        String result = packageArtifactoryConfigService.getGlobalPackageConfiguration(PackageType.PYPI);

        assertTrue(result.contains("\"simpleJsonFormatEnabled\":true"));
    }

    @Test
    void testSetPackageTypeConfigValue() {
        String repoKey = "local";
        String key = "myKey";
        String value = "myValue";
        Map<String, String> packageTypeConfig = new HashMap<>();
        RealRepoConfig repoConfig = mock(RealRepoConfig.class);
        when(repositoryService.getRealRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);

        packageArtifactoryConfigService.setPackageTypeConfigValue(repoKey, key, value);

        assertEquals(value, packageTypeConfig.get(key));
        verify(repositoryService).updateRepoConfig(repoConfig);
    }

    @Test
    void testSetPackageTypeConfigValueRepoNotFound() {
        String repoKey = "nonexistent";
        when(repositoryService.getRealRepoConfigByKey(repoKey)).thenReturn(null);

        packageArtifactoryConfigService.setPackageTypeConfigValue(repoKey, "key", "value");

        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void markRepositoryAsNormalizedWritesEnableNormalizedVersionAndBypassMarkerInOneUpdate() {
        String repoKey = "nuget-local12";
        Map<String, String> packageTypeConfig = new HashMap<>();
        RealRepoConfig repoConfig = mock(RealRepoConfig.class);
        when(repositoryService.getRealRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getPackageTypeConfig()).thenReturn(packageTypeConfig);

        packageArtifactoryConfigService.markRepositoryAsNormalized(repoKey);

        // Both the real flag and the transient bypass marker are written.
        // The interceptor will strip the marker before persistence.
        assertEquals("true", packageTypeConfig.get("enableNormalizedVersion"));
        assertEquals("true",
                packageTypeConfig.get(PackageRepoConfigChangeInterceptor.SKIP_NORMALIZATION_OVERRIDE_MARKER));
        // Single updateRepoConfig() call - both writes happen atomically so the interceptor
        // sees them in the same onBeforeSave invocation.
        verify(repositoryService).updateRepoConfig(repoConfig);
    }

    @Test
    void markRepositoryAsNormalizedNoOpsWhenRepositoryNotFound() {
        String repoKey = "nonexistent";
        when(repositoryService.getRealRepoConfigByKey(repoKey)).thenReturn(null);

        packageArtifactoryConfigService.markRepositoryAsNormalized(repoKey);

        verify(repositoryService, never()).updateRepoConfig(any());
    }

    @Test
    void testCreateLocalRepository() {
        String repoKey = "my-maven-local";
        String packageType = "maven";

        packageArtifactoryConfigService.createLocalRepository(repoKey, packageType, null);

        verify(repositoryService).upsertRepoConfig(any(LocalRepoConfig.class));
    }

    @Test
    void testCreateLocalRepositoryWithDescription() {
        String repoKey = "my-maven-local";
        String packageType = "maven";
        String description = "My Maven repo";

        packageArtifactoryConfigService.createLocalRepository(repoKey, packageType, description);

        verify(repositoryService).upsertRepoConfig(any(LocalRepoConfig.class));
    }

    @Test
    void testCreateLocalRepositoryUnknownPackageType() {
        String repoKey = "my-repo";
        String packageType = "invalidType";

        assertThrows(IllegalArgumentException.class,
                () -> packageArtifactoryConfigService.createLocalRepository(repoKey, packageType, null));

        verify(repositoryService, never()).upsertRepoConfig(any());
    }

    @Test
    void testCreateLocalRepositoryGenericPackageType() {
        String repoKey = "my-generic-local";
        String packageType = "generic";

        packageArtifactoryConfigService.createLocalRepository(repoKey, packageType, null);

        verify(repositoryService).upsertRepoConfig(any(LocalRepoConfig.class));
    }

    @Test
    void testRemoveRepositorySuccess() {
        String repoKey = "my-repo";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        when(repositoryService.removeRepository(repoKey)).thenReturn(statusHolder);

        assertTrue(packageArtifactoryConfigService.removeRepository(repoKey));
        verify(repositoryService).removeRepository(repoKey);
    }

    @Test
    void testRemoveRepositoryErrorStatus() {
        String repoKey = "my-repo";
        BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
        when(statusHolder.isError()).thenReturn(true);
        org.artifactory.common.status.StatusEntry errorEntry =
                new org.artifactory.common.status.StatusEntry(500, "Removal failed");
        when(statusHolder.getLastError()).thenReturn(errorEntry);
        when(repositoryService.removeRepository(repoKey)).thenReturn(statusHolder);

        assertFalse(packageArtifactoryConfigService.removeRepository(repoKey));
    }

    @Test
    void testRemoveRepositoryException() {
        String repoKey = "my-repo";
        when(repositoryService.removeRepository(repoKey)).thenThrow(new RuntimeException("Unexpected error"));

        assertFalse(packageArtifactoryConfigService.removeRepository(repoKey));
    }
}