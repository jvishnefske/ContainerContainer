package org.artifactory.ui.rest.service.admin.security.saml;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.sso.saml.SamlSsoAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.ui.rest.model.admin.security.saml.Saml;
import org.jfrog.access.client.model.SamlSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateSamlServiceTest {
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testAuthenticateAdditives() {
        ArtifactoryContextThreadBinder.bind(applicationContext);
        UpdateSamlService updateSamlService = new UpdateSamlService();
        AccessService accessService = mock(AccessService.class);
        when(accessService.getSamlSettings()).thenReturn(SamlSetting.builder().build());
        updateSamlService.setAccessService(accessService);
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        when(artifactoryRestRequest.getImodel()).thenReturn(Saml.builder()
                .enableIntegration(true)
                .useEncryptedAssertion(true)
                .noAutoUserCreation(true)
                .certificate("certificate").build());
        RestResponse response = new ArtifactoryRestResponse();
        AddonsManager addonsManager = mock(AddonsManager.class);
        SamlSsoAddon samlSsoAddon = mock(SamlSsoAddon.class);
        when(addonsManager.addonByType(SamlSsoAddon.class)).thenReturn(samlSsoAddon);
        when(ContextHelper.get().beanForType(AddonsManager.class)).thenReturn(addonsManager);
        updateSamlService.updateSamlSetting(artifactoryRestRequest, response);
        SamlSetting expected = SamlSetting.builder()
                .enableIntegration(true)
                .useEncryptedAssertion(true)
                .autoUserCreation(true)
                .certificate("certificate")
                .build();
        verify(accessService, times(1)).upsertSamlSetting(expected);
    }

    @Test
    void testAuthenticateException() {
        ArtifactoryContextThreadBinder.bind(applicationContext);
        UpdateSamlService updateSamlService = new UpdateSamlService();
        AccessService accessService = mock(AccessService.class);
        when(accessService.getSamlSettings()).thenReturn(SamlSetting.builder().build());
        updateSamlService.setAccessService(accessService);
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        updateSamlService.updateSamlSetting(artifactoryRestRequest, response);
    }
}
