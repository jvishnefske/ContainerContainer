package org.artifactory.repo;

import com.google.common.cache.CacheBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.GuaveCacheConnectionManagersHolder;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.repo.http.IdleConnectionMonitorServiceImpl;
import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.artifactory.api.repo.UpstreamRepoKind.SMART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnsibleHttpRepoTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "ansible-remote";

    @Mock
    private MBeanRegistrationServiceImpl mBeanRegistrationService;
    @Mock
    private OnlineMonitorService onlineMonitorService;
    @Mock
    private IRequestRateLimiterService rateLimiterService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private InternalArtifactoryContext artifactoryContext;

    private AnsibleHttpRepo ansibleHttpRepo;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);

        AddonsManager addonsManager = mock(AddonsManager.class);
        FileService fileService = mock(FileService.class);
        VfsItemProviderFactory itemProviderFactory = mock(VfsItemProviderFactory.class);
        VfsMutableFolderProvider folderProvider = mock(VfsMutableFolderProvider.class);
        ResearchService researchService = mock(ResearchService.class);
        IdleConnectionMonitorService idleConnSvc =
                new IdleConnectionMonitorServiceImpl(new GuaveCacheConnectionManagersHolder());
        DenyListCacheHolderService denyListSvc = mock(DenyListCacheHolderService.class);
        when(denyListSvc.getDenyListCache()).thenReturn(CacheBuilder.newBuilder().build());

        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(artifactoryContext.beanForType(FileService.class)).thenReturn(fileService);
        when(artifactoryContext.beanForType(VfsItemProviderFactory.class)).thenReturn(itemProviderFactory);
        when(artifactoryContext.beanForType(IdleConnectionMonitorService.class)).thenReturn(idleConnSvc);
        when(artifactoryContext.beanForType(DenyListCacheHolderService.class)).thenReturn(denyListSvc);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(mock(AolService.class));
        when(artifactoryContext.beanForType(MBeanRegistrationServiceImpl.class))
                .thenReturn(mBeanRegistrationService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(SmartRepoService.class)).thenReturn(mock(SmartRepoService.class));
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
        when(itemProviderFactory.createFolderProvider(any(), any(), any(), any()))
                .thenReturn(folderProvider);

        RemoteRepoConfig repoConfig = RemoteRepoConfig.create(REPO_KEY, RepoType.Ansible);
        repoConfig.getRepoTypeConfig().setUrl("https://galaxy.ansible.com");
        repoConfig.getRepoTypeConfig().setUsername("user");
        repoConfig.getRepoTypeConfig().setPassword("pass");
        CentralConfigService configService = mock(CentralConfigService.class);
        when(configService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(onlineMonitorService.isResourceUnavailable(any(StatusLine.class))).thenReturn(false);

        ansibleHttpRepo = spy(new AnsibleHttpRepo(new HttpRepoDescriptor(), repoConfig,
                repositoryService, addonsManager, researchService, null, onlineMonitorService,
                configService, rateLimiterService));
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void getTokenClientThrowsWhenOffline() {
        ansibleHttpRepo.getRepoConfig().getRepoTypeConfig().setOffline(true);

        assertThrows(IllegalStateException.class, ansibleHttpRepo::getTokenClient);
    }

    @Test
    void getTokenClientCreatesClientAndRegistersMBeanWhenOnline() {
        ansibleHttpRepo.init(false);

        ansibleHttpRepo.getHttpClient();
        verify(mBeanRegistrationService, never())
                .register(any(HTTPConnectionPool.class), eq("HTTPConnectionPool"),
                        eq(REPO_KEY + "-token-client"));

        ansibleHttpRepo.getTokenClient();
        verify(mBeanRegistrationService, times(1))
                .register(any(HTTPConnectionPool.class), eq("HTTPConnectionPool"),
                        eq(REPO_KEY + "-token-client"));

        ansibleHttpRepo.close();
    }

    @Test
    void fetchAnsibleAuthTokenReturnsTokenOnSuccess() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        HttpEntity entity = mock(HttpEntity.class);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(
                "{\"access_token\":\"token-123\",\"expires_in\":3600}"
                        .getBytes(StandardCharsets.UTF_8)));
        when(response.getEntity()).thenReturn(entity);
        doReturn(mock(CloseableHttpClient.class)).when(ansibleHttpRepo).getTokenClient();
        doReturn(response).when(ansibleHttpRepo)
                .doExecuteMethod(any(), any(), any(), any());

        OAuth2TokenModel result = ansibleHttpRepo.fetchAnsibleAuthToken(
                new HttpPost("https://sso.redhat.com/token"));

        assertNotNull(result);
        assertEquals("token-123", result.getAccessToken());
        assertEquals(3600, result.getExpiresIn());
    }

    @Test
    void fetchAnsibleAuthTokenReturnsNullOnFailedStatus() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(401);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(mock(HttpEntity.class));
        doReturn(mock(CloseableHttpClient.class)).when(ansibleHttpRepo).getTokenClient();
        doReturn(response).when(ansibleHttpRepo)
                .doExecuteMethod(any(), any(), any(), any());

        assertNull(ansibleHttpRepo.fetchAnsibleAuthToken(
                new HttpPost("https://sso.redhat.com/token")));
    }

    @Test
    void fetchAnsibleAuthTokenReturnsNullOnNullEntity() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(null);
        doReturn(mock(CloseableHttpClient.class)).when(ansibleHttpRepo).getTokenClient();
        doReturn(response).when(ansibleHttpRepo)
                .doExecuteMethod(any(), any(), any(), any());

        assertNull(ansibleHttpRepo.fetchAnsibleAuthToken(
                new HttpPost("https://sso.redhat.com/token")));
    }

    @Test
    void fetchAnsibleAuthTokenReturnsNullOnNullStatusLine() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(null);
        when(response.getEntity()).thenReturn(mock(HttpEntity.class));
        doReturn(mock(CloseableHttpClient.class)).when(ansibleHttpRepo).getTokenClient();
        doReturn(response).when(ansibleHttpRepo)
                .doExecuteMethod(any(), any(), any(), any());

        assertNull(ansibleHttpRepo.fetchAnsibleAuthToken(
                new HttpPost("https://sso.redhat.com/token")));
    }

    @Test
    void fetchAnsibleAuthTokenReturnsNullOnIOException() throws IOException {
        doReturn(mock(CloseableHttpClient.class)).when(ansibleHttpRepo).getTokenClient();
        doThrow(new IOException("Connection refused")).when(ansibleHttpRepo)
                .doExecuteMethod(any(), any(), any(), any());

        assertNull(ansibleHttpRepo.fetchAnsibleAuthToken(
                new HttpPost("https://sso.redhat.com/token")));
    }

    @Test
    void closeCleansUpTokenClient() {
        ansibleHttpRepo.init(false);
        ansibleHttpRepo.getTokenClient();
        ansibleHttpRepo.close();
    }

    @Test
    void destroyClosesTokenClient() {
        ansibleHttpRepo.init(false);
        ansibleHttpRepo.getTokenClient();
        ansibleHttpRepo.destroy();
    }
}
