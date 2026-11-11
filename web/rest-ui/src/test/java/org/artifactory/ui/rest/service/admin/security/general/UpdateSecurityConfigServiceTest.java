package org.artifactory.ui.rest.service.admin.security.general;


import com.google.gson.JsonParser;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.curation.CurationInterfaceSettings;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.UserLockPolicy;
import org.artifactory.rest.common.model.FeedbackMsg;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.security.general.CentralOAuthSecurityConfig;
import org.artifactory.ui.rest.model.admin.security.general.SecurityConfig;
import org.artifactory.ui.rest.model.admin.security.password.PasswordSettings;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class UpdateSecurityConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String JSON_STRING_EMPTY = "{}";

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private AccessService accessService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private CurationAddon curationAddon;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @InjectMocks
    private UpdateSecurityConfigService updateSecurityConfigService;
    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(applicationContext.beanForType(AccessService.class)).thenReturn(accessService);
        when(addonsManager.addonByType(CurationAddon.class)).thenReturn(curationAddon);
        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void tryToTurnOffBasicAuthWhenNoExternalProvidersExistFails() {
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = mock(SecurityDescriptor.class);
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(securityDescriptor.isAnyExternalAuthEnabled()).thenReturn(false);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setBasicAuthEnabled(false);

        when(restRequest.getImodel()).thenReturn(securityConfig);

        updateSecurityConfigService.execute(restRequest, restResponse);

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(accessService).updateConfigurationModel(mapArgumentCaptor.capture());
        String actualSecurityConfigSentToAccess =
                JsonUtils.getInstance().writeValueAsString(mapArgumentCaptor.getValue());

        Assertions.assertEquals(JsonParser.parseString(JSON_STRING_EMPTY),
                JsonParser.parseString(actualSecurityConfigSentToAccess),
                "Should not be able to turn off basic auth when no external authentication providers exist");
    }

    @Test
    void updateSecurityConfigDefaultValues() {
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();

        when(restRequest.getImodel()).thenReturn(securityConfig);

        updateSecurityConfigService.execute(restRequest, restResponse);

        Assertions.assertFalse(securityDescriptor.isBuildGlobalBasicReadAllowed());
        Assertions.assertFalse(securityDescriptor.isBuildGlobalBasicReadForAnonymous());
        Assertions.assertFalse(securityDescriptor.isHideUnauthorizedResources());

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(accessService).updateConfigurationModel(mapArgumentCaptor.capture());
        String actualSecurityConfigSentToAccess =
                JsonUtils.getInstance().writeValueAsString(mapArgumentCaptor.getValue());

        Assertions.assertEquals(JsonParser.parseString(JSON_STRING_EMPTY),
                JsonParser.parseString(actualSecurityConfigSentToAccess));
    }

    @Test
    void updateSecurityConfig() {
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        when(accessService.isAnyExternalAuthEnabled()).thenReturn(true);

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();
        CentralOAuthSecurityConfig centralOAuthSecurityConfig = new CentralOAuthSecurityConfig();
        securityConfig.setCentralOAuthProviders(centralOAuthSecurityConfig);
        securityConfig.setPasswordSettings(new PasswordSettings());
        securityConfig.setAnonAccessEnabled(true);
        securityConfig.setBasicAuthEnabled(false);
        securityConfig.setBuildGlobalBasicReadAllowed(true);
        securityConfig.setBuildGlobalBasicReadForAnonymous(true);
        securityConfig.setCentralOAuthProviders(centralOAuthSecurityConfig);
        securityConfig.setHideUnauthorizedResources(true);
        securityConfig.setUserLockPolicy(new UserLockPolicy());
        securityConfig.setFeedbackMsg(new FeedbackMsg());

        when(restRequest.getImodel()).thenReturn(securityConfig);

        updateSecurityConfigService.execute(restRequest, restResponse);

        Assertions.assertTrue(securityDescriptor.isBuildGlobalBasicReadAllowed());
        Assertions.assertTrue(securityDescriptor.isBuildGlobalBasicReadForAnonymous());
        Assertions.assertTrue(securityDescriptor.isHideUnauthorizedResources());

        ArgumentCaptor<Map> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(accessService).updateConfigurationModel(mapArgumentCaptor.capture());
        String actualSecurityConfigSentToAccess =
                JsonUtils.getInstance().writeValueAsString(mapArgumentCaptor.getValue());

        String expectedSecurityConfigSentToAccess = """
                {
                  "security": {
                    "anonymous-access-enabled": true,
                    "user-lock-policy": {
                      "attempts": 5
                    },
                    "authentication": {
                      "password-encryption": "supported",
                      "basic-authentication-enabled": false
                    }
                  }
                }""";
        Assertions.assertEquals(JsonParser.parseString(expectedSecurityConfigSentToAccess),
                JsonParser.parseString(actualSecurityConfigSentToAccess));
    }

    @Test
    void updateCurationInterfaceSettingsWhenCurationEntitled() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(curationAddon.isCurationEntitled()).thenReturn(true);

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAllowDownloadWhenUnavailable(true);
        when(restRequest.getImodel()).thenReturn(securityConfig);

        updateSecurityConfigService.execute(restRequest, restResponse);

        ArgumentCaptor<CurationInterfaceSettings> captor =
                ArgumentCaptor.forClass(CurationInterfaceSettings.class);
        verify(curationAddon).updateInterfaceSettings(captor.capture());
        Assertions.assertTrue(captor.getValue().allowDownloadWhenUnavailable());
    }

    @Test
    void skipCurationInterfaceSettingsUpdateWhenNotEntitled() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(curationAddon.isCurationEntitled()).thenReturn(false);

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();
        when(restRequest.getImodel()).thenReturn(securityConfig);

        updateSecurityConfigService.execute(restRequest, restResponse);

        verify(curationAddon, never()).updateInterfaceSettings(any());
    }

    @Test
    void skipCurationInterfaceSettingsUpdateWhenFeatureFlagDisabled() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "false");
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(curationAddon.isCurationEntitled()).thenReturn(true);

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAllowDownloadWhenUnavailable(true);
        when(restRequest.getImodel()).thenReturn(securityConfig);

        updateSecurityConfigService.execute(restRequest, restResponse);

        verify(curationAddon, never()).updateInterfaceSettings(any());
    }

    @Test
    void throwsConfigurationExceptionWhenCurationUpdateFails() {
        homeStub.setProperty(ConstantValues.curationFailOpenUiEnabled, "true");
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        SecurityDescriptor securityDescriptor = new SecurityDescriptor();
        centralConfigDescriptor.setSecurity(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(curationAddon.isCurationEntitled()).thenReturn(true);
        doThrow(new RuntimeException("curation service unavailable"))
                .when(curationAddon).updateInterfaceSettings(any());

        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse restResponse = mock(ArtifactoryRestResponse.class);

        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.setAllowDownloadWhenUnavailable(true);
        when(restRequest.getImodel()).thenReturn(securityConfig);

        Assertions.assertThrows(ConfigurationException.class,
                () -> updateSecurityConfigService.execute(restRequest, restResponse));
    }
}