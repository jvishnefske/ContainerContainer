package org.artifactory.ui.rest.service.admin.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.model.OAuthSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_INFO_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteOAuthProviderSettingsTest {

    @Mock
    private AccessService accessService;
    @Mock
    private UserGroupService userGroupService;
    private AutoCloseable mocks;
    private MockedStatic<ArtifactoryHome> homeMock;

    @InjectMocks
    DeleteOAuthProviderSettings deleter;
    @Mock
    private ArtifactoryHome home;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        homeMock = mockStatic(ArtifactoryHome.class);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
        homeMock.close();
    }

    @Test
    public void testExecute() {

        String name = "google central";
        String provider = "google";

        OAuthSetting google = OAuthSetting.builder().name(name).providerType(provider).useDefaultProxy(false).build();

        when(accessService.getOAuthSetting(name)).thenReturn(Optional.ofNullable(google));
        homeMock.when(ArtifactoryHome::get).thenReturn(home);

        RestResponse<Object> response = new ArtifactoryRestResponse<>();

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");

        ArtifactoryRestRequest<String> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder(servletRequest, null, null, null).model(name));

        deleter.execute(request, response);

        assertEquals(response.getResponseCode(), 200);
        assertTrue(response.getEntity().toString().contains("Successfully deleted OAuth provider google central"));
        Mockito.verify(accessService, Mockito.times(1)).getOAuthSetting(name);
        Mockito.verify(accessService, Mockito.times(1)).removeOAuthSetting(name);
        Mockito.verify(userGroupService, Mockito.times(1)).deletePropertyFromAllUsers(OAUTH_INFO_PROPERTY + name);
    }

    @Test
    public void testMissingProviderName() {
        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        ArtifactoryRestRequest<String> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, null, null, null).model(null));

        deleter.execute(request, response);

        assertEquals("Couldn't delete provider, missing provider name", response.getFeedbackMsg().getError());
    }

    @Test
    public void testProviderSettingsNotExist() {
        String providerName = "nonexistentProvider";

        when(accessService.getOAuthSetting(providerName)).thenReturn(Optional.empty());

        ArtifactoryRestResponse<Object> response = new ArtifactoryRestResponse<>();

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        ArtifactoryRestRequest<String> request = new ArtifactoryRestRequest<>(
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, null, null, null).model(providerName));

        deleter.execute(request, response);

        assertEquals("Couldn't delete OAuth provider, OAuth settings doesn't exist",
                response.getFeedbackMsg().getError());
    }
}
