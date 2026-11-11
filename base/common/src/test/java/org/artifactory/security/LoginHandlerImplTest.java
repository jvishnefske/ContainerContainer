package org.artifactory.security;

import jakarta.annotation.Nonnull;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;

/**
 * @author Yuval Reches
 */
public class LoginHandlerImplTest extends ArtifactoryHomeBoundTest {

    private final String USERNAME = "test";
    private final String TOKEN = "token";

    @Mock
    private ArtifactoryContext artifactoryContextMock;
    @Mock
    private OauthManager oauthManager;

    private LoginHandlerImpl loginHandler;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        loginHandler = Mockito.spy(new LoginHandlerImpl(oauthManager));
        ArtifactoryContextThreadBinder.bind(artifactoryContextMock);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testDoBasicAuth() {
        TokenManagerKey tokenManagerKey = new TokenManagerKey(USERNAME,TokenType.CONAN);
        TokenKeyValue tokenKeyValue = getTokenKeyValue();
        when(oauthManager.getToken(tokenManagerKey)).thenReturn(tokenKeyValue);
        OauthModel oauthModel = loginHandler.doBasicAuthWithDb(tokenManagerKey);
        assertAuthenticationModel(oauthModel);
    }

    @Test
    public void testGetOrCreateOauth2Token() {
        TokenManagerKey tokenManagerKey = new TokenManagerKey(USERNAME, TokenType.TERRAFORM);
        TokenKeyValue tokenKeyValue = getTokenKeyValue();
        when(oauthManager.getToken(tokenManagerKey)).thenReturn(tokenKeyValue);
        OauthModel oauthModel = loginHandler.getOrCreateOauth2Token(tokenManagerKey);
        assertOAuth2TokenModel(oauthModel);
    }

    @Nonnull
    private TokenKeyValue getTokenKeyValue() {
        return new TokenKeyValue("key", TOKEN);
    }

    private void assertAuthenticationModel(OauthModel oauthModel) {
        Assert.assertTrue(oauthModel instanceof AuthenticationModel);
        AuthenticationModel authenticationModel = (AuthenticationModel) oauthModel;
        Assert.assertEquals(authenticationModel.getToken(), TOKEN);
    }

    private void assertOAuth2TokenModel(OauthModel oauthModel) {
        Assert.assertTrue(oauthModel instanceof OAuth2TokenModel);
        OAuth2TokenModel authenticationModel = (OAuth2TokenModel) oauthModel;
        Assert.assertEquals(authenticationModel.getAccessToken(), TOKEN);
    }
}