package org.artifactory.rest.common.service;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.descriptor.repo.WebServerType;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyDescriptorModel;
import org.artifactory.rest.common.model.reverseproxy.ReverseProxyRepositories;
import org.artifactory.rest.common.service.admin.reverse.proxies.GetReverseProxiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.util.TomcatListeningDetector.SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetReverseProxiesServiceTest {

    private static final String REPO1_NAME = "repo1";
    private static final String REPO2_NAME = "repo2";


    private AutoCloseable autoCloseable;
    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private ArtifactoryRestRequest artifactoryRestRequest;

    @Mock
    private AolService aolService;

    private ReverseProxyDescriptor reverseProxyDescriptor;


    private static Stream<Arguments> provideParametersForTests() {
        return Stream.of(
                Arguments.of(false, false, 0),
                Arguments.of(false, true, 1),
                Arguments.of(true, true, 2));
    }

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(centralConfigService.getMutableDescriptor()).thenReturn(this.mutableCentralConfigDescriptor);

        when(this.artifactoryRestRequest.getServletRequest()).thenReturn(this.httpServletRequest);
        when(this.httpServletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL))
                .thenReturn("http://127.0.0.1");
        when(this.httpServletRequest.getHeader(ArtifactoryRequest.ARTIFACTORY_OVERRIDE_BASE_URL))
                .thenReturn("http://127.0.0.1");

        setArtifactoryContext();
        setArtifactoryHome();
        setDefaultValuesToReverseProxyDescriptor();
    }

    private void setArtifactoryContext() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    private void setArtifactoryHome() {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfigHelper sysConfigHelper = mock(SysConfigHelper.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        when(artifactoryHome.getSysConfig().helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(443);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getProperty(any())).thenReturn("0");
        when(sysConfig.getInt(SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT, -1)).thenReturn(8082);
        ArtifactoryHome.bind(artifactoryHome);
    }

    private void setDefaultValuesToReverseProxyDescriptor() {
        reverseProxyDescriptor = new ReverseProxyDescriptor();
        reverseProxyDescriptor.setWebServerType(WebServerType.NGINX);
        reverseProxyDescriptor.setUseHttps(false);
        reverseProxyDescriptor.setUseHttp(true);
        reverseProxyDescriptor.setKey("nginx");
        reverseProxyDescriptor.setServerName("test server name");
        reverseProxyDescriptor.setUpStreamName("artifactory");
        reverseProxyDescriptor.setHttpPort(8082);
        reverseProxyDescriptor.setHttpPort(443);
        reverseProxyDescriptor.setArtifactoryAppContext("artifactory");
        reverseProxyDescriptor.setDockerReverseProxyMethod(ReverseProxyMethod.SUBDOMAIN);
        reverseProxyDescriptor.setServerNameExpression("expression");
        reverseProxyDescriptor.setSslCertificate("certificate");
        reverseProxyDescriptor.setSslKey("ssl key");
        List<ReverseProxyRepoConfig> reverseProxyRepoConfigs = new ArrayList<>();
        ReverseProxyRepoConfig reverseProxyRepoConfig1 = new ReverseProxyRepoConfig();
        RepoBaseDescriptor repoRef1 = new HttpRepoDescriptor();
        repoRef1.setKey(REPO1_NAME);
        reverseProxyRepoConfig1.setRepoRef(repoRef1.getKey());
        reverseProxyRepoConfig1.setServerName("server1");
        reverseProxyRepoConfig1.setPort(8083);

        ReverseProxyRepoConfig reverseProxyRepoConfig2 = new ReverseProxyRepoConfig();
        RepoBaseDescriptor repoRef2 = new HttpRepoDescriptor();
        repoRef2.setKey(REPO2_NAME);
        reverseProxyRepoConfig2.setRepoRef(repoRef2.getKey());
        reverseProxyRepoConfig2.setServerName("server2");
        reverseProxyRepoConfig2.setPort(8083);

        reverseProxyRepoConfigs.add(reverseProxyRepoConfig1);
        reverseProxyRepoConfigs.add(reverseProxyRepoConfig2);
        reverseProxyDescriptor.setReverseProxyRepoConfigs(reverseProxyRepoConfigs);
    }

    @ParameterizedTest
    @MethodSource("provideParametersForTests")
    void getProxiesTest(boolean isFirstRepoAvailableForUser, boolean isSecondRepoAvailableForUser,
            int sizeOfAvailableRepos) {
        // set to user admin
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAdmin()).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO1_NAME)).
                thenReturn(isFirstRepoAvailableForUser);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO2_NAME)).
                thenReturn(isSecondRepoAvailableForUser);


        GetReverseProxiesService getReverseProxiesService =
                new GetReverseProxiesService(centralConfigService, authorizationService, securityService);

        when(this.mutableCentralConfigDescriptor.getCurrentReverseProxy()).thenReturn(reverseProxyDescriptor);

        ArtifactoryRestResponse artifactoryRestResponse = new ArtifactoryRestResponse();
        getReverseProxiesService.execute(artifactoryRestRequest, artifactoryRestResponse);

        ReverseProxyDescriptorModel reverseProxyDescriptorModel =
                (ReverseProxyDescriptorModel) artifactoryRestResponse.getIModel();

        ReverseProxyRepositories reverseProxyRepositories = reverseProxyDescriptorModel.
                getReverseProxyRepositories();

        int numberOfReverseProxies = reverseProxyRepositories == null ? 0 :
                reverseProxyDescriptorModel.getReverseProxyRepositories()
                        .getReverseProxyRepoConfigs().size();

        Assertions.assertEquals(numberOfReverseProxies, sizeOfAvailableRepos);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        if (autoCloseable != null) {
            autoCloseable.close();
            autoCloseable = null;
        }
    }

}
