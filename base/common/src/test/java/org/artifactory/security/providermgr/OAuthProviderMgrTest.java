package org.artifactory.security.providermgr;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.oauth.OAuthSettings;
import org.artifactory.security.LoginHandler;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthErrorEnum;
import org.artifactory.security.props.auth.model.OauthErrorModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Alexei Vainshtein
 */
public class OAuthProviderMgrTest extends ArtifactoryHomeBoundTest {

    private ArtifactoryContext context;
    private LoginHandler loginHandler;

    @BeforeMethod
    public void setup() {
        context = Mockito.mock(ArtifactoryContext.class);
        loginHandler = Mockito.mock(LoginHandler.class);
        Mockito.when(context.beanForType(LoginHandler.class)).thenReturn(loginHandler);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testFetchAndStoreTokenFromProvider() {
        AuthenticationModel model = new AuthenticationModel();
        TokenManagerKey tokenManagerKey = new TokenManagerKey("", TokenType.CONAN);
        model.setToken("token");
        Mockito.when(loginHandler.doBasicAuthWithDb(tokenManagerKey)).thenReturn(model);
        OAuthProviderMgr oAuthProviderMgr = new OAuthProviderMgr();
        OauthModel oauthModel = oAuthProviderMgr.fetchAndStoreTokenFromProvider(tokenManagerKey);
        Assert.assertNotNull(oauthModel);
        Assert.assertTrue(oauthModel instanceof AuthenticationModel);
        Assert.assertEquals(((AuthenticationModel) oauthModel).getToken(), model.getToken());
    }

    @Test
    public void testFetchAndStoreTokenFromProviderWithException() {
        preparePreConditionalMocks();
        OAuthProviderMgr oAuthProviderMgr = new OAuthProviderMgr();
        OauthModel oauthModel =
                oAuthProviderMgr.fetchAndStoreTokenFromProvider(new TokenManagerKey("", TokenType.CONAN));
        Assert.assertNotNull(oauthModel);
        Assert.assertTrue(oauthModel instanceof OauthErrorModel);
        Assert.assertEquals(((OauthErrorModel) oauthModel).getStatusCode(), HttpServletResponse.SC_UNAUTHORIZED);
        Assert.assertEquals(((OauthErrorModel) oauthModel).getInternalErrorMsg(), OauthErrorEnum.BAD_CREDENTIAL);
    }

    private void preparePreConditionalMocks() {
        CentralConfigService service = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor descriptor = Mockito.mock(CentralConfigDescriptor.class);
        Mockito.when(service.getDescriptor()).thenReturn(descriptor);
        SecurityDescriptor securityDescriptor = Mockito.mock(SecurityDescriptor.class);
        Mockito.when(descriptor.getSecurity()).thenReturn(securityDescriptor);
        OAuthSettings settings = Mockito.mock(OAuthSettings.class);
        Mockito.when(settings.getEnableIntegration()).thenReturn(false);
        Mockito.when(context.getCentralConfig()).thenReturn(service);
        Mockito.when(loginHandler.doBasicAuthWithDb(new TokenManagerKey("", TokenType.CONAN)))
                .thenThrow(new RuntimeException());
    }
}