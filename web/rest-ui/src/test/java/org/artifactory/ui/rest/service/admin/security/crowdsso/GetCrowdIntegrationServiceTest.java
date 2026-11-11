package org.artifactory.ui.rest.service.admin.security.crowdsso;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.sso.crowd.CrowdAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdIntegration;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.model.CrowdSetting;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetCrowdIntegrationServiceTest {

    @Mock
    private CrowdAddon crowdAddon;
    @Mock
    CentralConfigService centralConfigService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    MutableCentralConfigDescriptor centralConfigDescriptor;
    @Mock
    SecurityDescriptor securityDescriptor;
    @Mock
    AccessService accessService;
    @Mock
    AuthSettingsClient authSettingsClient;

    private GetCrowdIntegrationService crowdIntegrationService;
    private AutoCloseable mocks;

    @Test
    public void getCrowdFromKeyWhenConfiguredAndCrowdSettingNotNull() {
        String customKey = "customKey";
        when(crowdAddon.getCrowdCustomTokenKey()).thenReturn(customKey);
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        when(authSettingsClient.getCrowdSetting(false)).thenReturn(Optional.of(CrowdSetting.builder().build()));
        RestResponse<CrowdIntegration> response = new ArtifactoryRestResponse<>();

        crowdIntegrationService.execute(mock(ArtifactoryRestRequest.class), response);
        CrowdIntegration crowdIntegration = response.getIModel();
        Assert.assertEquals(crowdIntegration.getCustomCookieTokenKey(), customKey);
        verify(addonsManager, times(1)).addonByType(CrowdAddon.class);
        verify(crowdAddon, times(1)).getCrowdCustomTokenKey();
        verify(accessService, times(1)).getAccessAuthSettingsClient();
        verify(authSettingsClient, times(1)).getCrowdSetting(false);
    }

    @Test
    public void getCrowdFromKeyWhenConfigured() {
        String customKey = "customKey";
        when(crowdAddon.getCrowdCustomTokenKey()).thenReturn(customKey);
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        when(authSettingsClient.getCrowdSetting(false)).thenReturn(Optional.empty());
        RestResponse<CrowdIntegration> response = new ArtifactoryRestResponse<>();

        crowdIntegrationService.execute(mock(ArtifactoryRestRequest.class), response);
        CrowdIntegration crowdIntegration = response.getIModel();
        Assert.assertEquals(crowdIntegration.getCustomCookieTokenKey(), customKey);
        verify(addonsManager, times(1)).addonByType(CrowdAddon.class);
        verify(crowdAddon, times(1)).getCrowdCustomTokenKey();
        verify(accessService, times(1)).getAccessAuthSettingsClient();
        verify(authSettingsClient, times(1)).getCrowdSetting(false);
    }

    @Test
    public void getCrowdFromKeyWhenNullConfiguredAndCrowdSettingNotNull() {
        String appName = "appName";
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        when(authSettingsClient.getCrowdSetting(false)).thenReturn(Optional.of(CrowdSetting.builder()
                .applicationName(appName).build()));
        when(crowdAddon.getCrowdCustomTokenKey()).thenReturn(null);
        RestResponse<CrowdSetting> response = new ArtifactoryRestResponse<>();

        crowdIntegrationService.execute(mock(ArtifactoryRestRequest.class), response);
        CrowdIntegration crowdIntegration = response.getIModel();
        Assert.assertNull(crowdIntegration.getCustomCookieTokenKey());
        Assert.assertEquals(crowdIntegration.getApplicationName(), appName);
        verify(addonsManager, times(1)).addonByType(CrowdAddon.class);
        verify(crowdAddon, times(1)).getCrowdCustomTokenKey();
        verify(accessService, times(1)).getAccessAuthSettingsClient();
        verify(authSettingsClient, times(1)).getCrowdSetting(false);
    }

    @Test
    public void getCrowdFromKeyWhenNullConfiguredAndCrowdSettingIsNull() {
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);
        when(authSettingsClient.getCrowdSetting(false)).thenReturn(Optional.empty());
        when(crowdAddon.getCrowdCustomTokenKey()).thenReturn(null);
        RestResponse<CrowdIntegration> response = new ArtifactoryRestResponse<>();

        crowdIntegrationService.execute(mock(ArtifactoryRestRequest.class), response);
        CrowdIntegration crowdIntegration = response.getIModel();
        Assert.assertNull(crowdIntegration.getCustomCookieTokenKey());
        verify(addonsManager, times(1)).addonByType(CrowdAddon.class);
        verify(crowdAddon, times(1)).getCrowdCustomTokenKey();
        verify(accessService, times(1)).getAccessAuthSettingsClient();
        verify(authSettingsClient, times(1)).getCrowdSetting(false);
    }

    @BeforeMethod
    private void initCrowd() {
        mocks = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(CrowdAddon.class)).thenReturn(crowdAddon);
        when(centralConfigDescriptor.getSecurity()).thenReturn(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(accessService.getAccessAuthSettingsClient()).thenReturn(authSettingsClient);

        crowdIntegrationService = new GetCrowdIntegrationService();
        crowdIntegrationService.setAddonsManager(addonsManager);
        crowdIntegrationService.setAccessService(accessService);
    }

    @AfterMethod
    private void noMoreCalls() throws Exception {
        Mockito.verifyNoMoreInteractions(crowdAddon, centralConfigService, addonsManager, centralConfigDescriptor,
                securityDescriptor);
        mocks.close();
    }
}