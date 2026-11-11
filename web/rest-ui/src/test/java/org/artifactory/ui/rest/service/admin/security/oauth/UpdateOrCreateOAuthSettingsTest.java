package org.artifactory.ui.rest.service.admin.security.oauth;

import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.oauth.CentralOAuthUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIModel;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class UpdateOrCreateOAuthSettingsTest {

    @Mock
    private AccessService accessService;

    private AutoCloseable mocks;

    @InjectMocks
    UpdateOrCreateOAuthSettings updater;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testExecute() {

        String name = "google central";
        String provider = "google";

        OAuthSetting google = OAuthSetting.builder()
                .name(name)
                .providerType(provider)
                .build();

        when(accessService.getAllOAuthSettings()).thenReturn(List.of(google));

        RestResponse<CentralOAuthUIModel> response = new ArtifactoryRestResponse<>();

        HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");

        OAuthUIModel oAuthUIModel = new OAuthUIModel();
        oAuthUIModel.setEnabled(true);
        oAuthUIModel.setPersistUsers(true);
        oAuthUIModel.setAllowUserToAccessProfile(true);
        oAuthUIModel.setDefaultNpm("google central");
        oAuthUIModel.setProviders(List.of());

        ArtifactoryRestRequest<OAuthUIModel> request =
                new ArtifactoryRestRequest<>(new ArtifactoryRestRequest.RequestBuilder(servletRequest, null, null, null)
                        .model(oAuthUIModel));

        updater.execute(request, response);

        verify(accessService).upsertOAuthGeneralSetting(
                OAuthGeneralSettings.builder().defaultSetting("google central").enable(true).build());

        verify(accessService).upsertOAuthSettings(
                List.of(google.toBuilder().allowUserToAccessProfile(true).autoUserCreation(true).build()));

        assertEquals(response.getResponseCode(), 200);
    }
}
