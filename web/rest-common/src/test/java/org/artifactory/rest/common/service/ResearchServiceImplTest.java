package org.artifactory.rest.common.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.features.matrix.SmartRepoVersionFeatures;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.HttpRepositoryConfigurationImpl;
import org.artifactory.repo.remote.ContentSynchronisation;
import org.artifactory.util.HttpClientConfigurator;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.annotation.Nonnull;
import org.jfrog.client.util.KeyStoreProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyStore;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Dan Feldman
 * @author Avishay Halpren
 */
public class ResearchServiceImplTest {

    @Mock
    SmartRepoVersionFeatures versionFeatures;
    @Mock
    CentralConfigService configService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    HttpClientConfiguratorProvider httpClientConfiguratorprovider;
    HttpClientConfigurator httpClientConfigurator;

    private ResearchServiceImpl researchService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getProperty(any())).thenReturn("0");
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(AddonsManager.class))
                .thenReturn(addonsManager);
        ArtifactoryContextThreadBinder.bind(context);
        httpClientConfigurator = mock(HttpClientConfigurator.class, Mockito.RETURNS_SELF);
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @BeforeMethod
    public void setUp() {
        researchService = new ResearchServiceImpl();
        researchService.setSmartRepoVersionFeatures(versionFeatures);
        researchService.setConfigService(configService);
        researchService.setAddonsManager(addonsManager);
        researchService.setHttpClientConfiguratorProvider(httpClientConfiguratorprovider);
        researchService.initUrlLists();
    }

    @Test
    public void syncPropsDisabledWhenNoContentSyncAndNoPropSync() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setSynchronizeProperties(false);
        repoConfig.getRepoTypeConfig().setContentSynchronisation(null);
        assertFalse(researchService.isRepoConfiguredToSyncProperties(repoConfig));
    }

    @Test
    public void syncPropsDisabledWhenContentSyncPresentAndDisabledAndNoPropSync() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setSynchronizeProperties(false);
        ContentSynchronisation contentSynchronisation =
                new ContentSynchronisation();
        contentSynchronisation.setEnabled(false);
        repoConfig.getRepoTypeConfig().setContentSynchronisation(contentSynchronisation);
        assertFalse(researchService.isRepoConfiguredToSyncProperties(repoConfig));
    }

    @Test
    public void syncPropsDisabledWhenContentSyncEnabledAndContentPropSyncDisabledAndPropSyncDisabled() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setSynchronizeProperties(false);
        ContentSynchronisation contentSynchronisation =
                new ContentSynchronisation();
        contentSynchronisation.setEnabled(true);
        repoConfig.getRepoTypeConfig().setContentSynchronisation(contentSynchronisation);
        assertFalse(researchService.isRepoConfiguredToSyncProperties(repoConfig));
    }

    @Test
    public void syncPropsEnabledWhenNoContentSyncAndPropSyncEnabled() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setSynchronizeProperties(true);
        repoConfig.getRepoTypeConfig().setContentSynchronisation(null);
        assertTrue(researchService.isRepoConfiguredToSyncProperties(repoConfig));
    }

    @Test
    public void syncPropsEnabledWhenContentSyncEnabledAndContentPropSyncEnabledAndPropSyncDisabled() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setSynchronizeProperties(false);
        ContentSynchronisation contentSynchronisation = new ContentSynchronisation();
        contentSynchronisation.setEnabled(true);
        contentSynchronisation.getProperties().setEnabled(true);
        repoConfig.getRepoTypeConfig().setContentSynchronisation(contentSynchronisation);
        assertTrue(researchService.isRepoConfiguredToSyncProperties(repoConfig));
    }

    @Test
    public void isRealRepoOrAllowedVirtual_allowedVirtualRepos() throws IOException {
        for (RepoType repoType : ResearchServiceImpl.ALLOWED_VIRTUAL_REPO_TYPES) {
            try (CloseableHttpClient client = getClient(repoType.getType())) {
                String repoKey = repoType.getType() + "-virtual";
                URI uri = URI.create("http://admin:password1@localhost:18082/artifactory/" + repoKey + "/" +
                        "helm/elasticsearch/elasticsearch-7.6.0.tgz");
                configureAddonsManager();

                ResearchServiceImpl.RepoInfo result = researchService.getRepoInfo(client, uri, repoKey);
                assertEquals(ResearchServiceImpl.RepoInfo.ALLOWED_SMART, result);
            }
        }
    }

    @Test
    public void isRealRepoOrAllowedVirtual_notAllowedVirtualRepos() throws IOException {
        Set<RepoType> notAllowedRepoTypes = Stream.of(RepoType.values())
                .filter(repoType -> !ResearchServiceImpl.ALLOWED_VIRTUAL_REPO_TYPES.contains(repoType))
                .collect(Collectors.toSet());
        for (RepoType repoType : notAllowedRepoTypes) {
            try (CloseableHttpClient client = getClient(repoType.getType())) {
                String repoKey = repoType.getType() + "-virtual";
                URI uri = URI.create("http://admin:password1@localhost:18082/artifactory/" + repoKey + "/" +
                        "helm/elasticsearch/elasticsearch-7.6.0.tgz");
                configureAddonsManager();

                ResearchServiceImpl.RepoInfo result = researchService.getRepoInfo(client, uri, repoKey);
                assertEquals(result, ResearchServiceImpl.RepoInfo.NOT_ALLOWED_SMART);
            }
        }
    }

    @Test
    public void isRealRepoOrAllowedVirtual_noSuchRepoType() throws IOException {
        String packageType = "noSuchPackageType";
        try (CloseableHttpClient client = getClient(packageType)) {
            String repoKey = packageType + "-virtual";
            URI uri = URI.create("http://admin:password1@localhost:18082/artifactory/" + repoKey + "/" +
                    "helm/elasticsearch/elasticsearch-7.6.0.tgz");
            configureAddonsManager();

            ResearchServiceImpl.RepoInfo result = researchService.getRepoInfo(client, uri, repoKey);
            assertEquals(ResearchServiceImpl.RepoInfo.NOT_ALLOWED_SMART, result);
        }
    }

    @Test
    public void isDefaultPlatformProxyUsedWhenCreateHttpClient() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getDefaultProxy()).thenReturn(new ProxyDescriptor());
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor).getDefaultProxy();
    }

    @Test
    public void isConfigRepoProxyUsedWhenCreateHttpClient() {
        String proxyKey = "proxyKey";
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        repoConfig.setProxyRef(proxyKey);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getProxy(proxyKey)).thenReturn(new ProxyDescriptor());
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor).getProxy(proxyKey);
    }

    @Test
    public void isConfigRepoProxyRefExistsAndProxyNullWhenCreateHttpClient() {
        String proxyKey = "proxyKey";
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        repoConfig.setProxyRef(proxyKey);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        ProxyDescriptor defaultProxy = new ProxyDescriptor();

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getProxy(proxyKey)).thenReturn(null);
        when(descriptor.getDefaultProxy()).thenReturn(defaultProxy);
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor).getProxy(proxyKey);
        verify(descriptor).getDefaultProxy();
        verify(httpClientConfigurator).proxy(defaultProxy);
    }

    @Test
    public void isProxyDisableTrueWhenCreateHttpClient() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        repoConfig.setDisableProxy(true);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
    }

    @Test
    public void isDefaultPlatformProxyUsedWhenCreateHttpClientForHttpRepConfig() {
        HttpRepositoryConfigurationImpl repoConfig = new HttpRepositoryConfigurationImpl();
        repoConfig.setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.setSocketTimeoutMillis(1000);
        repoConfig.setUsername("admin");
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getDefaultProxy()).thenReturn(new ProxyDescriptor());
        when(httpClientConfigurator.build()).thenReturn(httpClient);
        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor).getDefaultProxy();
    }

    @Test
    public void invalidProxyRefFallsBackToDefaultProxyWhenCreateHttpClientForHttpRepConfig() {
        String invalidProxyKey = "nonExistentProxy";
        HttpRepositoryConfigurationImpl repoConfig = new HttpRepositoryConfigurationImpl();
        repoConfig.setKey("repoKey");
        repoConfig.setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.setSocketTimeoutMillis(1000);
        repoConfig.setUsername("admin");
        repoConfig.setProxy(invalidProxyKey);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        ProxyDescriptor defaultProxy = new ProxyDescriptor();

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getProxy(invalidProxyKey)).thenReturn(null);
        when(descriptor.getDefaultProxy()).thenReturn(defaultProxy);
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor).getProxy(invalidProxyKey);
        verify(descriptor).getDefaultProxy();
        verify(httpClientConfigurator).proxy(defaultProxy);
    }

    @Test
    public void blankProxyRefFallsBackToDefaultProxyWhenCreateHttpClientForHttpRepConfig() {
        String blankProxyKey = "";
        HttpRepositoryConfigurationImpl repoConfig = new HttpRepositoryConfigurationImpl();
        repoConfig.setKey("repoKey");
        repoConfig.setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.setSocketTimeoutMillis(1000);
        repoConfig.setUsername("admin");
        repoConfig.setProxy(blankProxyKey);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        ProxyDescriptor defaultProxy = new ProxyDescriptor();

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getDefaultProxy()).thenReturn(defaultProxy);
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor, never()).getProxy(blankProxyKey);
        verify(descriptor).getDefaultProxy();
        verify(httpClientConfigurator).proxy(defaultProxy);
    }

    @Test
    public void whitespaceProxyRefFallsBackToDefaultProxyWhenCreateHttpClient() {
        String blankProxyKey = "   ";
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        repoConfig.setProxyRef(blankProxyKey);
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        ProxyDescriptor defaultProxy = new ProxyDescriptor();

        when(httpClientConfiguratorprovider.getHttpClientConfigurator()).thenReturn(httpClientConfigurator);
        when(configService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getDefaultProxy()).thenReturn(defaultProxy);
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(descriptor, never()).getProxy(blankProxyKey);
        verify(descriptor).getDefaultProxy();
        verify(httpClientConfigurator).proxy(defaultProxy);
    }

    @Test
    public void clientCertConfiguredWhenCertSetOnRemoteRepoConfig()
            throws Exception {
        RemoteRepoConfig repoConfig =
                RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig()
                .setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        repoConfig.getRepoTypeConfig()
                .setClientTlsCertificate("my-cert");
        repoConfig.setDisableProxy(true);

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        ArtifactWebstartAddon webstartAddon =
                mock(ArtifactWebstartAddon.class);
        KeyStore keyStore = mock(KeyStore.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator())
                .thenReturn(httpClientConfigurator);
        when(httpClientConfigurator.build()).thenReturn(httpClient);
        when(addonsManager.addonByType(ArtifactWebstartAddon.class))
                .thenReturn(webstartAddon);
        when(webstartAddon.getExistingKeyStore()).thenReturn(keyStore);
        when(webstartAddon.getKeystorePassword()).thenReturn("pass");

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(httpClientConfigurator)
                .clientCertKeyStoreProvider(any(KeyStoreProvider.class));
        verify(httpClientConfigurator)
                .clientCertAlias("artsslcert:my-cert");
    }

    @Test
    public void clientCertNotConfiguredWhenNoCertOnRemoteRepoConfig() {
        RemoteRepoConfig repoConfig =
                RemoteRepoConfig.create("repoKey", RepoType.Generic);
        repoConfig.getRepoTypeConfig()
                .setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.getRepoTypeConfig().setSocketTimeoutMillis(1000);
        repoConfig.getRepoTypeConfig().setUsername("admin");
        repoConfig.setDisableProxy(true);

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator())
                .thenReturn(httpClientConfigurator);
        when(httpClientConfigurator.build()).thenReturn(httpClient);

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(httpClientConfigurator, never())
                .clientCertKeyStoreProvider(any());
        verify(httpClientConfigurator, never())
                .clientCertAlias(anyString());
    }

    @Test
    public void clientCertConfiguredWhenCertSetOnHttpRepoConfig()
            throws Exception {
        HttpRepositoryConfigurationImpl repoConfig =
                new HttpRepositoryConfigurationImpl();
        repoConfig.setUrl("http://server:8081/artifactory/repoKey/");
        repoConfig.setSocketTimeoutMillis(1000);
        repoConfig.setUsername("admin");
        repoConfig.setClientTlsCertificate("my-cert");
        repoConfig.setDisableProxy(true);

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        ArtifactWebstartAddon webstartAddon =
                mock(ArtifactWebstartAddon.class);
        KeyStore keyStore = mock(KeyStore.class);

        when(httpClientConfiguratorprovider.getHttpClientConfigurator())
                .thenReturn(httpClientConfigurator);
        when(httpClientConfigurator.build()).thenReturn(httpClient);
        when(addonsManager.addonByType(ArtifactWebstartAddon.class))
                .thenReturn(webstartAddon);
        when(webstartAddon.getExistingKeyStore()).thenReturn(keyStore);
        when(webstartAddon.getKeystorePassword()).thenReturn("pass");

        assertNotNull(researchService.createHttpClient(repoConfig));
        verify(httpClientConfigurator)
                .clientCertKeyStoreProvider(any(KeyStoreProvider.class));
        verify(httpClientConfigurator)
                .clientCertAlias("artsslcert:my-cert");
    }

    @DataProvider
    private Object[][] provideUrls() {
        return new Object[][] {
                // Standard Artifactory URLs
                {"http://server:8081/artifactory/repoName/", "http://server:8081/artifactory"},
                {"http://serverName/artifactory/repoName/", "http://serverName/artifactory"},
                {"https://server/artifactory/repoName/", "https://server/artifactory"},
                {"https://server/artifactory/api/npm/npm-remote/", "https://server/artifactory"},
                // Root URL with smart-repo pattern
                {"http://my.server.com/api/docker/docker-remote/", "http://my.server.com"},
                {"https://my.server.com/api/docker/docker-remote/", "https://my.server.com"},
                // Custom service name (reverse proxy)
                {"https://serverName/routed/docker-remote/", "https://serverName/routed"},
                {"https://serverName/artifactory2/docker-remote/", "https://serverName/artifactory2"},
                // Root URL without service name
                {"https://serverName/docker-remote/", "https://serverName"},
                // JFrog Bridge URLs - path /bridge-xxx/artifactory/...
                {"http://localhost:8046/bridge-dynamic1/artifactory/api/go/sh-go/",
                        "http://localhost:8046/bridge-dynamic1/artifactory"},
                {"https://server:8082/bridge-xyz/artifactory/npm-remote/",
                        "https://server:8082/bridge-xyz/artifactory"},
                {"http://localhost/bridge-test/artifactory/api/npm/npm-remote/",
                        "http://localhost/bridge-test/artifactory"},
        };
    }

    @Test(dataProvider = "provideUrls")
    public void produceVersionUrl_shouldReturnCorrectBaseUrl(String inputUrl, String expectedBaseUrl) {
        String result = researchService.produceVersionUrl(URI.create(inputUrl));
        assertEquals(result, expectedBaseUrl);
    }

    @DataProvider
    private Object[][] provideBridgeUrls() {
        return new Object[][] {
                {"/bridge-dynamic1/artifactory/api/go/sh-go/", "/bridge-dynamic1/artifactory"},
                {"/bridge-xyz/artifactory/npm-remote/", "/bridge-xyz/artifactory"},
                {"/bridge-simple/artifactory/repo/", "/bridge-simple/artifactory"},
                // Not Bridge: /bridge- must be at path root (matches full path /bridge-.*)
                {"/some/path/bridge-test/artifactory/repo/file.txt", null},
                // Bridge URLs without /artifactory/ - should return null
                {"/bridge-xxx/repo/", null},
                {"/bridge-test/some-repo/", null},
                // Non-Bridge URLs - should return null
                {"/artifactory/repo-name/", null},
                {"/api/docker/docker-remote/", null},
                {"/some-repo/file.txt", null},
        };
    }

    @Test(dataProvider = "provideBridgeUrls")
    public void extractBridgeServiceName_shouldExtractCorrectServiceName(String path, String expected) {
        String result = researchService.extractBridgeServiceName(path);
        assertEquals(result, expected);
    }

    @Nonnull
    private CloseableHttpClient getClient(String packageType) throws IOException {
        CloseableHttpClient stubClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = getResponse(packageType);
        when(stubClient.execute(any())).thenReturn(response);
        return stubClient;
    }

    private void configureAddonsManager() {
        HaCommonAddon addon = mock(HaCommonAddon.class);
        when(addon.getHostId()).thenReturn("host123");
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(addon);
    }

    @Nonnull
    private CloseableHttpResponse getResponse(String packageType) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        InputStream repoConfigInputStream = getVirtualRepoConfigAsInputStream(packageType);
        HttpEntity httpEntity = mock(HttpEntity.class);
        when(httpEntity.getContent()).thenReturn(repoConfigInputStream);
        when(response.getEntity()).thenReturn(httpEntity);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

    @Test
    public void testGetDefaultUrlsContainsHttpVariants() {
        Set<String> urls = researchService.getDefaultUrls();
        assertTrue(urls.contains(
                "https://repo1.maven.org/maven2"));
        assertTrue(urls.contains(
                "http://repo1.maven.org/maven2"));
    }

    @Test
    public void testIsUpstreamUrlBlockedForDefaultUrl() {
        assertTrue(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://repo1.maven.org/maven2/"));
    }

    @Test
    public void testIsUpstreamUrlNotBlockedForCustomUrl() {
        assertFalse(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://my-artifactory.example.com/repo/"));
    }

    @Test
    public void testIsUpstreamUrlNotBlockedForBlank() {
        assertFalse(researchService
                .isUpstreamUrlBlockedForPropertySync(""));
        assertFalse(researchService
                .isUpstreamUrlBlockedForPropertySync(null));
    }

    @Test
    public void testIsKnownPublicRegistryUrl_matchesDefaultUrl() {
        assertTrue(researchService
                .isKnownPublicRegistryUrl(
                        "https://repo1.maven.org/maven2/"));
    }

    @Test
    public void testIsKnownPublicRegistryUrl_doesNotMatchCustomUrl() {
        assertFalse(researchService
                .isKnownPublicRegistryUrl(
                        "https://my-artifactory.example.com/repo/"));
    }

    @Test
    public void testIsKnownPublicRegistryUrl_doesNotMatchBlank() {
        assertFalse(researchService.isKnownPublicRegistryUrl(""));
        assertFalse(researchService.isKnownPublicRegistryUrl(null));
    }

    @Test
    public void testIsKnownPublicRegistryUrl_ignoresAdminBlacklist() {
        // Admin-blacklisted URL should NOT be considered
        // a known public registry
        Set<String> blacklist = researchService.getDefaultUrls()
                .stream()
                .collect(Collectors.toSet());
        String adminBlockedUrl =
                "https://my-private-artifactory.example.com/";
        assertFalse(blacklist.contains(adminBlockedUrl),
                "Test precondition: URL must not be in defaults");
        assertTrue(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://repo1.maven.org/maven2/"),
                "Sanity: default URL is blocked for property sync");
        assertFalse(researchService
                .isKnownPublicRegistryUrl(adminBlockedUrl));
    }

    @Test
    public void testIsKnownPublicRegistryUrl_trailingSlashVariant() {
        // npm registry is listed without trailing slash
        assertTrue(researchService.isKnownPublicRegistryUrl(
                "https://registry.npmjs.org"));
        assertTrue(researchService.isKnownPublicRegistryUrl(
                "https://registry.npmjs.org/"));

        // Docker Hub is listed with trailing slash
        assertTrue(researchService.isKnownPublicRegistryUrl(
                "https://registry-1.docker.io/"));
        assertTrue(researchService.isKnownPublicRegistryUrl(
                "https://registry-1.docker.io"));
    }

    @Test
    public void testIsUpstreamUrlBlocked_trailingSlashVariant() {
        assertTrue(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://registry.npmjs.org"));
        assertTrue(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://registry.npmjs.org/"));

        assertTrue(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://registry-1.docker.io/"));
        assertTrue(researchService
                .isUpstreamUrlBlockedForPropertySync(
                        "https://registry-1.docker.io"));
    }

    @Test
    public void testNormalizeUrl() {
        assertEquals(
                ResearchServiceImpl.normalizeUrl("https://example.com/"),
                "https://example.com");
        assertEquals(
                ResearchServiceImpl.normalizeUrl("https://example.com"),
                "https://example.com");
        assertEquals(
                ResearchServiceImpl.normalizeUrl("https://a.com///"),
                "https://a.com");
        assertEquals(
                ResearchServiceImpl.normalizeUrl("  https://a.com/  "),
                "https://a.com");
        assertEquals(
                ResearchServiceImpl.normalizeUrl(null), null);
        assertEquals(
                ResearchServiceImpl.normalizeUrl(""), "");
    }

    @Nonnull
    private InputStream getVirtualRepoConfigAsInputStream(String packageType) throws IOException {
        HttpRepositoryConfigurationImpl httpRepositoryConfiguration = new HttpRepositoryConfigurationImpl();
        httpRepositoryConfiguration.setType("virtual");
        httpRepositoryConfiguration.setPackageType(packageType);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonGenerator jsonGenerator = JacksonFactory.createGenerator(outputStream);

        // Real filter from HttpRepositoryConfigurationImpl should be overridden with dummy one for tests
        SimpleFilterProvider dummy = new SimpleFilterProvider();
        dummy.setFailOnUnknownId(false);
        new ObjectMapper().writer(dummy).writeValue(jsonGenerator, httpRepositoryConfiguration);

        byte[] bytes = outputStream.toByteArray();
        return new ByteArrayInputStream(bytes);
    }
}