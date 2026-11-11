package org.artifactory.security.authentication;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthErrorEnum;
import org.artifactory.security.props.auth.model.OauthErrorModel;
import org.artifactory.spring.InternalArtifactoryContext;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
@Test
public class GitHubEnterpriseAuthenticationProviderTest {

    @BeforeMethod
    private void setUp() {
        bindMocks();
    }

    public void testAuthenticateWithoutSuccess() {
        UserGroupService userGroupService = Mockito.mock(UserGroupService.class);
        AccessService accessService = Mockito.mock(AccessService.class);
        GitHubEnterpriseAuthenticationProvider provider =
                new GitHubEnterpriseAuthenticationProvider(null, userGroupService, accessService);
        assertNull(provider.authenticate(null));

        Authentication authentication = createAuthentication();
        bindRequest("really/docker/v2/token");
        bindOauth(accessService, true, "default");
        OAuthHandler oAuthHandler = Mockito.mock(OAuthHandler.class);
        Mockito.when(ContextHelper.get().beanForType(OAuthHandler.class)).thenReturn(oAuthHandler);
        OauthErrorModel errorModel = createErrorModel();
        Mockito.when(oAuthHandler.getCreateToken(Mockito.anyString(), Mockito.eq(null))).thenReturn(errorModel);
        assertNull(provider.authenticate(authentication));
    }

    public void testAuthenticateWithSuccess() {
        UserGroupService userGroupService = Mockito.mock(UserGroupService.class);
        AccessService accessService = Mockito.mock(AccessService.class);
        GitHubEnterpriseAuthenticationProvider provider =
                new GitHubEnterpriseAuthenticationProvider(null, userGroupService, accessService);

        Authentication authentication = createAuthentication();
        bindRequest("really/docker/v2/token");
        bindOauth(accessService, true, "default");
        OAuthHandler oAuthHandler = Mockito.mock(OAuthHandler.class);
        Mockito.when(ContextHelper.get().beanForType(OAuthHandler.class)).thenReturn(oAuthHandler);
        AuthenticationModel model = new AuthenticationModel("token", "at");

        UserInfo userInfo = Mockito.mock(UserInfo.class);
        Mockito.when(userGroupService
                .findOrCreateExternalAuthUser(Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyBoolean()))
                .thenReturn(userInfo);
        Mockito.when(oAuthHandler.getCreateToken(Mockito.anyString(), Mockito.eq(null)))
                .thenReturn(model);
        Authentication authenticate = provider.authenticate(authentication);
        assertNotNull(authenticate);
        assertTrue(authenticate instanceof OauthAuthentication);
    }

    public void testSupportsNullRequest() {
        RequestWrapper requestWrapper = Mockito.mock(RequestWrapper.class);
        AccessService accessService = Mockito.mock(AccessService.class);
        RequestThreadLocal.bind(requestWrapper);
        GitHubEnterpriseAuthenticationProvider provider =
                new GitHubEnterpriseAuthenticationProvider(null, null, accessService);
        assertFalse(provider.supports(null));
    }

    public void testSupportsNonV2Request() {
        bindRequest("really/not/docker");
        GitHubEnterpriseAuthenticationProvider provider = new GitHubEnterpriseAuthenticationProvider(null, null, null);
        assertFalse(provider.supports(null));
    }

    public void testSupportsV2RequestNoBasicAuth() {
        bindRequest("api/docker/v2/token");
        GitHubEnterpriseAuthenticationProvider provider = new GitHubEnterpriseAuthenticationProvider(null, null, null);
        assertFalse(provider.supports(null));
    }

    public void testSupportsV2RequestNoApiDocker() {
        bindRequest("really/docker/v2/token");
        GitHubEnterpriseAuthenticationProvider provider = new GitHubEnterpriseAuthenticationProvider(null, null, null);
        assertFalse(provider.supports(null));
    }

    public void testSupportsV2RequestNoOauthEnabled() {
        HttpServletRequest request = bindRequest("really/docker/v2/token");
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic auto");
        AccessService accessService = Mockito.mock(AccessService.class);

        GitHubEnterpriseAuthenticationProvider provider = new GitHubEnterpriseAuthenticationProvider(null, null, null);
        assertFalse(provider.supports(null));

        bindOauth(accessService, false, "");
        assertFalse(provider.supports(null));
    }

    public void testSupportsV2RequestOauthEnabled() {
        HttpServletRequest request = bindRequest("api/docker/v2/token");
        Mockito.when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic auto");
        AccessService accessService = Mockito.mock(AccessService.class);

        GitHubEnterpriseAuthenticationProvider provider =
                new GitHubEnterpriseAuthenticationProvider(null, null, accessService);
        assertFalse(provider.supports(null));

        bindOauth(accessService, true, "default");
        assertTrue(provider.supports(null));
    }

    public void testGetRealm() {
        GitHubEnterpriseAuthenticationProvider provider = new GitHubEnterpriseAuthenticationProvider(null, null, null);
        assertEquals(provider.getRealm(), OAuthHandler.REALM);
    }

    private void bindOauth(AccessService accessService, boolean isEnabled, String defaultProvider) {
        List<OAuthSetting> oAuthSettings = List.of(OAuthSetting.builder().name("default").build());
        OAuthGeneralSettings oAuthGeneralSettings =
                OAuthGeneralSettings.builder().enable(isEnabled).defaultSetting(defaultProvider).build();
        Mockito.when(accessService.getAllOAuthSettings()).thenReturn(oAuthSettings);
        Mockito.when(accessService.getOAuthGeneralSettings()).thenReturn(oAuthGeneralSettings);
        Mockito.when(accessService.isOAuthEnabled()).thenReturn(true);
    }

    private void bindMocks() {
        ArtifactoryContext context = Mockito.mock(InternalArtifactoryContext.class);
        CentralConfigService configService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor descriptor = Mockito.mock(CentralConfigDescriptor.class);
        Mockito.when(configService.getDescriptor()).thenReturn(descriptor);
        Mockito.when(context.getCentralConfig()).thenReturn(configService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @Nonnull
    private OauthErrorModel createErrorModel() {
        OauthErrorModel errorModel = new OauthErrorModel();
        errorModel.setInternalErrorMsg(OauthErrorEnum.BAD_REQUEST);
        errorModel.setStatusCode(400);
        return errorModel;
    }

    private HttpServletRequest bindRequest(String uri) {
        RequestWrapper requestWrapper = Mockito.mock(RequestWrapper.class);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(requestWrapper.getRequest()).thenReturn(request);
        Mockito.when(request.getRequestURI()).thenReturn(uri);
        RequestThreadLocal.bind(requestWrapper);
        return request;
    }

    @Nonnull
    private Authentication createAuthentication() {
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal()).thenReturn("test");
        Mockito.when(authentication.getCredentials()).thenReturn("test");
        return authentication;
    }

    @AfterMethod
    public void unbindRequest() {
        RequestThreadLocal.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }
}