package org.artifactory.ui.rest.service.admin.security.oauth;

import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.oauth.CentralOAuthUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthProviderUIModel;
import org.artifactory.ui.rest.model.admin.security.oauth.OAuthUIModel;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class GetOAuthSettingsTest {

    @Mock
    private AccessService accessService;
    private AutoCloseable mocks;

    @InjectMocks
    GetOAuthSettings getter;

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

        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());
        when(accessService.getAllOAuthSettings()).thenReturn(List.of(google));

        RestResponse<CentralOAuthUIModel> response = new ArtifactoryRestResponse<>();

        getter.execute(null, response);

        OAuthProviderUIModel oAuthProviderUIModel = ((OAuthUIModel) response.getIModel()).getProviders().get(0);
        assertEquals(oAuthProviderUIModel.getName(), name);
        assertEquals(oAuthProviderUIModel.getProviderType(), provider);
    }

    @Test
    public void testExecuteWithCustomProviderType() {
        String name = "custom-provider";
        String providerType = "ayush";

        OAuthSetting customProvider = OAuthSetting.builder()
                .name(name)
                .providerType(providerType)
                .build();

        when(accessService.getOAuthGeneralSettings()).thenReturn(OAuthGeneralSettings.builder().build());
        when(accessService.getAllOAuthSettings()).thenReturn(List.of(customProvider));

        RestResponse<CentralOAuthUIModel> response = new ArtifactoryRestResponse<>();

        getter.execute(null, response);

        OAuthProviderUIModel oAuthProviderUIModel = ((OAuthUIModel) response.getIModel()).getProviders().get(0);
        assertEquals(name, oAuthProviderUIModel.getName());
        assertEquals(providerType, oAuthProviderUIModel.getProviderType());
    }
}
