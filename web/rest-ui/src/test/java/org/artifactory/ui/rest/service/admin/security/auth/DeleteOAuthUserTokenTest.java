package org.artifactory.ui.rest.service.admin.security.auth;

import org.artifactory.model.security.UserImpl;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUserToken;
import org.artifactory.ui.rest.service.admin.security.oauth.DeleteOAuthUserToken;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Test
public class DeleteOAuthUserTokenTest {

    private DeleteOAuthUserToken deleteOAuthUserToken = new DeleteOAuthUserToken();
    private AutoCloseable mockitoClosable;
    @Mock
    private ArtifactoryRestRequest requestMock;
    @Mock
    private HttpServletRequest servletRequestMock;
    @Mock
    private ArtifactoryRestResponse responseMock;
    @Mock
    private InternalSecurityService internalSecurityService;

    @BeforeMethod
    public void setUp() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(deleteOAuthUserToken, "internalSecurityService", internalSecurityService);
        OAuthUserToken user = new OAuthUserToken("myuser", "provider");
        when(requestMock.getImodel()).thenReturn(user);
        when(requestMock.getServletRequest()).thenReturn(servletRequestMock);
    }

    @Test
    public void deleteUserForAdmin() {
        when(internalSecurityService.isAdmin()).thenReturn(true);
        deleteOAuthUserToken.execute(requestMock, responseMock);
        verify(internalSecurityService, times(1))
                .deleteUserProperty("myuser", OAUTH_INFO_PROPERTY + "provider");
    }

    public void deleteUserForNonAdmin() {
        when(internalSecurityService.isAdmin()).thenReturn(false);
        when(internalSecurityService.currentUser()).thenReturn(new UserImpl("myuser"));
        deleteOAuthUserToken.execute(requestMock, responseMock);
        verify(internalSecurityService, times(1))
                .deleteUserProperty("myuser", OAUTH_INFO_PROPERTY + "provider");
    }

    public void deleteNotPerformedUserForNonAdmin() {
        when(internalSecurityService.isAdmin()).thenReturn(false);
        when(internalSecurityService.currentUser()).thenReturn(new UserImpl("myuser1"));
        deleteOAuthUserToken.execute(requestMock, responseMock);
        verify(internalSecurityService, times(0))
                .deleteUserProperty("myuser1", OAUTH_INFO_PROPERTY + "provider");
    }
}
