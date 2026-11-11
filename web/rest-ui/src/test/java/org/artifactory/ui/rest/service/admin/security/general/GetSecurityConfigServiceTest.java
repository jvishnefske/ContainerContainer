package org.artifactory.ui.rest.service.admin.security.general;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.api.config.ArtifactorySystemConfig;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.CentralOAuthSettings;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.jfrog.access.rest.system.UserLockPolicyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetSecurityConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private AccessService accessService;
    @Mock
    private AddonsManager addonsManager;
    @InjectMocks
    private GetSecurityConfigService getSecurityConfigService;

    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
    }

    @Test
    void getSecurityConfigPopulatesCurationEntitledAndAllowDownloadWhenEnabled() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        CurationAddon curationAddon = mock(CurationAddon.class);
        when(curationAddon.isCurationEntitled()).thenReturn(true);
        when(curationAddon.isAllowDownloadWhenUnavailable()).thenReturn(true);
        setUpCommonMocks(curationAddon);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        getSecurityConfigService.execute(request, response);

        ArgumentCaptor<SecurityConfig> captor = ArgumentCaptor.forClass(SecurityConfig.class);
        verify(response).iModel(captor.capture());
        SecurityConfig result = captor.getValue();
        assertTrue(result.isCurationEntitled());
        assertTrue(result.isAllowDownloadWhenUnavailable());
    }

    @Test
    void getSecurityConfigPopulatesCurationEntitledFalseWhenNotEntitled() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        CurationAddon curationAddon = mock(CurationAddon.class);
        when(curationAddon.isCurationEntitled()).thenReturn(false);
        when(curationAddon.isAllowDownloadWhenUnavailable()).thenReturn(false);
        setUpCommonMocks(curationAddon);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        getSecurityConfigService.execute(request, response);

        ArgumentCaptor<SecurityConfig> captor = ArgumentCaptor.forClass(SecurityConfig.class);
        verify(response).iModel(captor.capture());
        SecurityConfig result = captor.getValue();
        assertFalse(result.isCurationEntitled());
        assertFalse(result.isAllowDownloadWhenUnavailable());
    }

    @Test
    void getSecurityConfigDefaultsToBothFalseWhenAddonByTypeThrows() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        CurationAddon curationAddon = mock(CurationAddon.class);
        setUpCommonMocks(curationAddon);
        when(addonsManager.addonByType(CurationAddon.class))
                .thenThrow(new RuntimeException("Curation service unavailable"));

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        getSecurityConfigService.execute(request, response);

        ArgumentCaptor<SecurityConfig> captor = ArgumentCaptor.forClass(SecurityConfig.class);
        verify(response).iModel(captor.capture());
        SecurityConfig result = captor.getValue();
        assertFalse(result.isCurationEntitled());
        assertFalse(result.isAllowDownloadWhenUnavailable());
    }

    @Test
    void getSecurityConfigDefaultsToBothFalseWhenCurationEntitledThrows() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        CurationAddon curationAddon = mock(CurationAddon.class);
        when(curationAddon.isCurationEntitled()).thenThrow(new RuntimeException("Curation entitlement check failed"));
        setUpCommonMocks(curationAddon);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        getSecurityConfigService.execute(request, response);

        ArgumentCaptor<SecurityConfig> captor = ArgumentCaptor.forClass(SecurityConfig.class);
        verify(response).iModel(captor.capture());
        SecurityConfig result = captor.getValue();
        assertFalse(result.isCurationEntitled());
        assertFalse(result.isAllowDownloadWhenUnavailable());
    }

    @Test
    void getSecurityConfigReturnsFalseForCurationFieldsWhenFeatureFlagDisabled() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "false");
        CurationAddon curationAddon = mock(CurationAddon.class);
        when(curationAddon.isCurationEntitled()).thenReturn(true);
        when(curationAddon.isAllowDownloadWhenUnavailable()).thenReturn(true);
        setUpCommonMocks(curationAddon);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        getSecurityConfigService.execute(request, response);

        ArgumentCaptor<SecurityConfig> captor = ArgumentCaptor.forClass(SecurityConfig.class);
        verify(response).iModel(captor.capture());
        SecurityConfig result = captor.getValue();
        assertFalse(result.isCurationEntitled());
        assertFalse(result.isAllowDownloadWhenUnavailable());
    }

    private void setUpCommonMocks(CurationAddon curationAddon) {
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        SecurityDescriptor securityDescriptor = mock(SecurityDescriptor.class);
        ArtifactorySystemConfig systemConfig = mock(ArtifactorySystemConfig.class);
        UserLockPolicyConfig userLockPolicyConfig = mock(UserLockPolicyConfig.class);

        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getSecurity()).thenReturn(securityDescriptor);
        when(securityDescriptor.getPasswordSettings())
                .thenReturn(new org.artifactory.descriptor.security.PasswordSettings());
        when(accessService.getUserLockPolicyConfig()).thenReturn(userLockPolicyConfig);
        when(userLockPolicyConfig.getAttempts()).thenReturn(0);
        CentralOAuthSettings centralOAuthSettings = mock(CentralOAuthSettings.class);
        when(centralConfigService.getSystemConfiguration()).thenReturn(systemConfig);
        when(systemConfig.getCentralOAuthDefaultConfig()).thenReturn(centralOAuthSettings);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(curationAddon);
    }
}
