package org.artifactory.ui.rest.service.admin.security.saml;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.ui.rest.model.admin.security.saml.Saml;
import org.jfrog.access.client.model.SamlSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetSamlServiceTest {
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
        GetSamlService getSamlService = new GetSamlService();
        AccessService accessService = mock(AccessService.class);
        when(accessService.getSamlSettings()).thenReturn(SamlSetting.builder().build());
        getSamlService.setAccessService(accessService);
        Saml actual = (Saml)getSamlService.getSamlModel();
        SamlSetting expected = SamlSetting.builder().build();
        assertEquals(expected.isEnableIntegration(), actual.isEnableIntegration());
        assertEquals(expected.isUseEncryptedAssertion(), actual.isUseEncryptedAssertion());
        assertEquals(expected.isSyncGroups(), actual.isSyncGroups());
        assertEquals(expected.isAutoUserCreation(), actual.isNoAutoUserCreation());
        assertEquals(expected.isAllowUserToAccessProfile(), actual.isAllowUserToAccessProfile());
        assertEquals(expected.isAutoRedirect(), actual.isAutoRedirect());
        assertEquals(expected.isVerifyAudienceRestriction(), actual.isVerifyAudienceRestriction());
        assertEquals(expected.getLoginUrl(), actual.getLoginUrl());
        assertEquals(expected.getServiceProviderName(), actual.getServiceProviderName());
        assertEquals(expected.getCertificate(), actual.getCertificate());
        assertEquals(expected.getLogoutUrl(), actual.getLogoutUrl());
        assertEquals(expected.getEmailAttribute(), actual.getEmailAttribute());
        assertEquals(expected.getGroupAttribute(), actual.getGroupAttribute());
    }
}
