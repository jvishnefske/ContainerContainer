package org.artifactory.ui.rest.service.admin.security.oauth;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.sso.CentralOAuthSSOAddon;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.descriptor.security.oauth.OAuthProviderSettings;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.security.oauth.CentralOAuthUIModel;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

public class GetCentralOAuthSettingsTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CentralOAuthSSOAddon centralOAuthSSOAddon;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testExecute() {
        OAuthProviderSettings google = new OAuthProviderSettings();
        google.setName("google central");

        OAuthProviderSettings github = new OAuthProviderSettings();
        github.setName("github central");

        CentralOAuthSettings config = new CentralOAuthSettings(true, List.of(google, github));

        Mockito.when(addonsManager.addonByType(CentralOAuthSSOAddon.class)).thenReturn(centralOAuthSSOAddon);
        Mockito.when(centralOAuthSSOAddon.getConfiguration()).thenReturn(config);

        RestResponse<CentralOAuthUIModel> response = new ArtifactoryRestResponse<>();

        GetCentralOAuthSettings getter = new GetCentralOAuthSettings(addonsManager);
        getter.execute(null, response);

        CentralOAuthUIModel expected = new CentralOAuthUIModel(List.of("google central", "github central"));
        Assert.assertEquals(expected, response.getIModel());
    }
}