/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo;

import com.google.common.collect.ArrayListMultimap;
import com.jfrog.sysconf.SysConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.Nonnull;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.curation.CurationCachedPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatusPayload;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.plugin.PluginAction;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.RemoteRequestCtx;
import org.artifactory.addon.plugin.download.BeforeRemoteDownloadAction;
import org.artifactory.addon.plugin.download.BeforeRemoteInfoRequestAction;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.InternalRequestFactory;
import org.artifactory.exceptions.IllegalUrlPathException;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.download.RateLimiterException;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.http.SrcTenantIdHeaderInterceptor;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.md.Properties;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.config.RepoConfigDefaultValues.DefaultUrl;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.GuaveCacheConnectionManagersHolder;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.request.UrlVerifier;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.RemoteRepoResource;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.service.SmartRepoService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.artifactory.traffic.OutboundRequestLogger;
import org.artifactory.traffic.TrafficService;
import org.artifactory.unifiedevent.UnifiedEventServiceImpl;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.client.http.JFrogDefaultRedirectStrategy;
import org.jfrog.client.util.DenylistValidator;
import org.jfrog.common.JsonSensitiveDataException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.artifactory.api.request.ArtifactoryHeaders.CURATION_ORIGIN;
import static org.artifactory.api.request.ArtifactoryHeaders.ORIGIN_REMOTE_PATH;
import static org.artifactory.api.request.ArtifactoryHeaders.XRAY_ORIGIN;
import static org.artifactory.api.request.ArtifactoryResponse.ARTIFACTORY_ID;
import static org.artifactory.common.ConstantValues.ignoreRedirectStrategy;
import static org.artifactory.common.ConstantValues.xrayRemoteValidate;
import static org.artifactory.repo.HttpRepo.BITBUCKET_API_HOST;
import static org.artifactory.repo.HttpRepo.GITHUB_API_HOST;
import static org.artifactory.repo.HttpRepo.GITHUB_RAW_HOST;
import static org.artifactory.repo.HttpRepo.GITLAB_API_HOST;
import static org.artifactory.repo.db.DbStoringRepoMixin.ORIGIN_REMOTE_PATH_PROP_KEY;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNull;

/**
 * @author Yoav Landman
 */
public class HttpRepoTest extends ArtifactoryHomeBoundJUnitTest {

    private static final CurationPackageStatus PERMITTED_STATUS = new CurationPackageStatus(true, null, null);
    private static final CurationPackageStatus BLOCKED_STATUS = new CurationPackageStatus(false, null, null);
    private static final RemoteRepoConfig REPO_CONFIG = prepareRemoteRepoConfig(false);
    private static final RepoPath REPO_PATH =
            InternalRepoPathFactory.create("test-repo", "path/to/artifact");

    @Mock
    private InternalRepositoryService internalRepoService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private IRequestRateLimiterService rateLimiterService;
    @Mock
    private ResearchService researchService;
    @Mock
    private InternalArtifactoryContext contextMock;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private LayoutsCoreAddon layoutsCoreAddon;
    @Mock
    private HaAddon haAddon;
    @Mock
    private CurationAddon curationAddon;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private PluginsAddon pluginsAddon;
    @Mock
    private FileService fileService;
    @Mock
    private HttpConnectionPoolMonitorService httpConnectionPoolMonitorService;
    @Mock
    private MBeanRegistrationServiceImpl mBeanRegistrationService;
    @Mock
    private EdgeSmartRepoAddon edgeSmartRepoAddon;
    @Mock
    private AolService aolService;
    @Mock
    private TrafficService trafficService;
    @Mock
    private UrlVerifier urlVerifier;
    @Mock
    private SmartRepoService smartRepoService;

    @Mock
    private OnlineMonitorService onlineMonitorService;
    @Mock
    private WorkersAddon workersAddon;
    @Mock
    private UnifiedEventServiceImpl unifiedEventService;

    private HttpRepoDescriptor descriptor;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(ConstantValues.artifactoryProductVersion.getPropertyName(), "true");
        mocks = MockitoAnnotations.openMocks(this);
        getBound().setProperty(ConstantValues.idleConnectionMonitorInterval, "10");
        getBound().setProperty(ConstantValues.disableIdleConnectionMonitoring, "false");
        IdleConnectionMonitorService idleConnectionMonitorService =
                new IdleConnectionMonitorServiceImpl(new GuaveCacheConnectionManagersHolder());
        when(addonsManager.addonByType(LayoutsCoreAddon.class)).thenReturn(layoutsCoreAddon);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(addonsManager.addonByType(PluginsAddon.class)).thenReturn(pluginsAddon);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(curationAddon);
        when(curationAddon.getPackageStatus(any(), any(), any(), any(), anyBoolean())).thenReturn(
                new CurationCachedPackageStatus(null, PERMITTED_STATUS));
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);
        when(addonsManager.addonByType(WorkersAddon.class)).thenReturn(workersAddon);
        when(contextMock.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(contextMock.beanForType(IdleConnectionMonitorService.class)).thenReturn(idleConnectionMonitorService);
        when(contextMock.beanForType(DenyListCacheHolderService.class)).thenReturn(
                mock(DenyListCacheHolderService.class));
        when(contextMock.beanForType(FileService.class)).thenReturn(fileService);
        when(contextMock.beanForType(MBeanRegistrationServiceImpl.class)).thenReturn(mBeanRegistrationService);
        when(contextMock.beanForType(HttpConnectionPoolMonitorService.class))
                .thenReturn(httpConnectionPoolMonitorService);
        when(contextMock.beanForType(TrafficService.class)).thenReturn(trafficService);
        when(aolService.isAol()).thenReturn(false);
        when(contextMock.beanForType(AolService.class)).thenReturn(aolService);
        when(researchService.isRepoConfiguredToSyncProperties(any(RemoteRepoConfig.class))).thenReturn(true);
        when(contextMock.beanForType(ArtifactoryTracer.class)).thenReturn(new ArtifactoryTracer(
                ArtifactoryTracerConfiguration.testTracer()));
        when(contextMock.beanForType(UrlVerifier.class)).thenReturn(urlVerifier);
        when(smartRepoService.isSmartRepo(any())).thenReturn(false);
        when(contextMock.beanForType(SmartRepoService.class)).thenReturn(smartRepoService);
        when(contextMock.beanForType(UnifiedEventServiceImpl.class)).thenReturn(unifiedEventService);
        // Default stubbing for unified event service - all tests use shouldProceed(true)
        DispatcherResponse defaultDispatcherResponse = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder().headers(new HashMap<>()).build())
                .build();
        when(unifiedEventService.sendArtifactEvent(eq(SupportedUnifiedEvents.BEFORE_DOWNLOAD),
                any(ArtifactoryRequest.class), any(RepoPath.class))).thenReturn(defaultDispatcherResponse);
        ArtifactoryContextThreadBinder.bind(contextMock);
        descriptor = spy(new HttpRepoDescriptor());
        when(edgeSmartRepoAddon.updateUpstreamRepoKind(any())).thenReturn(UpstreamRepoKind.SMART);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testHttpRepoCtorWithRateLimiter() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo =
                new HttpRepo(descriptor, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);
        assertNotNull(httpRepo);
    }

    @Test
    void syncPropsDisabledWhenRepoConfigDisabled() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo =
                new HttpRepo(descriptor, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        when(researchService.isRepoConfiguredToSyncProperties(remoteRepoConfig)).thenReturn(false);
        assertThat(httpRepo.isSynchronizeProperties()).isFalse();
    }

    @Test
    void syncPropsEnabledForNonBlockedUrl() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Maven);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://13.11.201.9");
        when(researchService.isUpstreamUrlBlockedForPropertySync("http://13.11.201.9")).thenReturn(false);
        HttpRepo httpRepo = new HttpRepo(descriptor, remoteRepoConfig, internalRepoService,
                addonsManager, researchService, null, null, centralConfigService);
        assertThat(httpRepo.isSynchronizeProperties()).isTrue();
    }

    @Test
    void syncPropsDisabledForBlockedUrl() {
        RemoteRepoConfig remoteRepoConfig =
                RemoteRepoConfig.create("repoKey", RepoType.Generic);
        String url = DefaultUrl.MAVEN.getUrl();
        remoteRepoConfig.getRepoTypeConfig().setUrl(url);
        when(researchService
                .isUpstreamUrlBlockedForPropertySync(url))
                .thenReturn(true);
        HttpRepo httpRepo = new HttpRepo(descriptor, remoteRepoConfig, internalRepoService,
                addonsManager, researchService, null, null, centralConfigService);
        assertThat(httpRepo.isSynchronizeProperties()).isFalse();
    }

    @Test
    void testSuffixCreateHttpClient() throws IOException {
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("test-repo", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://test");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        HttpRepo httpRepo =
                new HttpRepo(new HttpRepoDescriptor(), repoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);

        try (CloseableHttpClient ignored = httpRepo.createHttpClient(
                ConstantValues.httpClientMaxTotalConnections.getInt(), true, true, "test-suffix")) {
            verify(mBeanRegistrationService, times(1))
                    .register(any(HTTPConnectionPool.class), eq("HTTPConnectionPool"), eq("test-repo-test-suffix"));
        }
    }

    @Test
    void testAddAdditionalAuthScopesWithBitbucket() {
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("test-repo", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("https://bitbucket.org");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        HttpRepo httpRepo =
                new HttpRepo(new HttpRepoDescriptor(), repoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        List<String> addAdditionalAuthScopes = httpRepo.addAdditionalAuthScopes();
        assertFalse(addAdditionalAuthScopes.isEmpty());
        assertEquals(BITBUCKET_API_HOST, addAdditionalAuthScopes.get(0));
    }

    @Test
    void testAddAdditionalAuthScopesWithInvalidUrl() {
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("test-repo", RepoType.Generic);

        repoConfig.getRepoTypeConfig().setUrl("invalid_url");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");

        HttpRepo httpRepo = new HttpRepo(new HttpRepoDescriptor(), repoConfig, internalRepoService, addonsManager,
                researchService, null, null, centralConfigService);

        assertThrows(IllegalArgumentException.class, httpRepo::addAdditionalAuthScopes);
    }

    @Test
    void testAddAdditionalAuthScopesWithGithub() {
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("test-repo", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("https://github.com");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        HttpRepo httpRepo =
                new HttpRepo(new HttpRepoDescriptor(), repoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        List<String> addAdditionalAuthScopes = httpRepo.addAdditionalAuthScopes();
        assertFalse(addAdditionalAuthScopes.isEmpty());
        assertTrue(addAdditionalAuthScopes.contains(GITHUB_RAW_HOST));
        assertTrue(addAdditionalAuthScopes.contains(GITHUB_API_HOST));
    }

    @Test
    void testAddAdditionalAuthScopesWithGithubGHE() {
        String GH_API_HOST = "api.sha2li.ghe.com";
        String GH_RAW_HOST = "raw.sha2li.ghe.com";
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("test-repo", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("https://sha2li.ghe.com");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        HttpRepo httpRepo =
                new HttpRepo(new HttpRepoDescriptor(), repoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        List<String> addAdditionalAuthScopes = httpRepo.addAdditionalAuthScopes();
        assertFalse(addAdditionalAuthScopes.isEmpty());
        assertTrue(addAdditionalAuthScopes.contains(GH_RAW_HOST));
        assertTrue(addAdditionalAuthScopes.contains(GH_API_HOST));
    }

    @Test
    void testAddAdditionalAuthScopesWithGitLab() {
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("test-repo", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("https://gitlab.com");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        HttpRepo httpRepo =
                new HttpRepo(new HttpRepoDescriptor(), repoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        List<String> addAdditionalAuthScopes = httpRepo.addAdditionalAuthScopes();
        assertFalse(addAdditionalAuthScopes.isEmpty());
        assertEquals(GITLAB_API_HOST, addAdditionalAuthScopes.get(0));
    }

    @Test
    void testProxyRemoteAuthAndMultihome() throws IOException {
        String proxyKey = "proxy123";
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey(proxyKey);
        proxyDescriptor.setHost("proxyHost");
        proxyDescriptor.setPort(12345);
        proxyDescriptor.setUsername("proxy-username");
        proxyDescriptor.setPassword("proxy-password");

        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = spy(new RemoteRepositoryRepoTypeConfig());
        ReflectionTestUtils.setField(repoConfig, "repoTypeConfig", remoteRepoConfig);
        repoConfig.getRepoTypeConfig().setUrl("http://test");
        repoConfig.getRepoTypeConfig().setProxyRef(proxyKey);
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        repoConfig.getRepoTypeConfig().setLocalAddress("0.0.0.0");

        CentralConfigDescriptorImpl configDescriptor = spy(new CentralConfigDescriptorImpl());
        configDescriptor.setProxies(List.of(proxyDescriptor));
        when(this.centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        HttpRepoDescriptor repoDescriptor = spy(new HttpRepoDescriptor());
        HttpRepo httpRepo =
                new HttpRepo(repoDescriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, null, this.centralConfigService);
        CloseableHttpClient client =
                httpRepo.createHttpClient(ConstantValues.httpClientMaxTotalConnections.getInt(), true, true, null);
        if (client != null) {
            client.close();
        }
        verify(remoteRepoConfig, atLeast(1)).getUrl();
        verify(repoDescriptor, times(1)).getProxy();
        verify(remoteRepoConfig, times(2)).getUsername();
        verify(remoteRepoConfig, times(1)).getPassword();
        verify(remoteRepoConfig, times(1)).getLocalAddress();
    }

    @Test
    void shouldProcessWithoutExceptions() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://THIS_IS_A_DUMMY_URL/BLA/BLA/BLA");
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager, researchService, null,
                        null, centralConfigService);
        assertFalse(httpRepo.isNeedToNormalize());
    }

    @Test
    void doExecuteMethod_shouldLogOnConnectTimeoutException() throws IOException {
        // Arrange
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo =
                spy(new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager, researchService, null,
                        null, centralConfigService));
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        doReturn(client).when(httpRepo).getHttpClient();
        doReturn(response).when(httpRepo).interceptResponse(any());
        // Act & Assert
        HttpRequestBase httpRequestBase = mock(HttpRequestBase.class);
        when(httpRequestBase.getURI()).thenReturn(URI.create("http://google.com"));
        HttpContext httpContext = mock(HttpContext.class);
        doThrow(new ConnectTimeoutException("Timeout")).when(client)
                .execute(httpRequestBase, httpContext);
        doNothing().when(httpRepo).putOffline();
        when(contextMock.beanForType(MeterRegistry.class)).thenReturn(new SimpleMeterRegistry());
        try (MockedStatic<OutboundRequestLogger> mockedLogger = mockStatic(OutboundRequestLogger.class)) {
            Assertions.assertThrows(ConnectTimeoutException.class, () -> {
                httpRepo.executeMethod(httpRequestBase, httpContext, null);
            });
            mockedLogger.verify(() ->
                    OutboundRequestLogger.logFailingRequestWithoutResponse(anyString(), isNull(),
                            any(HttpRequestBase.class), anyLong(), anyBoolean(), any(Exception.class)), times(1));
        }
    }

    @Test
    void shouldNormalizeUrl() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://google.com");
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        assertTrue(httpRepo.isNeedToNormalize());
    }

    @Test
    void shouldNotNormalizeUrl() {
        String[] hostsArray = ConstantValues.listOfReposHostsNeedToAvoidUrlNormalisation.getString().trim().split(",");
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        Arrays.stream(hostsArray).forEach(url -> {
            remoteRepoConfig.getRepoTypeConfig().setUrl("http://" + url.trim() + "/somepackage");
            HttpRepo httpRepo =
                    new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                            null, null, centralConfigService);
            assertFalse(httpRepo.isNeedToNormalize());
        });
    }

    @Test
    void shouldHandleUrlNormalizationViaDescriptor() {
        // Check when disableUrlNormalization == true
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://someUrl/somePackage");
        remoteRepoConfig.getRepoTypeConfig().setDisableUrlNormalization(true);
        HttpRepo httpRepo =
                new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                        null, null, centralConfigService);
        assertFalse(httpRepo.isNeedToNormalize());
        // Check when disableUrlNormalization == false
        remoteRepoConfig.getRepoTypeConfig().setDisableUrlNormalization(false);
        httpRepo = new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager,
                researchService, null, null, centralConfigService);
        assertTrue(httpRepo.isNeedToNormalize());
        // Check when wrong URL
        remoteRepoConfig.getRepoTypeConfig().setUrl("http:\\someUrl/somePackage");
        remoteRepoConfig.getRepoTypeConfig().setDisableUrlNormalization(true);
        httpRepo = new HttpRepo(null, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                null, null, centralConfigService);
        assertTrue(httpRepo.isNeedToNormalize());
    }

    @Test
    void testPluginInvocationOnDownloadResource() throws IOException {
        // setup
        RemoteRepoConfig remoteRepoConfig = getRemoteRepoConfig();

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(httpEntity.getContent()).thenReturn(mock(InputStream.class));
        CloseableHttpResponse mockedResponse = mock(CloseableHttpResponse.class);
        when(mockedResponse.getStatusLine())
                .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK"));
        when(mockedResponse.getEntity()).thenReturn(httpEntity);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class), (HttpContext) isNull())).thenReturn(mockedResponse);
        HttpRepo httpRepo =
                spy(new HttpRepo(descriptor, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService));
        when(onlineMonitorService.isResourceUnavailable(any(StatusLine.class))).thenReturn(false);
        doReturn(client).when(httpRepo).getHttpClient();
        httpRepo.init(false);

        ArgumentCaptor<Class<? extends PluginAction>> argCaptor = ArgumentCaptor.forClass(Class.class);

        // test
        httpRepo.downloadResource("path/file.txt");

        // verify
        verify(pluginsAddon, times(1)).execPluginActions(argCaptor.capture(), any(), any(Object[].class));

        List<Class<? extends PluginAction>> allClassesUsed = argCaptor.getAllValues();
        assertEquals(1, allClassesUsed.size());
        assertTrue(allClassesUsed.contains(BeforeRemoteDownloadAction.class));
    }

    @Nonnull
    private RemoteRepoConfig getRemoteRepoConfig() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        remoteRepoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        remoteRepoConfig.getRepoTypeConfig().setUrl("http://localhost:8082/artifactory");
        when(layoutsCoreAddon.translateArtifactPath(any(), any(), any())).thenReturn("path/file.txt");
        when(fileService.exists(any())).thenReturn(true);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setRepoLayouts(List.of(RepoLayoutUtils.MAVEN_2_DEFAULT));
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        return remoteRepoConfig;
    }

    @Test
    void testPluginInvocationOnRetrieveInfo() throws IOException {
        // setup
        RemoteRepoConfig remoteRepoConfig = getRemoteRepoConfig();

        HttpEntity httpEntity = mock(HttpEntity.class);
        when(httpEntity.getContent()).thenReturn(mock(InputStream.class));
        CloseableHttpResponse mockedResponse = mock(CloseableHttpResponse.class);
        when(mockedResponse.getStatusLine())
                .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK"));
        when(mockedResponse.getEntity()).thenReturn(httpEntity);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpHead.class), any(HttpContext.class))).thenReturn(mockedResponse);

        RequestContext requestContext = mock(RequestContext.class);
        ArtifactoryRequest request = mock(ArtifactoryRequest.class);
        when(requestContext.getRequest()).thenReturn(request);
        when(requestContext.alterPath(any(), any())).thenCallRealMethod();

        HttpRepo httpRepo =
                spy(new HttpRepo(descriptor, remoteRepoConfig, internalRepoService, addonsManager,
                        researchService, null, onlineMonitorService, centralConfigService));
        doReturn(client).when(httpRepo).getHttpClient();
        httpRepo.init(false);

        ArgumentCaptor<Class<? extends PluginAction>> argCaptor = ArgumentCaptor.forClass(Class.class);

        // test
        httpRepo.retrieveInfo("path/file.txt", false, requestContext);

        // verify
        verify(pluginsAddon).execPluginActions(argCaptor.capture(), any(), any(Object[].class));
        verify(httpRepo).whiteListHeaders(requestContext);

        List<Class<? extends PluginAction>> allClassesUsed = argCaptor.getAllValues();
        assertEquals(1, allClassesUsed.size());
        assertTrue(allClassesUsed.contains(BeforeRemoteInfoRequestAction.class));
    }

    @Test
    void testPlusSymbolEscape() {
        String actual = HttpRepo.encodeIfNeeded("http://localhost:8081/bellsoft-jre8u252+9-linux-amd64.tar.gz",
                null);
        assertEquals("http://localhost:8081/bellsoft-jre8u252%2B9-linux-amd64.tar.gz", actual);
    }

    @Test
    void testWhiteListHeadersWithNullContext() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo =
                new HttpRepo(descriptor, remoteRepoConfig, internalRepoService, addonsManager, researchService,
                        null, null, null);
        HeadersMultiMap headersMultiMap = httpRepo.whiteListHeaders(null);
        assertNotNull(headersMultiMap);
    }

    @ParameterizedTest
    @CsvSource({
            "true, false",
            "false, false",
            "false, true"
    })
    void testCurationReplaceHead(boolean shouldSendContext, boolean passThrough) throws IOException {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(shouldSendContext);
        repoConfig.getRepoTypeConfig().setPassThrough(passThrough);
        when(layoutsCoreAddon.translateArtifactPath(any(), any(), any())).thenReturn("path/file.txt");
        when(fileService.exists(any())).thenReturn(true);

        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", List.of());
        CloseableHttpClient client = prepareHttpClient(shouldSendContext, passThrough, httpRepo, BlockStatus.UNBLOCKED);

        DownloadRequestContext requestContext = new DownloadRequestContext(mock(ArtifactoryRequest.class));
        RepoResource repoResource = httpRepo.retrieveInfo("path", false, requestContext);

        assertInstanceOf(RemoteRepoResource.class, repoResource);
        assertFalse(repoResource.isExpirable());
        assertTrue(repoResource.isFound());
        assertEquals(RepoPathFactory.create("repoKey", "path"), repoResource.getRepoPath());
        if (shouldSendContext || passThrough) {
            verify(client, times(1)).execute(any(HttpGet.class), any(HttpContext.class));
        } else {
            verify(client, times(1)).execute(any(HttpHead.class), any(HttpContext.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCurationReturnForbiddenIfNeeded(boolean curated) throws IOException {
        when(layoutsCoreAddon.translateArtifactPath(any(), any(), any())).thenReturn("path/file.txt");
        when(fileService.exists(any())).thenReturn(true);

        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();

        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", List.of());
        DownloadRequestContext requestContext = new DownloadRequestContext(mock(ArtifactoryRequest.class));
        BlockStatus blockStatus = curated ? BlockStatus.CURATED : BlockStatus.FORBIDDEN;
        CloseableHttpClient client = prepareHttpClient(false, false, httpRepo, blockStatus);

        RepoResource repoResource = httpRepo.retrieveInfo("path", false, requestContext);
        if (blockStatus.isCurated()) {
            assertInstanceOf(BlockedRepoResource.class, repoResource);
            assertEquals("curated", ((BlockedRepoResource) repoResource).getMessage());
            assertEquals(BlockedRepoResource.BlockedType.CURATION,
                    (((BlockedRepoResource) repoResource).getBlockedType()));
        } else {
            assertInstanceOf(UnfoundRepoResource.class, repoResource);
        }
        verify(client, times(1)).execute(any(HttpHead.class), any(HttpContext.class));
    }

    @Test
    void redirectStrategyNotAolWithIgnoreRedirectStrategySetToTrue() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        try (MockedStatic<AolUtils> mocked = mockStatic(AolUtils.class)) {
            mocked.when(AolUtils::isAol).thenReturn(false);
            this.setStringSystemProperty(ignoreRedirectStrategy, String.valueOf(true));
            HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
            assertInstanceOf(DefaultRedirectStrategy.class, baseRedirectStrategy(httpRepo));
        }
    }

    /**
     * Unwraps UrlVerifierRedirectStrategy decorator (applied by default in shadow mode)
     * to assert on the underlying base strategy selected by {@code getRedirectStrategy()}.
     */
    private static RedirectStrategy baseRedirectStrategy(HttpRepo httpRepo) {
        RedirectStrategy strategy = httpRepo.getRedirectStrategy();
        return strategy instanceof UrlVerifierRedirectStrategy wrapper ? wrapper.getDelegate() : strategy;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shallowBlockForRedirectStrategy(boolean shallowBlockValue) {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        try (MockedStatic<AolUtils> mocked = mockStatic(AolUtils.class)) {
            mocked.when(AolUtils::isAol).thenReturn(false);
            this.setStringSystemProperty(ignoreRedirectStrategy, String.valueOf(false));
            ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
            ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
            when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
            when(artifactorySystemProperties.getLongProperty(
                    ConstantValues.httpUpstreamRepositoryStatusExpirationSeconds)).thenReturn(5l);
            when(artifactorySystemProperties.getProperty(
                    ConstantValues.listOfReposHostsNeedToAvoidUrlNormalisation)).thenReturn("hello");
            SysConfig sysConfig = mock(SysConfig.class);
            when(sysConfig.getBoolean(eq("artifactory.http.remote.client.redirect.shallow.block"), anyBoolean()))
                    .thenReturn(shallowBlockValue);
            when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
            ArtifactoryHome.bind(artifactoryHome);
            HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
            assertInstanceOf(JFrogDefaultRedirectStrategy.class, httpRepo.getRedirectStrategy());
            JFrogDefaultRedirectStrategy strategy = (JFrogDefaultRedirectStrategy) httpRepo.getRedirectStrategy();
            try {
                boolean isShallowBlock = isShallowBlock(strategy);
                if (shallowBlockValue) {
                    assertTrue(isShallowBlock, "Expected denylistValidator.isShallowBlock to be true");
                } else {
                    assertFalse(isShallowBlock, "Expected denylistValidator.isShallowBlock to be false");
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fail("Failed to access denylistValidator field: " + e.getMessage());
            }
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    private boolean isShallowBlock(JFrogDefaultRedirectStrategy strategy)
            throws NoSuchFieldException, IllegalAccessException {
        Field denylistValidatorField = JFrogDefaultRedirectStrategy.class.getDeclaredField("denylistValidator");
        denylistValidatorField.setAccessible(true);
        DenylistValidator validator = (DenylistValidator) denylistValidatorField.get(strategy);
        Field isShallowBlockField = DenylistValidator.class.getDeclaredField("isShallowBlock");
        isShallowBlockField.setAccessible(true);
        return (boolean) (Boolean) isShallowBlockField.get(validator);
    }

    @Test
    void redirectStrategyNotAolWithIgnoreRedirectStrategySetToFalse() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        try (MockedStatic<AolUtils> mocked = mockStatic(AolUtils.class)) {
            mocked.when(AolUtils::isAol).thenReturn(false);
            this.setStringSystemProperty(ignoreRedirectStrategy, String.valueOf(false));
            HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
            assertInstanceOf(JFrogDefaultRedirectStrategy.class, baseRedirectStrategy(httpRepo));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void redirectStrategyAolWithIgnoreRedirectStrategy(boolean isIgnoreRedirectStrategy) {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        try (MockedStatic<AolUtils> mocked = mockStatic(AolUtils.class)) {
            mocked.when(AolUtils::isAol).thenReturn(true);
            this.setStringSystemProperty(ignoreRedirectStrategy, String.valueOf(isIgnoreRedirectStrategy));
            HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
            assertInstanceOf(JFrogDefaultRedirectStrategy.class, baseRedirectStrategy(httpRepo));
        }
    }

    @Test
    void redirectStrategyAolWithDefaultIgnoreRedirectStrategy() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        try (MockedStatic<AolUtils> mocked = mockStatic(AolUtils.class)) {
            mocked.when(AolUtils::isAol).thenReturn(false);
            HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
            assertInstanceOf(JFrogDefaultRedirectStrategy.class, baseRedirectStrategy(httpRepo));
        }
    }

    @ParameterizedTest
    @MethodSource("queryParamsProvider")
    void testAddQueryParamsWithMultiValParam(Map<String, String[]> queryParams, String expectedQueryParams) {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        String url = "http://localhost:8081/repoKey/path/file.txt";
        RequestContext requestContext = createRequestContext(queryParams);

        String actual = httpRepo.addQueryParams(url, requestContext);

        assertEquals(url + expectedQueryParams, actual);
    }

    private static Stream<Arguments> queryParamsProvider() {
        return Stream.of(
                Arguments.of(
                        new TreeMap<>(Map.of("param1", new String[] {"value1"})),
                        "?param1=value1"
                ),
                Arguments.of(
                        new TreeMap<>(Map.of("param1", new String[] {"value1", "value2"})),
                        "?param1=value1&param1=value2"
                ),
                Arguments.of(
                        new TreeMap<>(Map.of(
                                "param1", new String[] {"value1"},
                                "param2", new String[] {"value2"}
                        )),
                        "?param1=value1&param2=value2"
                ),
                Arguments.of(
                        new TreeMap<>(Map.of(
                                "param1", new String[] {"value1", "value2"},
                                "param2", new String[] {"value3", "value4"}
                        )),
                        "?param1=value1&param1=value2&param2=value3&param2=value4"
                ),
                Arguments.of(
                        new TreeMap<>(Map.of("param1", new String[] {""})),
                        "?param1="
                ),
                Arguments.of(
                        new TreeMap<>(Map.of()),
                        ""
                )
        );
    }

    @Test
    void testAddQueryParamsWithPropagateQueryParams() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        String url = "http://localhost:8081/artifactory/repoKey/path/file.txt";

        RequestContext requestContext = createRequestContext(Map.of("param1", new String[] {"value1"}));
        //regular case
        String actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals("http://localhost:8081/artifactory/repoKey/path/file.txt?param1=value1",
                actual);

        //regular case with multiple params
        requestContext = createRequestContext(Map.of("param1", new String[] {"value1"}, "param2",
                new String[] {"value2"}));
        actual = httpRepo.addQueryParams(url, requestContext);
        assertTrue(actual.contains("?") && actual.contains("param1=value1") &&
                actual.contains("param2=value2"));

        //case when there are no query params
        requestContext = createRequestContext(Map.of());
        actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals(url, actual);

        //case when there is a single param without a value, and it's "trace"
        requestContext = createRequestContext(Map.of("trace", new String[] {""}));
        actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals(url, actual);

        //case when query param "trace" has a value
        requestContext = createRequestContext(Map.of("trace", new String[] {"value"}));
        actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals(url, actual);

        //case when there are multiple params, one of them is "trace" without a value
        requestContext = createRequestContext(Map.of("param1", new String[] {"value1"},
                "trace", new String[] {""}));
        actual = httpRepo.addQueryParams(url, requestContext);
        assertTrue(actual.contains("?") && actual.contains("param1=value1") &&
                !actual.contains("trace"));

        //case when there are multiple params, one of them is "trace" with a value
        requestContext = createRequestContext(Map.of("param1", new String[] {"value1"},
                "trace", new String[] {"value"}));
        actual = httpRepo.addQueryParams(url, requestContext);
        assertTrue(actual.contains("?") && actual.contains("param1=value1") &&
                !actual.contains("trace=value"));

        // Bridge :properties URL already has ?propertiesXml — propagated params must join with &, not ?…?
        String bridgePropertiesUrl =
                "https://host/bridge-tenant/artifactory/api/storage/libs-release/a/b.jar?propertiesXml";
        requestContext = createRequestContext(Map.of("x", new String[] {"y"}));
        actual = httpRepo.addQueryParams(bridgePropertiesUrl, requestContext);
        assertEquals(bridgePropertiesUrl + "&x=y", actual);
    }

    @Test
    void testAdjustPropagatedQueryStringForBridgeFullUrl() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        String bridgeUrl = "https://host/bridge-tenant/artifactory/api/storage/repo/a.jar?propertiesXml";

        //bridge URL with existing query → ? replaced by &
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(bridgeUrl, "?x=y"), "&x=y");

        //bridge URL with existing query and multiple params
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(bridgeUrl, "?a=1&b=2"), "&a=1&b=2");

        //bridge URL without existing query → unchanged
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(
                "https://host/bridge-tenant/artifactory/repo/path/", "?x=y"), "?x=y");

        //non-bridge URL with existing query → unchanged
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(
                "https://remote-host/artifactory/repo/a.jar?param=val", "?x=y"), "?x=y");

        //non-bridge URL without query → unchanged
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(
                "https://remote-host/artifactory/repo/path", "?x=y"), "?x=y");

        //bridge URL with query but params already start with & → unchanged
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(
                bridgeUrl, "&already=ampersand"), "&already=ampersand");

        //bridge URL with query and empty params string → unchanged
        assertEquals(httpRepo.adjustPropagatedQueryStringForBridgeFullUrl(bridgeUrl, ""), "");
    }

    @Test
    void testAddQueryParamsWithoutPropagateQueryParams() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(false);
        String url = "http://localhost:8081/artifactory/repoKey/path/file.txt";

        RequestContext requestContext = createRequestContext(Map.of("param1", new String[] {"value1"}));
        //regular case
        String actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals(url, actual);

        //case when there are no query params
        requestContext = createRequestContext(Map.of());
        actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals(url, actual);

        //case when there is a single param without a value, and it's "trace"
        requestContext = createRequestContext(Map.of("trace", new String[] {""}));
        actual = httpRepo.addQueryParams(url, requestContext);
        assertEquals(url, actual);
    }

    @Test
    void headersMultiMapFrom_collectsApacheHeadersForMultipartClient() {
        HttpGet get = new HttpGet("http://example.com/artifact.jar");
        get.addHeader("X-Parallel", "1");
        get.addHeader(HttpHeaders.CONNECTION, "Keep-Alive");

        List<Map.Entry<String, String>> entries = new ArrayList<>();
        HttpRepo.headersMultiMapFrom(get).entries().forEach(entries::add);

        assertTrue(entries.stream().anyMatch(e ->
                HttpHeaders.CONNECTION.equalsIgnoreCase(e.getKey()) && "Keep-Alive".equals(e.getValue())));
        assertTrue(entries.stream().anyMatch(e ->
                "X-Parallel".equals(e.getKey()) && "1".equals(e.getValue())));
    }

    @Test
    void headersMultiMapFrom_returnsEmptyWhenRequestHasNoHeaders() {
        HttpGet get = new HttpGet("http://example.com/a");

        List<Map.Entry<String, String>> entries = new ArrayList<>();
        HttpRepo.headersMultiMapFrom(get).entries().forEach(entries::add);

        assertTrue(entries.isEmpty());
    }

    @Test
    void preExecuteSteps_appendsRepoQueryParamsToRangeRequestUri() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(false);
        repoConfig.getRepoTypeConfig().setQueryParams("token=abc123&v=1");
        when(haCommonAddon.getHostId()).thenReturn("test-host");
        HttpRepo repo = createHttpRepo(repoConfig, descriptor);

        HttpGet get = new HttpGet("http://remote/path/file.jar");
        ReflectionTestUtils.invokeMethod(repo, "preExecuteSteps", get, new HeadersMultiMap());

        String query = get.getURI().getQuery();
        assertNotNull(query, "Range request URI must carry query params");
        assertTrue(query.contains("token=abc123"), "Expected token in query: " + query);
        assertTrue(query.contains("v=1"), "Expected v=1 in query: " + query);
    }

    @Test
    void preExecuteSteps_noQueryStringWhenRepoHasNoneConfigured() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(false);
        when(haCommonAddon.getHostId()).thenReturn("test-host");
        HttpRepo repo = createHttpRepo(repoConfig, descriptor);

        HttpGet get = new HttpGet("http://remote/path/file.jar");
        ReflectionTestUtils.invokeMethod(repo, "preExecuteSteps", get, new HeadersMultiMap());

        assertNull(get.getURI().getQuery(), "Expected no query string when repo has no query params configured");
    }

    @Test
    void addSrcTenantIdInterceptorIfSmartRepo_attaches_whenSmartRepo() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(false);
        when(smartRepoService.isSmartRepo(any())).thenReturn(true);
        HttpRepo repo = createHttpRepo(repoConfig, descriptor);
        HttpClientConfigurator configurator = mock(HttpClientConfigurator.class);

        repo.addSrcTenantIdInterceptorIfSmartRepo(configurator);

        verify(configurator).addRequestInterceptor(any(SrcTenantIdHeaderInterceptor.class));
    }

    @Test
    void addSrcTenantIdInterceptorIfSmartRepo_doesNotAttach_whenNotSmartRepo() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(false);
        when(smartRepoService.isSmartRepo(any())).thenReturn(false);
        HttpRepo repo = createHttpRepo(repoConfig, descriptor);
        HttpClientConfigurator configurator = mock(HttpClientConfigurator.class);

        repo.addSrcTenantIdInterceptorIfSmartRepo(configurator);

        verify(configurator, never()).addRequestInterceptor(any());
    }

    private RequestContext createRequestContext(Map<String, String[]> params) {
        RequestContext requestContext = mock(RequestContext.class);
        Request request = mock(Request.class);
        when(request.getParameters()).thenReturn(params);
        when(requestContext.getRequest()).thenReturn(request);
        return requestContext;
    }

    private HttpRepo prepareHttpRepoWithPropagateQueryParams(boolean withPropogate) {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        REPO_CONFIG.getRepoTypeConfig().setPropagateQueryParams(withPropogate);
        return createHttpRepo(REPO_CONFIG, repoDescriptor);
    }

    private static RemoteRepoConfig prepareRemoteRepoConfig(boolean shouldSendContent) {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = spy(new RemoteRepositoryRepoTypeConfig());
        remoteRepoConfig.setStoreArtifactsLocally(false);
        remoteRepoConfig.setSendContext(shouldSendContent);
        repoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        ReflectionTestUtils.setField(repoConfig, "repoTypeConfig", remoteRepoConfig);
        repoConfig.getRepoTypeConfig().setUrl("http://test");
        repoConfig.getRepoTypeConfig().setUsername("repo-username");
        repoConfig.getRepoTypeConfig().setPassword("repo-password");
        return repoConfig;
    }

    private HttpRepo createHttpRepo(RemoteRepoConfig repoConfig, HttpRepoDescriptor repoDescriptor) {
        return new HttpRepo(repoDescriptor, repoConfig, internalRepoService, addonsManager, researchService,
                null, onlineMonitorService, this.centralConfigService);
    }


    private HttpRepoDescriptor prepareCentralConfigService() {
        HttpRepoDescriptor repoDescriptor = new HttpRepoDescriptor();
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        return repoDescriptor;
    }

    private static CloseableHttpClient prepareHttpClient(boolean shouldSendContext, boolean passThrough,
            HttpRepo httpRepo, BlockStatus blockStatus) throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mockResponse(blockStatus);
        if (shouldSendContext || passThrough) {
            when(client.execute(any(HttpGet.class), any(HttpContext.class))).thenReturn(response);
        } else {
            when(client.execute(any(HttpHead.class), any(HttpContext.class))).thenReturn(response);
        }
        ReflectionTestUtils.setField(httpRepo, "client", client);
        return client;
    }

    private static CloseableHttpResponse mockResponse(BlockStatus blockStatus) {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.containsHeader(ARTIFACTORY_ID)).thenReturn(true);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(blockStatus.getStatus());
        if (blockStatus.isBlocked()) {
            mockResponseContent(blockStatus, response);
        }
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

    private static void mockResponseContent(BlockStatus blockStatus, CloseableHttpResponse response) {
        ByteArrayInputStream inStream;
        if (blockStatus == BlockStatus.CURATED) {
            inStream = new ByteArrayInputStream("{\"errors\":[{\"message\":\"curated\"}]}".getBytes());
        } else {
            inStream = new ByteArrayInputStream("FORBIDDEN".getBytes());
        }
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContent(inStream);
        when(response.getEntity()).thenReturn(entity);
    }

    private enum BlockStatus {
        FORBIDDEN, CURATED, UNBLOCKED;

        int getStatus() {
            return isBlocked() ? HttpStatus.SC_FORBIDDEN : HttpStatus.SC_OK;
        }

        boolean isBlocked() {
            return this != UNBLOCKED;
        }

        boolean isCurated() {
            return this == CURATED;
        }
    }

    @Test
    void testXrayReturnForbidden() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("repoKey", "path/artifact.txt");
        HttpRequestBase method = mock(HttpRequestBase.class);
        String downloadUrl = "";
        CloseableHttpResponse response = mock(CloseableHttpResponse.class, RETURNS_DEEP_STUBS);
        when(response.getStatusLine().getStatusCode()).thenReturn(HttpStatus.SC_FORBIDDEN);
        when(response.containsHeader(XRAY_ORIGIN)).thenReturn(true);

        try {
            ArtifactoryHome.get().getArtifactoryProperties().setProperty(xrayRemoteValidate.getPropertyName(), "true");
            RepoResource resource = httpRepo.handleGetInfoResponse(repoPath, method, downloadUrl, response, null, null);
            assertNotNull(resource);
            BlockedRepoResource blockedResource = ((BlockedRepoResource) resource);
            assertEquals("Blocked by Xray", blockedResource.getMessage());
            assertEquals("repoKey:path/artifact.txt", blockedResource.getRepoPath().toString());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties().setProperty(xrayRemoteValidate.getPropertyName(), "false");
        }
    }

    @Test
    void handleGetInfoResponseBlockedByCurationTest() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("repoKey", "path/artifact.txt");
        HttpRequestBase method = mock(HttpRequestBase.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(SC_OK);
        when(response.getStatusLine()).thenReturn(statusLine);
        RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.getRequest()).thenReturn(mock(Request.class));
        when(curationAddon.getPackageStatus(any(), any(), any(), any(), anyBoolean())).thenReturn(
                new CurationCachedPackageStatus(null, BLOCKED_STATUS));
        RepoResource resource = httpRepo.handleGetInfoResponse(repoPath, method, "", response, null, requestContext);
        assertInstanceOf(BlockedRepoResource.class, resource);
    }

    @Test
    void getHttpRequestInterceptorDefaultBehaviourTest() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        assertNotNull(httpRepo.getHttpRequestInterceptor());
    }

    @Test
    void testRetrieveInfo() throws IOException {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        when(layoutsCoreAddon.translateArtifactPath(any(), any(), any())).thenReturn("path/file.txt");
        when(fileService.exists(any())).thenReturn(true);

        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        ReflectionTestUtils.setField(httpRepo, "remoteRepoInterceptors", List.of());

        DownloadRequestContext requestContext = new DownloadRequestContext(mock(ArtifactoryRequest.class));
        requestContext.setAttribute(ArtifactoryHeaders.PARAM_REPLACE_HEAD_IN_RETRIEVE_INFO_WITH_GET, "true");

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(response.getStatusLine()).thenReturn(statusLine);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class), any(HttpContext.class))).thenReturn(response);
        ReflectionTestUtils.setField(httpRepo, "client", client);

        RepoResource repoResource = httpRepo.retrieveInfo("path", false, requestContext);

        assertTrue(repoResource.isFound());
        assertEquals(RepoPathFactory.create("repoKey", "path"), repoResource.getRepoPath());

        verify(response, times(1)).getEntity();
        verify(response, times(1)).close();
    }

    @ParameterizedTest
    @MethodSource("shouldReturnUnfoundResourceDataProviderWithRange")
    void shouldReturnUnfoundResourceTestRangeFlagEnabled(int status, boolean returned) {
        setStringSystemProperty(ConstantValues.useRangeForReplacedGet, "true");
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        CloseableHttpResponse mockedResponse = mock(CloseableHttpResponse.class);
        when(mockedResponse.getStatusLine())
                .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), status, "resp"));

        boolean actual = httpRepo.shouldReturnUnfoundResource(null, mockedResponse, null, null, status);
        assertEquals(returned, actual);
    }

    @ParameterizedTest
    @MethodSource("shouldReturnUnfoundResourceDataProvider")
    void shouldReturnUnfoundResourceTest(int status, boolean returned) {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        CloseableHttpResponse mockedResponse = mock(CloseableHttpResponse.class);
        when(mockedResponse.getStatusLine())
                .thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), status, "resp"));

        boolean actual = httpRepo.shouldReturnUnfoundResource(null, mockedResponse, null, null, status);
        assertEquals(returned, actual);
    }

    @Test
    void shouldSetReasonPhraseAndReturnTrue_whenRedirectToFolder() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("test-repo", "path/to/artifact");

        CloseableHttpResponse response = new ResponseForTest(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_MOVED_TEMPORARILY, "Found"));
        assertFalse(response.containsHeader("Content-Disposition"),
                "Folder-redirect baseline must not carry Content-Disposition");
        HttpClientContext context = mock(HttpClientContext.class);
        when(context.getRedirectLocations())
                .thenReturn(Collections.singletonList(URI.create("http://remote.example.com/repo/path/")));

        boolean actual = httpRepo.shouldReturnUnfoundResource(repoPath, response, context, null, 302);

        assertTrue(actual);
        assertEquals("The path should be a directory not a file", response.getStatusLine().getReasonPhrase());
        UnfoundRepoResource unfound = httpRepo.returnUnfoundResource(repoPath, response);
        assertEquals("The path should be a directory not a file", unfound.getDetail());
    }

    @Test
    void shouldReturnFalse_whenRedirectToFolderWithContentDispositionAttachment() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("test-repo", "path/to/artifact");

        ResponseForTest response = new ResponseForTest(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK"));
        response.setHeader(new BasicHeader("Content-Disposition", "attachment; filename=\"audit.zip\""));
        HttpClientContext context = mock(HttpClientContext.class);
        when(context.getRedirectLocations())
                .thenReturn(Collections.singletonList(URI.create("http://remote.example.com/repo/path/")));

        boolean actual = httpRepo.shouldReturnUnfoundResource(repoPath, response, context, null, 200);

        assertFalse(actual, "Content-Disposition: attachment must bypass the folder-redirect heuristic");
    }

    @Test
    void shouldReturnFalse_whenRedirectToFolderWithUppercaseAttachment() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("test-repo", "path/to/artifact");

        ResponseForTest response = new ResponseForTest(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_OK, "OK"));
        response.setHeader(new BasicHeader("Content-Disposition", "ATTACHMENT; filename=\"audit.zip\""));
        HttpClientContext context = mock(HttpClientContext.class);
        when(context.getRedirectLocations())
                .thenReturn(Collections.singletonList(URI.create("http://remote.example.com/repo/path/")));

        boolean actual = httpRepo.shouldReturnUnfoundResource(repoPath, response, context, null, 200);

        assertFalse(actual,
                "RFC 6266 disposition tokens are case-insensitive; ATTACHMENT must bypass the heuristic too");
    }

    @Test
    void shouldReturnTrue_whenRedirectToFolderWithInlineContentDisposition() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("test-repo", "path/to/artifact");

        ResponseForTest response = new ResponseForTest(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_MOVED_TEMPORARILY, "Found"));
        response.setHeader(new BasicHeader("Content-Disposition", "inline"));
        HttpClientContext context = mock(HttpClientContext.class);
        when(context.getRedirectLocations())
                .thenReturn(Collections.singletonList(URI.create("http://remote.example.com/repo/path/")));

        boolean actual = httpRepo.shouldReturnUnfoundResource(repoPath, response, context, null, 302);

        assertTrue(actual, "Content-Disposition: inline must NOT bypass the folder-redirect heuristic");
    }

    @Test
    void shouldReturnTrue_whenRedirectToFolderWithMalformedContentDispositionNullValue() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        RepoPath repoPath = InternalRepoPathFactory.create("test-repo", "path/to/artifact");

        Header malformedHeader = mock(Header.class);
        when(malformedHeader.getName()).thenReturn("Content-Disposition");
        when(malformedHeader.getValue()).thenReturn(null);
        ResponseForTest response = new ResponseForTest(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), HttpStatus.SC_MOVED_TEMPORARILY, "Found"));
        response.setHeader(malformedHeader);
        HttpClientContext context = mock(HttpClientContext.class);
        when(context.getRedirectLocations())
                .thenReturn(Collections.singletonList(URI.create("http://remote.example.com/repo/path/")));

        boolean actual = httpRepo.shouldReturnUnfoundResource(repoPath, response, context, null, 302);

        assertTrue(actual,
                "A Content-Disposition header with a null value must defensively not bypass the folder heuristic");
    }

    @ParameterizedTest
    @MethodSource("rangeIsNotSupportedDataProvider")
    void testShouldLogRangeNotSupported(int status, boolean returned) {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(status);
        when(response.getStatusLine()).thenReturn(statusLine);

        boolean actual = httpRepo.shouldLogRangeNotSupported(response);
        Assert.assertEquals(actual, returned);
    }

    @Test
    void testShouldLogRangeNotSupportedNull() {
        RemoteRepoConfig repoConfig = prepareRemoteRepoConfig(true);
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(repoConfig, repoDescriptor);
        boolean actual = httpRepo.shouldLogRangeNotSupported(null);
        Assert.assertFalse(actual);
    }

    static Stream<Arguments> rangeIsNotSupportedDataProvider() {
        return Stream.of(
                Arguments.of(200, true),
                Arguments.of(204, true),
                Arguments.of(206, false),
                Arguments.of(416, false),
                Arguments.of(404, false),
                Arguments.of(404, false),
                Arguments.of(500, false)
        );
    }

    static Stream<Arguments> shouldReturnUnfoundResourceDataProvider() {
        return Stream.of(
                Arguments.of(200, false),
                Arguments.of(204, false),
                Arguments.of(404, true),
                Arguments.of(500, true),
                Arguments.of(416, true)
        );
    }

    static Stream<Arguments> shouldReturnUnfoundResourceDataProviderWithRange() {
        return Stream.of(
                Arguments.of(200, false),
                Arguments.of(204, false),
                Arguments.of(206, false),
                Arguments.of(416, false),
                Arguments.of(404, true),
                Arguments.of(500, true)
        );
    }

    @Test
    void testDoRequestRateLimitWhenThrow() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = spy(new RemoteRepositoryRepoTypeConfig());
        ReflectionTestUtils.setField(repoConfig, "repoTypeConfig", remoteRepoConfig);
        String dockerRegistry = "https://registry-1.docker.io/";
        repoConfig.getRepoTypeConfig().setUrl(dockerRegistry);
        HttpRequestBase mockHttpRequestBase = mock(HttpRequestBase.class);
        when(mockHttpRequestBase.getMethod()).thenReturn("GET");

        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService);


        doThrow(new RateLimiterException("Request rate limiter time exceeded")).when(rateLimiterService)
                .acquirePermit(dockerRegistry, false);

        assertThrows(RateLimiterException.class, () -> httpRepo.doRequestRateLimit(mockHttpRequestBase));
    }

    @Test
    void testDoRequestRateLimit() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = spy(new RemoteRepositoryRepoTypeConfig());
        ReflectionTestUtils.setField(repoConfig, "repoTypeConfig", remoteRepoConfig);
        String dockerRegistry = "https://registry-1.docker.io/";
        repoConfig.getRepoTypeConfig().setUrl(dockerRegistry);
        HttpRequestBase mockHttpRequestBase = mock(HttpRequestBase.class);
        when(mockHttpRequestBase.getMethod()).thenReturn("GET");

        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService);
        httpRepo.doRequestRateLimit(mockHttpRequestBase);
    }

    @ParameterizedTest
    @MethodSource("encodeUrlProvider")
    void testCreateEncodeUrl(boolean withProperties, String expectedResult, String expectedEncodedUrl) {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        RemoteRepositoryRepoTypeConfig remoteRepoConfig = spy(new RemoteRepositoryRepoTypeConfig());
        ReflectionTestUtils.setField(repoConfig, "repoTypeConfig", remoteRepoConfig);
        HttpRepo httpRepo = spy(new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService));
        String path = "page_[test].html";
        doReturn(path).when(httpRepo).convertRequestPathIfNeeded(any());
        doReturn("page_[test].html").when(httpRepo)
                .resolvePathForUrl(any(), any());
        RequestContext mockContext = Mockito.mock(RequestContext.class);
        when(mockContext.getRequest()).thenReturn(Mockito.mock(Request.class));
        if (withProperties) {
            Properties properties = new PropertiesImpl();
            properties.put("key1", "value1");
            when(mockContext.getProperties()).thenReturn(properties);
            when(mockContext.getRequest()).thenReturn(Mockito.mock(Request.class));
        }
        when(remoteRepoConfig.getUrl()).thenReturn("http://localhost:8082/artifactory/example-repo-local");
        String result = httpRepo.assembleRetrieveInfoUrl(path, mockContext);
        assertEquals(expectedResult, result);
        String encodedUrl = httpRepo.encodeIfNeeded(result, mockContext);
        assertEquals(expectedEncodedUrl, encodedUrl);
    }

    private static Stream<Arguments> encodeUrlProvider() {
        return Stream.of(Arguments.of(
                        false,
                        "http://localhost:8082/artifactory/example-repo-local/page_[test].html",
                        "http://localhost:8082/artifactory/example-repo-local/page_%5Btest%5D.html"),
                Arguments.of(true,
                        "http://localhost:8082/artifactory/example-repo-local/page_[test].html;key1=value1",
                        "http://localhost:8082/artifactory/example-repo-local/page_%5Btest%5D.html;key1=value1"));
    }

    @Test
    void testGetHttpClient() {
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("remote-test-1", RepoType.Generic);
        HttpRepo httpRepo =
                spy(new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService));

        CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);
        doReturn(mockHttpClient).when(httpRepo).createHttpClient(anyInt(), anyBoolean(), anyBoolean(), any());
        CloseableHttpClient client = httpRepo.getHttpClient();
        assertNotNull(client);
        client = httpRepo.getHttpClient();
        assertNotNull(client);
        verify(httpRepo, times(1)).createHttpClient(anyInt(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void testGetHttpClientOfflineRemoteRepoFromDescriptor() {
        var centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        doReturn(centralConfigDescriptor).when(centralConfigService).getDescriptor();
        doReturn(true).when(centralConfigDescriptor).isOfflineMode();
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("remote-test-1", RepoType.Generic);
        HttpRepo httpRepo =
                spy(new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService));
        assertThrows(IllegalStateException.class, httpRepo::getHttpClient);
    }

    @Test
    void testGetHttpClientOfflineRemoteRepoFromConfig() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("remote-test-1", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setOffline(true);
        HttpRepo httpRepo =
                spy(new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService));
        assertThrows(IllegalStateException.class, httpRepo::getHttpClient);
    }

    @Test
    void runWorkersTest() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = spy(new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService));
        httpRepo.runWorkers(any(), any(), any(), any());
        verify(httpRepo, times(1)).runWorkers(any(), any(), any(), any());
    }

    @Test
    void runWorkersAsyncTest() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = spy(new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService));
        httpRepo.runWorkersAsync(any(), any(), any(), any());
        verify(httpRepo, times(1)).runWorkersAsync(any(), any(), any(), any());
    }

    @Test
    void testCreateRepoMonitorIfNeeded_AssumedOfflineAndNotMonitored() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = spy(new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService));
        when(onlineMonitorService.isRemoteRepoMonitored(httpRepo.getKey())).thenReturn(false);
        httpRepo.setAssumedOffline();

        httpRepo.isAssumedOffline();

        verify(httpRepo, times(1)).putOffline();
        verify(onlineMonitorService, times(1)).isRemoteRepoMonitored(httpRepo.getKey());
        verify(onlineMonitorService, times(1)).createOfflineRepoMonitor(httpRepo);
    }

    @Test
    void testCreateRepoMonitorIfNeeded_AssumedOfflineAndMonitored() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = spy(new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService));
        when(onlineMonitorService.isRemoteRepoMonitored(httpRepo.getKey())).thenReturn(true);
        httpRepo.setAssumedOffline();

        httpRepo.isAssumedOffline();

        verify(httpRepo, never()).putOffline();
        verify(onlineMonitorService, times(1)).isRemoteRepoMonitored(httpRepo.getKey());
        verify(onlineMonitorService, never()).createOfflineRepoMonitor(httpRepo);
    }

    @Test
    void testCreateRepoMonitorIfNeeded_NotAssumedOffline() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = spy(new HttpRepo(descriptor, repoConfig,
                internalRepoService, addonsManager, researchService, null, onlineMonitorService, centralConfigService,
                rateLimiterService));

        verify(httpRepo, never()).putOffline();
        verify(onlineMonitorService, never()).isRemoteRepoMonitored(httpRepo.getKey());
        verify(onlineMonitorService, never()).createOfflineRepoMonitor(httpRepo);
    }

    @Test
    void testIsAssumedOffline() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo =
                spy(new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService));
        assertFalse(httpRepo.isAssumedOffline());
        httpRepo.setAssumedOffline();
        assertTrue(httpRepo.isAssumedOffline());
    }

    @Test
    void setCurationPropertyWhenBlockDownloadDisabled() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                null, onlineMonitorService, centralConfigService, rateLimiterService);

        RepoResource repoResource = mock(RepoResource.class);
        when(repoResource.getRepoPath()).thenReturn(new RepoPathImpl("a", "b"));
        Assertions.assertNull(httpRepo.setOriginRemotePathProperty(repoResource, null));
    }

    @Test
    void setCurationPropertyWhenPayloadIsNull() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                null, onlineMonitorService, centralConfigService, rateLimiterService);

        when(curationAddon.isBlockDownloadEnabled(any())).thenReturn(true);
        when(smartRepoService.isSmartRepo(any())).thenReturn(true);

        RemoteRepoResource resource = new RemoteRepoResource(new RepoPathImpl("repoKey", "path/file.txt"),
                0, "", 0, new HashSet<>(), new Header[0]);

        Assertions.assertNull(httpRepo.setOriginRemotePathProperty(resource, null));
    }

    @Test
    void setCurationPropertyWhenPayloadIsNotNullAndSmartRepo() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                null, onlineMonitorService, centralConfigService, rateLimiterService);

        when(curationAddon.isBlockDownloadEnabled(any())).thenReturn(true);
        when(smartRepoService.isSmartRepo(any())).thenReturn(true);

        RemoteRepoResource resource = new RemoteRepoResource(new RepoPathImpl("repoKey", "path/file.txt"),
                0, "", 0, new HashSet<>(), new Header[0]);
        CurationPackageStatusPayload
                payload = new CurationPackageStatusPayload("packageType", "packageUrl",
                "repoKey", "projectKey", "user", "email", "originRepoKey",
                "originProjectKey", "originServer", "downloadUrl",
                ArrayListMultimap.create(), false, "waiverRequest", false, null, null, null);

        Assertions.assertNull(httpRepo.setOriginRemotePathProperty(resource, null));
    }

    @Test
    void setCurationPropertyWhenPayloadIsNotNullAndNotSmartRepo() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);
        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                null, onlineMonitorService, centralConfigService, rateLimiterService);

        when(curationAddon.isBlockDownloadEnabled(any())).thenReturn(true);

        Header header = new BasicHeader(ORIGIN_REMOTE_PATH, "ORIGIN_REMOTE_PATH");
        when(smartRepoService.isSmartRepo(any())).thenReturn(false);
        RemoteRepoResource resource = new RemoteRepoResource(new RepoPathImpl("repoKey", "path/file.txt"),
                0, "", 0, new HashSet<>(), new Header[] {header});
        ArrayListMultimap<String, String> responseHeaders = ArrayListMultimap.create();
        responseHeaders.put(HttpHeaders.CONTENT_TYPE, "application/json");

        Properties actual = httpRepo.setOriginRemotePathProperty(resource, null);
        Set<String> properties = actual.get(ORIGIN_REMOTE_PATH_PROP_KEY);
        assertTrue(properties.contains("ORIGIN_REMOTE_PATH"));
    }

    @CsvSource(useHeadersInDisplayName = true, textBlock =
            """
                    SMART,  RETRIEVED_URL,  ASSEMBLED_URL,  EXPECTED_URL,                         EXPECTED_PROP
                    true,   url-retrieved,  url-assembled,  url-retrieved,                        url-retrieved
                    true,   empty,          url-assembled,  url-assembled,                        empty
                    true,   empty,          empty,          http://test.com/path/file.txt,        empty
                    true,   local,          url-assembled,  local:url-assembled,                  local:url-assembled
                    true,   local,          empty,          local:http://test.com/path/file.txt,  empty
                    false,  empty,          url-assembled,  url-assembled,                        url-assembled
                    false,  empty,          empty,          http://test.com/path/file.txt,        empty
                    """
    )
    @ParameterizedTest
    void checkOriginRemotePath(boolean isSmartRepo, String retrievedUrl, String assembledUrl, String expectedUrl,
            String expectedProp) {
        when(smartRepoService.isSmartRepo(any())).thenReturn(isSmartRepo);
        String repoKey = "repoKey";
        RepoPath repoPath = new RepoPathImpl(repoKey + "-cache", "path/file.txt");

        RemoteRepoConfig repoConfig = RemoteRepoConfig.create(repoKey, RepoType.Generic);
        repoConfig.getRepoTypeConfig().setUrl("http://test.com");

        HttpRepoForTest httpRepo =
                new HttpRepoForTest(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);
        HttpRepo httpRepoSpy = spy(httpRepo);
        doReturn(assembledUrl.equals("empty") ? "" : assembledUrl)
                .when(httpRepoSpy).assembleRetrieveInfoUrl(any(), any());
        doReturn(retrievedUrl.equals("empty") ? "" : retrievedUrl)
                .when(httpRepoSpy).retrieveOriginRemotePathHeader(any(), any());

        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        when(localCacheRepo.getKey()).thenReturn(repoKey);
        ReflectionTestUtils.setField(httpRepoSpy, "localCacheRepo", localCacheRepo);

        FileInfo fileInfo = new FileInfoImpl(repoPath);
        FileResource fileResource = new FileResource(fileInfo, true);

        PropertiesService propertiesService = mock(PropertiesService.class);
        when(contextMock.beanForType(PropertiesService.class)).thenReturn(propertiesService);

        when(smartRepoService.isSmartRepo(any())).thenReturn(isSmartRepo);
        RequestContext requestContext = mock(RequestContext.class);
        when(requestContext.isSkipCurationOnResource()).thenReturn(false);

        when(curationAddon.isBlockDownloadEnabled(any())).thenReturn(true);
        ArgumentCaptor<String> downloadUrlCaptor = ArgumentCaptor.forClass(String.class);
        doAnswer(invocation -> PERMITTED_STATUS)
                .when(curationAddon).getCachedPackageStatus(any(), any(), downloadUrlCaptor.capture(), any());

        httpRepoSpy.returnCachedResource(repoPath, fileResource, requestContext);

        assertEquals(expectedUrl, downloadUrlCaptor.getValue());

        if (expectedProp.equals("empty")) {
            assertEquals("", fileResource.getOriginRemotePath());
            verify(propertiesService, never()).addProperty(any(), any(), any(), any());
        } else {
            assertEquals(expectedProp, fileResource.getOriginRemotePath());
            Property value = new Property(ORIGIN_REMOTE_PATH_PROP_KEY);
            verify(propertiesService, times(1)).addProperty(eq(repoPath), any(), eq(value),
                    eq(expectedProp));
        }
    }

    @Test
    void whenResourceAlreadyHasOriginRemotePath() {
        String repoKey = "repoKey";
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create(repoKey, RepoType.Generic);

        HttpRepo httpRepo = new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                null, onlineMonitorService, centralConfigService, rateLimiterService);

        RepoPath repoPath = new RepoPathImpl(repoKey + "-cache", "path/file.txt");
        FileInfo fileInfo = new FileInfoImpl(repoPath);
        FileResource fileResource = new FileResource(fileInfo, true);
        String existingOriginRemotePath = "existing-origin-path";
        fileResource.setOriginRemotePath(existingOriginRemotePath);

        RequestContext requestContext = mock(RequestContext.class);
        PropertiesService propertiesService = mock(PropertiesService.class);
        when(contextMock.beanForType(PropertiesService.class)).thenReturn(propertiesService);

        httpRepo.checkOriginRemotePath(fileResource, requestContext);

        assertEquals(existingOriginRemotePath, fileResource.getOriginRemotePath());
        verify(propertiesService, never()).addProperty(any(), any(), any(), any());
    }

    @Test
    void retrieveHeaderWhenOffline() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);

        HttpRepo httpRepo =
                new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);
        HttpRepo httpRepoSpy = spy(httpRepo);
        doReturn(true).when(httpRepoSpy).isOffline();

        String header = httpRepoSpy.retrieveOriginRemotePathHeader(null, null);

        assertTrue(header.isEmpty());
    }

    @Test
    void retrieveHeaderHappyFlow() throws IOException {
        assertEquals("originRemotePath",
                retrieveHeader(new BasicHeader(ORIGIN_REMOTE_PATH, "originRemotePath")));
    }

    @Test
    void retrieveHeaderWhenNoHeader() throws IOException {
        assertEquals("", retrieveHeader(null));
    }

    private String retrieveHeader(Header header) throws IOException {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);

        HttpRepo httpRepo =
                new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);
        HttpRepo httpRepoSpy = spy(httpRepo);
        doReturn(false).when(httpRepoSpy).isOffline();
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getFirstHeader(eq(ORIGIN_REMOTE_PATH))).thenReturn(header);
        doReturn(response).when(httpRepoSpy).executeMethod(any(), any(), any());
        HttpRepo.HttpRequestExecutionContext httpRequestExecutionContext =
                mock(HttpRepo.HttpRequestExecutionContext.class);
        doReturn(httpRequestExecutionContext).when(httpRepoSpy).createMethodExecutionContext(any(), any());

        return httpRepoSpy.retrieveOriginRemotePathHeader(null, null);
    }

    @Test
    void retrieveHeaderWhenException() throws IOException {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);

        HttpRepo httpRepo =
                new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);

        HttpRepo httpRepoSpy = spy(httpRepo);
        doReturn(false).when(httpRepoSpy).isOffline();

        doThrow(RuntimeException.class).when(httpRepoSpy).executeMethod(any(), any(), any());

        HttpRepo.HttpRequestExecutionContext httpRequestExecutionContext =
                mock(HttpRepo.HttpRequestExecutionContext.class);
        doReturn(httpRequestExecutionContext).when(httpRepoSpy).createMethodExecutionContext(any(), any());

        DownloadRequestContext requestContext = new DownloadRequestContext(mock(ArtifactoryRequest.class));
        requestContext.setSkipCurationOnResource(false);
        assertFalse(requestContext.isSkipCurationOnResource());

        String header = httpRepoSpy.retrieveOriginRemotePathHeader(null, null);

        assertTrue(header.isEmpty());
    }

    @Test
    void retrieveInfoWhenException() throws IOException {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create("repoKey", RepoType.Generic);

        HttpRepo httpRepo =
                new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);

        HttpRepo httpRepoSpy = spy(httpRepo);
        doReturn(false).when(httpRepoSpy).isOffline();

        HttpRepo.HttpRequestExecutionContext httpRequestExecutionContext =
                mock(HttpRepo.HttpRequestExecutionContext.class);
        when(httpRequestExecutionContext.headers()).thenReturn(mock(HeadersMultiMap.class));
        doReturn(httpRequestExecutionContext).when(httpRepoSpy).createMethodExecutionContext(any(), any());

        doNothing().when(httpRepoSpy).notifyInterceptorsOnBeforeRemoteHttpMethodExecution(any(), any(), any(), any());

        doThrow(IOException.class).when(httpRepoSpy).executeMethod(any(), any(), any());

        assertThrows(RuntimeException.class, () -> httpRepoSpy.retrieveInfo(null, false, null));
    }

    @ParameterizedTest
    @CsvSource({
            "true, originRemotePath",
            "false, downloadUrl"
    })
    void originRemotePathHeaderUpdate(boolean smartRepo, String expectedHeader) {
        when(smartRepoService.isSmartRepo(any())).thenReturn(smartRepo);
        getBound().setProperty(ConstantValues.saveGetResource, "true");

        String repoKey = "repoKey";
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create(repoKey, RepoType.Generic);
        RepoPath repoPath = new RepoPathImpl(repoKey + "-cache", "path/file.txt");

        HttpRepo httpRepo =
                new HttpRepo(descriptor, repoConfig, internalRepoService, addonsManager, researchService,
                        null, onlineMonitorService, centralConfigService, rateLimiterService);

        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        ResponseForTest response = new ResponseForTest(statusLine);
        response.setHeader(ORIGIN_REMOTE_PATH, "originRemotePath");

        RemoteRepoResource handled =
                (RemoteRepoResource) httpRepo.handleGetInfoResponse(repoPath, null, "downloadUrl", response, null,
                        null);
        Header[] allHeaders = handled.getAllHeaders();
        assertEquals(1, allHeaders.length);
        assertEquals(expectedHeader, allHeaders[0].getValue());
    }

    private static class ResponseForTest extends BasicHttpResponse implements CloseableHttpResponse {

        public ResponseForTest(StatusLine statusline) {
            super(statusline);
        }

        @Override
        public void close() {
            // empty
        }
    }

    private static class HttpRepoForTest extends HttpRepo {

        public HttpRepoForTest(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
                InternalRepositoryService repositoryService, AddonsManager addonsManager,
                ResearchService researchService, RemoteRepo<?, ?> oldRemoteRepo,
                OnlineMonitorService onlineMonitorService, CentralConfigService configService,
                IRequestRateLimiterService rateLimiterService) {
            super(descriptor, repoConfig, repositoryService, addonsManager, researchService, oldRemoteRepo,
                    onlineMonitorService, configService);
        }

        @Override
        public RepoResource returnCachedResource(RepoPath repoPath, RepoResource cachedResource,
                RequestContext context) {
            return super.returnCachedResource(repoPath, cachedResource, context);
        }

    }

    @Test
    void testExtractCurationErrorMessageIfExistsWithJsonParsingException() throws IOException {
        final HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        final HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        final String malformedJson = "{\"errors\":[{\"message\":\"unclosed string}";

        CloseableHttpResponse response = createCloseableHttpResponse(malformedJson);

        final JsonSensitiveDataException ex = assertThrows(JsonSensitiveDataException.class, () ->
                httpRepo.extractBlockedRepoResource(REPO_PATH, response, malformedJson)
        );
        assertTrue(ex.getMessage()
                .contains("JsonEOFException: Unexpected end-of-input: " +
                        "was expecting closing quote for a string value"));
    }

    @ParameterizedTest
    @MethodSource("extractCurationErrorMessageFromContentTestCases")
    void testExtractCurationErrorMessageIfExists(final String jsonContent,
            final boolean expectBlocked,
            final String expectedMessage) throws IOException {
        final HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        final HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);

        CloseableHttpResponse response = createCloseableHttpResponse(jsonContent);
        if (expectedMessage != null) {
            when(response.getFirstHeader(CURATION_ORIGIN)).thenReturn(new BasicHeader(CURATION_ORIGIN, "true"));
            when(response.containsHeader(CURATION_ORIGIN)).thenReturn(true);
        }

        final BlockedRepoResource result = httpRepo.extractBlockedRepoResource(REPO_PATH, response, jsonContent);

        if (expectBlocked) {
            assertNotNull(result, "Expected a BlockedRepoResource but got null");
            assertEquals(expectedMessage, result.getMessage());
            assertEquals(BlockedRepoResource.BlockedType.CURATION, result.getBlockedType());
            assertEquals(REPO_PATH, result.getRepoPath());
        } else {
            assertNull(result, "Expected null but got a BlockedRepoResource");
        }
    }

    @Test
    void testExtractCurationErrorMessageFromResponseHeader() {
        final HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        final HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(mock(StatusLine.class));
        when(response.getStatusLine().getStatusCode()).thenReturn(HttpStatus.SC_FORBIDDEN);
        Header[] allHeaders = new Header[] {
                new BasicHeader("Content-Type", "application/json"),
                new BasicHeader("npm-response", "Package curation has failed")
        };
        when(response.getAllHeaders()).thenReturn(allHeaders);
        when(response.getFirstHeader(CURATION_ORIGIN)).thenReturn(new BasicHeader(CURATION_ORIGIN, "true"));
        when(response.containsHeader(CURATION_ORIGIN)).thenReturn(true);

        final BlockedRepoResource result = httpRepo.extractBlockedRepoResource(REPO_PATH, response, null);
        assertNotNull(result, "Expected a BlockedRepoResource but got null");
        assertEquals("Package curation has failed", result.getMessage());
        assertEquals(BlockedRepoResource.BlockedType.CURATION, result.getBlockedType());
        assertEquals(REPO_PATH, result.getRepoPath());
    }

    @Test
    void testExtractMessageForCurationRelyingOnIfHeaderExists() throws IOException {
        final HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        final HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        String jsonContent = "{\"errors\":[{\"message\":\"Package is forbidden!\"}]}";

        CloseableHttpResponse response = createCloseableHttpResponse(jsonContent);

        when(response.getAllHeaders()).thenReturn(new Header[] {});
        when(response.getFirstHeader(CURATION_ORIGIN)).thenReturn(new BasicHeader(CURATION_ORIGIN, "true"));
        when(response.containsHeader(CURATION_ORIGIN)).thenReturn(true);

        final BlockedRepoResource result = httpRepo.extractBlockedRepoResource(REPO_PATH, response, jsonContent);
        assertNotNull(result, "Expected a BlockedRepoResource but got null");
        assertEquals("Package is forbidden!", result.getMessage());
        assertEquals(BlockedRepoResource.BlockedType.CURATION, result.getBlockedType());
        assertEquals(REPO_PATH, result.getRepoPath());
    }

    @ParameterizedTest
    @MethodSource("assertNotBlockedByCurationTestCases")
    void testAssertNotBlockedByCurationWhenFeatureFlagIsOff(String blockedByCurationBasedOnHeaderValue,
            String jsonContent,
            String msg)
            throws IOException {
        final HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        final HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);

        CloseableHttpResponse response = createCloseableHttpResponse(jsonContent);
        when(response.containsHeader(ARTIFACTORY_ID)).thenReturn(true);

        homeStub.setProperty(ConstantValues.curationBlockedBasedOnHeader.getPropertyName(),
                blockedByCurationBasedOnHeaderValue);
        if (Boolean.parseBoolean(blockedByCurationBasedOnHeaderValue)) {
            when(response.getAllHeaders()).thenReturn(new Header[] {});
            when(response.getFirstHeader(CURATION_ORIGIN)).thenReturn(new BasicHeader(CURATION_ORIGIN, "true"));
            when(response.containsHeader(CURATION_ORIGIN)).thenReturn(true);
        }
        BlockedRepoResource result = httpRepo.assertNotBlockedByCuration(REPO_PATH, response, 403);

        assertNotNull(result, "Expected a BlockedRepoResource but got null");
        assertEquals(msg, result.getMessage());
        assertEquals(BlockedRepoResource.BlockedType.CURATION, result.getBlockedType());
        assertEquals(REPO_PATH, result.getRepoPath());
    }

    private static CloseableHttpResponse createCloseableHttpResponse(String jsonContent) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class, RETURNS_DEEP_STUBS);
        when(response.getStatusLine().getStatusCode()).thenReturn(HttpStatus.SC_FORBIDDEN);
        HttpEntity httpEntity = mock(HttpEntity.class);
        when(response.getEntity()).thenReturn(httpEntity);
        InputStream content = new ByteArrayInputStream(jsonContent.getBytes(Charset.defaultCharset()));
        when(httpEntity.getContent()).thenReturn(content);
        return response;
    }

    private static Stream<Arguments> extractCurationErrorMessageFromContentTestCases() {
        return Stream.of(
                Arguments.of("{\"errors\":[{\"message\":\"Package blocked by curation policy\"}]}",
                        true, "Package blocked by curation policy"),
                Arguments.of("{\"errors\":[{\"detail\":\"Cargo package blocked by curation rules\"}]}",
                        true, "Cargo package blocked by curation rules"),
                Arguments.of(
                        "{\"errors\":[{\"message\":\"Docker curation block\",\"detail\":\"Cargo detail message\"}]}",
                        true, "Docker curation block"
                ),
                Arguments.of("{\"errors\":[{\"message\":\"Package not found in registry\"}]}", false, null),
                Arguments.of("{\"errors\":[{\"detail\":\"Package version not available\"}]}", false, null),
                Arguments.of("{\"errors\":[]}", false, null),
                Arguments.of("{\"status\":\"warn\",\"message\":\"Something went wrong\"}", false, null),
                Arguments.of("{\"errors\":null}", false, null),
                Arguments.of("{\"errors\":[{\"code\":\"FORBIDDEN\",\"status\":403}]}", false, null),
                Arguments.of("{\"errors\":[{\"message\":\"Blocked by curation scan\"}," +
                        "                 {\"message\":\"Another error\"}]}", true, "Blocked by curation scan"),
                Arguments.of("{\"errors\":[{\"message\":\"Package blocked by CURATION policy\"}]}", true,
                        "Package blocked by CURATION policy"),
                Arguments.of("", false, null)
        );
    }

    private static Stream<Arguments> assertNotBlockedByCurationTestCases() {
        return Stream.of(
                Arguments.of("false", "{\"errors\":[{\"message\":\"Package blocked by curation policy\"}]}",
                        "Package blocked by curation policy"),
                Arguments.of("true", "{\"errors\":[{\"message\":\"Forbidden\"}]}", "Forbidden")
        );
    }

    @Test
    void sendUnifiedEvent_shouldAddHeaders_whenResponseHasNonEmptyHeaders() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
        ArtifactoryRequest artifactoryRequest = mock(ArtifactoryRequest.class);
        RepoPath repoPath = mock(RepoPath.class);
        Map<String, String> expectedHeaders = Map.of("X-Custom-Header", "value");
        DispatcherResponse response = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder().headers(expectedHeaders).build())
                .build();
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, artifactoryRequest,
                repoPath)).thenReturn(response);
        httpRepo.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, remoteRequestCtx,
                artifactoryRequest, repoPath);
        assertEquals(expectedHeaders, remoteRequestCtx.getHeaders());
    }

    @Test
    void sendUnifiedEvent_shouldNotAddHeaders_whenResponseDataIsNullOrEmpty() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
        ArtifactoryRequest artifactoryRequest = mock(ArtifactoryRequest.class);
        RepoPath repoPath = mock(RepoPath.class);
        DispatcherResponse response = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(null)
                .build();
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, artifactoryRequest,
                repoPath)).thenReturn(response);
        httpRepo.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, remoteRequestCtx,
                artifactoryRequest, repoPath);
        assertTrue(remoteRequestCtx.getHeaders().isEmpty());
    }

    @Test
    void sendUnifiedEvent_shouldThrowWorkerStopException_whenUnifiedEventExceptionWithConflict() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
        ArtifactoryRequest artifactoryRequest = mock(ArtifactoryRequest.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, artifactoryRequest,
                repoPath))
                .thenThrow(new UnifiedEventException("Conflict error"));

        WorkerStopException exception = assertThrows(WorkerStopException.class,
                () -> httpRepo.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD,
                        remoteRequestCtx, artifactoryRequest, repoPath));
        assertTrue(exception.getMessage().contains("Conflict error"));
    }

    @Test
    void sendUnifiedEvent_shouldRethrowUnifiedEventException_whenNonConflictStatusCode() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
        ArtifactoryRequest artifactoryRequest = mock(ArtifactoryRequest.class);
        RepoPath repoPath = mock(RepoPath.class);
        UnifiedEventException originalException =
                new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "Bad request error");
        when(unifiedEventService.sendArtifactEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD, artifactoryRequest,
                repoPath)).thenThrow(originalException);

        UnifiedEventException exception = assertThrows(UnifiedEventException.class,
                () -> httpRepo.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_DOWNLOAD,
                        remoteRequestCtx, artifactoryRequest, repoPath));
        assertEquals(originalException, exception);
    }

    @Test
    void sendUnifiedEvent_shouldReturnGracefully_whenRequestIsNull() {
        HttpRepoDescriptor repoDescriptor = prepareCentralConfigService();
        HttpRepo httpRepo = createHttpRepo(REPO_CONFIG, repoDescriptor);
        RemoteRequestCtx remoteRequestCtx = new RemoteRequestCtx();
        RepoPath repoPath = mock(RepoPath.class);

        httpRepo.sendUnifiedEvent(SupportedUnifiedEvents.BEFORE_REMOTE_INFO, remoteRequestCtx, null, repoPath);

        verify(unifiedEventService, never()).sendArtifactEvent(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("resolvePathForUrlTestCases")
    void testResolvePathForUrlForDirectoryRequest(boolean flagEnabled, boolean isDirectory,
                                                  String inputPath, String expectedPath) {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        ArtifactoryRequest requestMock = mock(ArtifactoryRequest.class);
        when(requestMock.isDirectoryRequest()).thenReturn(isDirectory);
        setStringSystemProperty(ConstantValues.directoryRequestQueryParamsEnabled,
                    String.valueOf(flagEnabled));
        String result = httpRepo.resolvePathForUrl(inputPath, requestMock);
        assertEquals(expectedPath, result);
    }

    private static Stream<Arguments> resolvePathForUrlTestCases() {
        return Stream.of(
                Arguments.of(false, true, "dl", "dl"),
                Arguments.of(true, false, "dl", "dl"),
                Arguments.of(true, true, "dl/", "dl/"),
                Arguments.of(true, true, "dl", "dl/")
        );
    }

    @Test
    void resolvePathForUrl_throwsIllegalUrlPathException_whenAlternativeUrlBlockedBySsrfGuard() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardEnabled, "true");
        InternalArtifactoryRequest req = InternalRequestFactory.createInternalRequestDisableRedirect(
                new RepoPathImpl("repoKey", "_external/internal-data/data"));
        req.setAlternativeRemoteDownloadUrl("http://127.0.0.1:9999/data");
        when(urlVerifier.isOutboundFetchBlocked("http://127.0.0.1:9999/data", "repoKey")).thenReturn(true);

        assertThrows(IllegalUrlPathException.class,
                () -> httpRepo.resolvePathForUrl("_external/internal-data/data", req),
                "resolvePathForUrl must throw when the SSRF guard blocks the alternative URL");
        verify(urlVerifier).isOutboundFetchBlocked("http://127.0.0.1:9999/data", "repoKey");
    }

    @Test
    void resolvePathForUrl_returnsAlternativeUrl_whenSsrfGuardAllows() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardEnabled, "true");
        String allowedUrl = "https://registry.bower.io/some-pkg-1.0.0.tgz";
        InternalArtifactoryRequest req = InternalRequestFactory.createInternalRequestDisableRedirect(
                new RepoPathImpl("repoKey", "_external/some-pkg/file.tgz"));
        req.setAlternativeRemoteDownloadUrl(allowedUrl);
        when(urlVerifier.isOutboundFetchBlocked(allowedUrl, "repoKey")).thenReturn(false);

        String result = httpRepo.resolvePathForUrl("_external/some-pkg/file.tgz", req);

        assertEquals(allowedUrl, result,
                "resolvePathForUrl must return the alternative URL when the SSRF guard allows it");
        verify(urlVerifier).isOutboundFetchBlocked(allowedUrl, "repoKey");
    }

    @Test
    void resolvePathForUrl_skipsGuardCheck_whenBothFlagsDisabled() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardEnabled, "false");
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardShadowMode, "false");
        String blockedUrl = "http://127.0.0.1:9999/data";
        InternalArtifactoryRequest req = InternalRequestFactory.createInternalRequestDisableRedirect(
                new RepoPathImpl("repoKey", "_external/internal-data/data"));
        req.setAlternativeRemoteDownloadUrl(blockedUrl);

        // Both guard and shadow mode are off — UrlVerifier must not be consulted.
        String result = httpRepo.resolvePathForUrl("_external/internal-data/data", req);

        assertEquals(blockedUrl, result,
                "resolvePathForUrl must bypass the guard entirely when both flags are disabled");
        verify(urlVerifier, never()).isOutboundFetchBlocked(any(), any());
    }

    @Test
    void resolvePathForUrl_logsButDoesNotBlock_whenShadowModeOnlyEnabled() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardEnabled, "false");
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardShadowMode, "true");
        String blockedUrl = "http://127.0.0.1:9999/data";
        InternalArtifactoryRequest req = InternalRequestFactory.createInternalRequestDisableRedirect(
                new RepoPathImpl("repoKey", "_external/internal-data/data"));
        req.setAlternativeRemoteDownloadUrl(blockedUrl);
        when(urlVerifier.isOutboundFetchBlocked(blockedUrl, "repoKey")).thenReturn(true);

        // Shadow mode on, guard off — URL must be returned (no throw) even though verifier says blocked.
        String result = httpRepo.resolvePathForUrl("_external/internal-data/data", req);

        assertEquals(blockedUrl, result,
                "Shadow mode must log but not block — the URL must still be returned");
        verify(urlVerifier).isOutboundFetchBlocked(blockedUrl, "repoKey");
    }

    @Test
    void executeMethod_blocksLoopbackTarget_atOutboundChokePoint_whenGuardEnforced() {
        HttpRepo httpRepo = prepareHttpRepoWithPropagateQueryParams(true);
        getBound().setProperty(ConstantValues.httpRepoOutboundSsrfGuardEnabled, "true");
        String routerUrl = "http://127.0.0.1:8046/router/api/v1/system/health";
        when(urlVerifier.isOutboundTargetBlocked(routerUrl, "repoKey")).thenReturn(true);

        HttpGet method = new HttpGet(routerUrl);
        assertThrows(IllegalUrlPathException.class, () -> httpRepo.executeMethod(method),
                "executeMethod must block a non-bridge loopback target at the outbound choke point");
        verify(urlVerifier).isOutboundTargetBlocked(routerUrl, "repoKey");
    }
}

