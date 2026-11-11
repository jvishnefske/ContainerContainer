package org.artifactory.rest.resource.system;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.RepoBulkRemovalReport;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.BaseUrlModel;
import org.artifactory.api.config.MailServerModel;
import org.artifactory.api.config.ProxiesListModel;
import org.artifactory.api.config.ProxyModel;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.CentralConfigServiceImpl;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.utils.AuthenticationUtils;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.storage.db.security.service.VersioningCacheImpl;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.CollectionUtils;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.common.YamlUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Slf4j
public class ConfigResourceTest extends ArtifactoryHomeBoundTest {
    @Mock
    protected CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor descriptor;
    @Mock
    private MutableCentralConfigDescriptor mutableDescriptor;
    @Mock
    protected ArtifactoryContext context;
    @Mock
    protected AolService aolService;
    @Mock
    protected RepositoryService repositoryService;
    @Mock
    protected PlatformConfigService platformConfigService;
    @Mock
    protected AccessService accessService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    HttpServletRequest servletRequest;
    @Mock
    MailServerDescriptor mailServerDescriptorDescriptor;
    @Mock
    SecurityService securityService;

    private AutoCloseable autoCloseable;
    private ConfigResource configResource;
    private MockedStatic<AuthenticationUtils> authenticationUtils;

    @BeforeMethod
    protected void setup() {
        this.autoCloseable = MockitoAnnotations.openMocks(this);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        this.configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        this.authenticationUtils = mockStatic(AuthenticationUtils.class);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryService);
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.FALSE.toString());
    }

    @AfterMethod
    protected void cleanup() throws Exception {
        this.autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
        authenticationUtils.close();
    }

    @Test
    public void testIsSaas() {
        this.createAolMock(true, false);
        assertTrue(this.configResource.isSaas().isSaas());
    }

    @Test
    public void testIsNotSaas() {
        this.createAolMock(false, false);
        assertFalse(this.configResource.isSaas().isSaas());
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testGetResourceAolNotDashboardUser() {
        this.createAolMock(true, false);
        this.configResource.getConfig();
    }

    @Test(dataProvider = "configure")
    public void testGetResourceAolWithBasicAuthDashboardUser(boolean isAol, boolean isDashboardUser) {
        this.createAolMock(isAol, isDashboardUser);
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        when(mutableDescriptor.getMailServer()).thenReturn(mailServerDescriptorDescriptor);

        assertNotNull(this.configResource.getConfig());
    }

    @Test(dataProvider = "configure")
    public void testGetResourceAolWithTokenAuthDashboardUser(boolean isAol, boolean isDashboardUser) {
        this.createAolMock(isAol, isDashboardUser);
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(true);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);

        Assert.assertNotNull(this.configResource.getConfig());
    }

    @DataProvider
    public Object[][] configure() {
        return new Object[][] {
                {true, true},
                {false, false}
        };
    }

    private void createAolMock(boolean isAol, boolean isDashboardUser) {
        when(this.aolService.isDashboardUser()).thenReturn(isDashboardUser);
        when(this.aolService.isAol()).thenReturn(isAol);
        when(this.context.beanForType(AolService.class)).thenReturn(this.aolService);
    }

    private void createAolMock() {
        when(this.aolService.isDashboardUser()).thenReturn(true);
        when(this.aolService.isAol()).thenReturn(true);
        when(this.context.beanForType(AolService.class)).thenReturn(this.aolService);
    }

    @Test(expectedExceptions = BadRequestException.class,
            expectedExceptionsMessageRegExp = ".*not supported for SaaS.*")
    public void testSetUrlBaseAolNotDashboardUser() {
        // Arrange
        createAolMock(true, false);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(securityService.hasMcScope()).thenReturn(false);
        getBound().setProperty(ConstantValues.allowAolBaseUrlChange.getPropertyName(), Boolean.FALSE.toString());

        // Act
        configResource.setUrlBase("https://example.com");
    }

    @Test
    public void testSetUrlBaseAolDashboardUser() {
        // Arrange
        createAolMock(true, true);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        when(mutableDescriptor.getUrlBase()).thenReturn("https://example.com");
        getBound().setProperty(ConstantValues.allowAolBaseUrlChange.getPropertyName(), Boolean.FALSE.toString());

        // Act
        Response response = configResource.setUrlBase("https://example.com");

        // Assert
        assertEquals(response.getStatus(), SC_OK);
    }

    @Test
    public void testSetConfigAolDashboardUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(true);
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        configResource.setConfig("Some config");
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testSetConfigAolNotDashboardUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        configResource.setConfig("Some config");
    }

    @Test
    public void testSetConfigAolHaUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        configResource.setConfig("Some config");
    }

    @Test
    public void testPatchConfigAolDashboardUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(true);
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        Map<Object, Object> data = ImmutableMap.builder()
                .put("indexer",
                        ImmutableMap.of(
                                "enabled", "false",
                                "cronExp", "0 0 0 0 0",
                                "includedRepositories", ImmutableList.of("repokey20", "repokey21"))
                )
                .build();
        configResource.patchConfig(YamlUtils.getInstance().writeValueAsString(data));
    }

    @Test
    public void testPatchConfigAolHaUser() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        Map<Object, Object> data = ImmutableMap.builder()
                .put("indexer",
                        ImmutableMap.of(
                                "enabled", "false",
                                "cronExp", "0 0 0 0 0",
                                "includedRepositories", ImmutableList.of("repokey20", "repokey21"))
                )
                .build();
        configResource.patchConfig(YamlUtils.getInstance().writeValueAsString(data));
    }

    @Test
    public void testPatchConfigUserLockPolicy() {
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        Map<Object, Object> data = ImmutableMap.builder()
                .put("security", ImmutableMap.of("userLockPolicy",
                        ImmutableMap.of(
                                "enabled", true,
                                "loginAttempts", 3))
                )
                .build();
        configResource.patchConfig(YamlUtils.getInstance().writeValueAsString(data));
    }

    @Test
    public void testPatchConfigInsertLocalDescriptor() {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);

        Map<Object, Object> data = ImmutableMap.builder()
                .put("localRepositories",
                        Map.of("example-repo-local", Map.of(
                                "type", "generic",
                                "description", "Example artifactory repository",
                                "repoLayoutRef", "simple-default",
                                "handleReleases", "true",
                                "handleSnapshots", "false",
                                "suppressPomConsistencyChecks", "true")
                        )
                ).build();
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());

        configResource.patchConfig(YamlUtils.getInstance().writeValueAsString(data));

        verify(repositoryService).bulkUpsertRepoConfigs(anyCollection(), eq(true));
    }

    @Test
    public void testPatchConfigDeleteLocalDescriptor() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.FALSE.toString());
        String repoKey = "example-repo-local";

        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);

        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);

        String yamlBody = """
                    localRepositories:
                        %s: null
                """.formatted(repoKey);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(repositoryService.getRepoConfigsByKeys(Set.of(repoKey))).thenReturn(
                Set.of(LocalRepoConfig.create(repoKey, Generic)));

        configResource.patchConfig(yamlBody);

        verify(repositoryService).bulkDeleteRepoConfigs(List.of(repoKey), true, true);
    }

    @Test
    public void testPatchConfigDeleteV2LocalDescriptor() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(), Boolean.TRUE.toString());
        String repoKey = "example-repo-local";

        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(repositoryService.removeRepositories(CollectionUtils.newLinkedHashSet(repoKey))).thenReturn(
                new RepoBulkRemovalReport(Set.of(new RepoRemovalStatusHolder())));
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);

        String yamlBody = """
                    localRepositories:
                        %s: null
                """.formatted(repoKey);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(repositoryService.getRepoConfigsByKeys(Set.of(repoKey))).thenReturn(
                Set.of(LocalRepoConfig.create(repoKey, Generic)));

        configResource.patchConfig(yamlBody);

        verify(repositoryService).removeRepositories(CollectionUtils.newLinkedHashSet(repoKey));
    }

    @Test
    public void testPatchConfigDeleteV2LocalDescriptor_FailedToDelete() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(), Boolean.TRUE.toString());
        String repoKey = "example-repo-local";

        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(false);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
        statusHolder.error("Failed to delete", SC_BAD_REQUEST,
                org.slf4j.LoggerFactory.getLogger(ConfigResourceTest.class));
        when(repositoryService.removeRepositories(CollectionUtils.newLinkedHashSet(repoKey))).thenReturn(
                new RepoBulkRemovalReport(Set.of(statusHolder)));
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);

        String yamlBody = """
                    localRepositories:
                        %s: null
                """.formatted(repoKey);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(new CentralConfigDescriptorImpl());
        when(repositoryService.getRepoConfigsByKeys(Set.of(repoKey))).thenReturn(
                Set.of(LocalRepoConfig.create(repoKey, Generic)));

        try (Response response = configResource.patchConfig(yamlBody)) {
            assertEquals(response.getStatus(), SC_OK);
            assertEquals(response.getEntity().toString(), "0 changes to config merged successfully");
        }
    }

    @Test(expectedExceptions = BadRequestException.class, expectedExceptionsMessageRegExp = ".*Unicode is not " +
            "supported")
    public void testInvalidPatchMailServerConfig() {
        CentralConfigServiceImpl configService = getCentralConfigService();
        ConfigResource configResource =
                new ConfigResource(configService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        Map<Object, Object> data = ImmutableMap.builder()
                .put("mailServer", ImmutableMap.of(
                                "enabled", true,
                                "from", "test@jfrog.com",
                                "host", "host",
                                "username", "invalid\u0007username",
                                "password", "password",
                                "port", "55",
                                "subjectPrefix", "JFrog",
                                "ssl", false,
                                "tls", false
                        )
                )
                .build();
        configResource.patchConfig(YamlUtils.getInstance().writeValueAsString(data));
    }

    @Nonnull
    private static CentralConfigServiceImpl getCentralConfigService() {
        CentralConfigServiceImpl centralConfigService = new CentralConfigServiceImpl();
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        VersioningCacheImpl<CentralConfigServiceImpl.CentralConfigDescriptorCache> cache =
                new VersioningCacheImpl<>(3000,
                        () -> new CentralConfigServiceImpl.CentralConfigDescriptorCache(centralConfigDescriptor,
                                centralConfigDescriptor.getServerName()), "config-descriptor-cache");
        ReflectionTestUtils.setField(centralConfigService, "descriptorCache", cache);
        ReflectionTestUtils.setField(centralConfigService, "configValidationService",
                new ConfigValidationService());
        return centralConfigService;
    }

    @Test(dataProvider = "mailServerConfigurationProvider")
    public void testGetMailServer(boolean isAol, boolean isXrayTokenAuthentication, String expectedServerHost) {
        ConfigResource configResource =
                new ConfigResource(centralConfigService, servletRequest, aolService, addonsManager, accessService,
                        repositoryService, platformConfigService);
        mockXrayTokenAuthentication(isXrayTokenAuthentication);
        MailServerDescriptor mailServer = new MailServerDescriptor();
        mailServer.setHost("SMTP host");
        when(descriptor.getMailServer()).thenReturn(mailServer);
        this.createAolMock(isAol, false);
        var response = configResource.getMailServer();
        assertEquals(response.getHost(), expectedServerHost);
    }

    private void mockXrayTokenAuthentication(boolean isXrayTokenAuthentication) {
        if (isXrayTokenAuthentication) {
            AccessTokenAuthentication authentication = Mockito.mock(AccessTokenAuthentication.class);
            JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
            when(authentication.getAccessToken()).thenReturn(jwtAccessToken);
            when(jwtAccessToken.getSubject()).thenReturn(ServiceType.XRAY + "@");
            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);
            authenticationUtils.when(() -> AuthenticationUtils.isXrayTokenAuthentication(authentication))
                    .thenReturn(true);
        }
    }

    @DataProvider
    public static Object[][] mailServerConfigurationProvider() {
        return new Object[][] {
                // Aol
                // Should not return SMTP host
                {true, false, null},
                // Should return SMTP host for Xray token
                {true, true, "SMTP host"},
                // on prem
                // Should return SMTP host
                {false, false, "SMTP host"},
                {false, true, "SMTP host"},
        };
    }

    @Test
    public void getConfigBasedOnTokenShouldReturnMailServerAndProxyPasswords() {
        this.createAolMock();
        MailServerDescriptor mailServerDescriptor = mailServerDescriptor();
        ProxyDescriptor proxyDescriptor = proxyDescriptor();
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(true);
        when(descriptor.getMailServer()).thenReturn(mailServerDescriptor);
        when(descriptor.getProxies()).thenReturn(ImmutableList.of(proxyDescriptor));

        CentralConfigDescriptor config = configResource.getConfig();

        assertNotNull(config);
        assertNotNull(config.getMailServer());
        assertEquals(config.getMailServer().getPassword(), mailServerDescriptor.getPassword());
        config.getProxies()
                .forEach(descriptor -> assertEquals(descriptor.getPassword(), proxyDescriptor.getPassword()));
    }

    @Test
    public void getConfigBasedOnBasicAuthShouldNotReturnMailServerAndProxyPasswords() {
        this.createAolMock();
        MailServerDescriptor mailServerDescriptor = mailServerDescriptor();
        ImmutableList<ProxyDescriptor> proxyDescriptors = ImmutableList.of(proxyDescriptor());
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(descriptor.getMailServer()).thenReturn(mailServerDescriptor);
        when(descriptor.getProxies()).thenReturn(proxyDescriptors);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        when(mutableDescriptor.getMailServer()).thenReturn(mailServerDescriptor);
        when(mutableDescriptor.getProxies()).thenReturn(proxyDescriptors);

        CentralConfigDescriptor config = configResource.getConfig();

        assertNotNull(configResource.getConfig());
        assertNull(config.getMailServer().getPassword());
        assertFalse(config.getProxies().isEmpty());
        config.getProxies().forEach(proxyDescriptor -> assertNull(proxyDescriptor.getPassword()));
    }

    @Test
    public void getConfigBasedOnBasicAuthWhenProxyNotConfigured() {
        this.createAolMock();
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(descriptor.getMailServer()).thenReturn(mailServerDescriptor());
        when(descriptor.getProxies()).thenReturn(List.of());
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        when(mutableDescriptor.getMailServer()).thenReturn(mailServerDescriptor());
        when(mutableDescriptor.getProxies()).thenReturn(List.of());

        CentralConfigDescriptor config = configResource.getConfig();

        assertNotNull(config);
        assertNotNull(config.getMailServer());
        assertNull(config.getMailServer().getPassword());
        assertTrue(config.getProxies().isEmpty());
    }

    @Test
    public void getConfigBasedOnBasicAuthWhenMailServerNotConfigured() {
        this.createAolMock();
        ImmutableList<ProxyDescriptor> proxyDescriptors = ImmutableList.of(proxyDescriptor());
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(descriptor.getMailServer()).thenReturn(null);
        when(descriptor.getProxies()).thenReturn(proxyDescriptors);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        when(mutableDescriptor.getMailServer()).thenReturn(null);
        when(mutableDescriptor.getProxies()).thenReturn(proxyDescriptors);

        CentralConfigDescriptor config = configResource.getConfig();

        assertNotNull(config);
        assertNull(config.getMailServer());
        config.getProxies().forEach(proxyDescriptor -> assertNull(proxyDescriptor.getPassword()));
    }

    @Test
    public void getConfigWhenMailServerAndProxyNotConfiguredShouldReturnDescriptor() {
        this.createAolMock();
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(true);
        when(descriptor.getMailServer()).thenReturn(null);
        when(descriptor.getProxies()).thenReturn(List.of());

        CentralConfigDescriptor config = configResource.getConfig();

        assertNotNull(config);
        assertNull(config.getMailServer());
        assertTrue(config.getProxies().isEmpty());
    }

    @Test
    public void getPlatformProxiesBasedOnTokenShouldReturnPassword() {
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(true);
        when(descriptor.getProxies()).thenReturn(ImmutableList.of(proxyDescriptor()));

        ProxiesListModel platformProxies = configResource.getPlatformProxies();
        platformProxies.getProxies().forEach(proxy -> assertNotNull(proxy.getPassword()));
    }

    @Test
    public void getPlatformProxiesBasedOnBasicAuthShouldNotReturnPassword() {
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(descriptor.getProxies()).thenReturn(ImmutableList.of(proxyDescriptor()));

        ProxiesListModel platformProxies = configResource.getPlatformProxies();
        platformProxies.getProxies().forEach(proxy -> assertNull(proxy.getPassword()));
    }

    @Test
    public void getServiceProxyBasedOnTokenShouldReturnPassword() {
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(true);
        when(centralConfigService.getServiceProxyByKey("serviceKey")).thenReturn(proxyDescriptor());

        ProxyModel serviceProxy = configResource.getServiceProxy("serviceKey");
        assertNotNull(serviceProxy.getPassword());
    }

    @Test
    public void getServiceProxyBasedOnBasicAuthShouldNotReturnPassword() {
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(centralConfigService.getServiceProxyByKey("serviceKey")).thenReturn(proxyDescriptor());

        ProxyModel serviceProxy = configResource.getServiceProxy("serviceKey");
        assertNull(serviceProxy.getPassword());
    }

    @Test(expectedExceptions = NotFoundException.class, expectedExceptionsMessageRegExp = "Proxy configuration for " +
            "specified service 'serviceKey' not found")
    public void getServiceProxyShouldThrowNotFound() {
        when(centralConfigService.getServiceProxyByKey("serviceKey")).thenReturn(null);

        configResource.getServiceProxy("serviceKey");
    }

    @Test
    public void getMailServerBasedOnTokenAuthShouldNotReturnMailServerPassword() {
        this.createAolMock();
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(false);
        when(descriptor.getMailServer()).thenReturn(mailServerDescriptor());

        MailServerModel mailServer = configResource.getMailServer();
        assertNull((mailServer.getPassword()));
    }

    @Test
    public void getMailServerBasedOnTokenAuthShouldReturnMailServerPassword() {
        this.createAolMock();
        authenticationUtils.when(AuthenticationUtils::isAccessTokenAuthentication).thenReturn(true);
        when(descriptor.getMailServer()).thenReturn(mailServerDescriptorDescriptor);
        when(descriptor.getMailServer()).thenReturn(mailServerDescriptor());

        MailServerModel mailServer = configResource.getMailServer();
        assertNotNull((mailServer.getPassword()));
    }

    @Test
    public void getFederatedUrl_whenBaseUrlIsNull_returnsNullBaseUrl() {
        when(descriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn(null);
        BaseUrlModel result = configResource.getFederatedUrl();
        assertNull(result.getBaseUrl());
    }

    @DataProvider
    public Object[][] federatedUrlProvider() {
        return new Object[][] {
                {"http://example.com", "http://example.com/artifactory"},
                {"http://example.com/artifactory", "http://example.com/artifactory"},
                {"http://example.com/artifactory/", "http://example.com/artifactory"},
        };
    }

    @Test(dataProvider = "federatedUrlProvider")
    public void getFederatedUrl_whenBaseUrlIsSet_returnsUrlWithArtifactorySuffix(
            String input, String expected) {
        when(descriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn(input);
        BaseUrlModel result = configResource.getFederatedUrl();
        assertEquals(result.getBaseUrl(), expected);
    }

    private ProxyDescriptor proxyDescriptor() {
        ProxyDescriptor proxyDescriptor = new ProxyDescriptor();
        proxyDescriptor.setKey("key");
        proxyDescriptor.setHost("host");
        proxyDescriptor.setPort(8080);
        proxyDescriptor.setUsername("username");
        proxyDescriptor.setPassword("password");
        proxyDescriptor.setNtHost("ntHost");
        proxyDescriptor.setDomain("domain");
        proxyDescriptor.setPlatformDefault(true);
        proxyDescriptor.setRedirectedToHosts("redirectedToHosts");
        proxyDescriptor.setServices("services");
        return proxyDescriptor;
    }

    private MailServerDescriptor mailServerDescriptor() {
        MailServerDescriptor mailServerDescriptor = new MailServerDescriptor();
        mailServerDescriptor.setHost("host");
        mailServerDescriptor.setPort(8080);
        mailServerDescriptor.setUsername("username");
        mailServerDescriptor.setPassword("password");
        mailServerDescriptor.setFrom("from");
        mailServerDescriptor.setSubjectPrefix("subjectPrefix");
        mailServerDescriptor.setArtifactoryUrl("http://artifactory.com");
        mailServerDescriptor.setTls(true);
        mailServerDescriptor.setSsl(true);
        return mailServerDescriptor;
    }
}