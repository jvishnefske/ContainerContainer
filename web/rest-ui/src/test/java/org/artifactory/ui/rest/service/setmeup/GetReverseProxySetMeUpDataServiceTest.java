package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.setmeup.ReverseProxySetMeUpDataModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetReverseProxySetMeUpDataServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "repoKey";

    @InjectMocks
    private GetReverseProxySetMeUpDataService getReverseProxySetMeUpDataService;
    @Mock
    ArtifactoryContext artifactoryContext;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private CentralConfigService centralConfig;
    @Mock
    private ReverseProxyDescriptor reverseProxy;
    @Mock
    private ReverseProxyRepoConfig reverseProxyRepoConfig;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfig);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfig.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getCurrentReverseProxy()).thenReturn(reverseProxy);
        when(reverseProxy.getReverseProxyRepoConfig(REPO_KEY)).thenReturn(reverseProxyRepoConfig);
        when(reverseProxyRepoConfig.getPort()).thenReturn(8081);
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @ParameterizedTest
    @EnumSource(ReverseProxyMethod.class)
    void testExecute(ReverseProxyMethod reverseProxyMethod) {
        when(request.getQueryParamByKey("repoKey")).thenReturn(REPO_KEY);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(REPO_KEY)).thenReturn(true);
        when(reverseProxy.isUseHttps()).thenReturn(true);
        when(reverseProxy.getDockerReverseProxyMethod()).thenReturn(reverseProxyMethod);
        when(reverseProxy.getServerName()).thenReturn("serverName");
        ReverseProxyRepoConfig perRepo = mock(ReverseProxyRepoConfig.class);
        when(perRepo.getPort()).thenReturn(8080);
        ArtifactoryRestResponse<ReverseProxySetMeUpDataModel> response = new ArtifactoryRestResponse<>();
        getReverseProxySetMeUpDataService.execute(request, response);
        ReverseProxySetMeUpDataModel res = response.getIModel();
        Assertions.assertNotNull(res);
        Assertions.assertTrue(res.isUsingHttps());
        Assertions.assertEquals(reverseProxyMethod != ReverseProxyMethod.NOVALUE, res.isMethodSelected());
        Assertions.assertEquals("serverName", res.getServerName());
        Assertions.assertEquals(reverseProxyMethod.getVal(), res.getMethodType());
        if (reverseProxyMethod == ReverseProxyMethod.PORTPERREPO) {
            Assertions.assertTrue(res.isUsingPorts());
            Assertions.assertEquals(8081, res.getRepoPort());
        } else {
            Assertions.assertFalse(res.isUsingPorts());
        }
    }
}