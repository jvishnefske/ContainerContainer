package org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.security.ldap.LdapSettingModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetLdapSettingsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private SecurityDescriptor securityDescriptor;
    @Mock
    private LdapSetting ldapSetting;
    @Mock
    private AccessService accessService;

    private GetLdapSettingsService ldapSettingsService;
    private AutoCloseable mockitoAutoCloseable;

    private static MockedStatic<CryptoHelper> staticCryptoHelper;

    @BeforeAll
    static void beforeAll() {
        staticCryptoHelper = Mockito.mockStatic(CryptoHelper.class);
        staticCryptoHelper.when(() -> CryptoHelper.encryptIfNeeded(any(), anyString())).thenReturn("encryptedPassword");
    }

    @AfterAll
    static void afterAll() {
        staticCryptoHelper.close();
    }

    @BeforeEach
    void initLdap() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(centralConfigDescriptor.getSecurity()).thenReturn(securityDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(centralConfigDescriptor);
        when(accessService.getLdapSettings(anyString())).thenReturn(ldapSetting);
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword("SUPER_SECRET_PASSWORD");
        when(ldapSetting.getSearch()).thenReturn(searchPattern);

        ldapSettingsService = new GetLdapSettingsService(accessService);
    }

    @AfterEach
    void noMoreCalls() throws Exception {
        Mockito.verifyNoMoreInteractions(centralConfigService, centralConfigDescriptor);
        mockitoAutoCloseable.close();
    }

    @Test
    void getLdapSettings() {
        RestResponse<LdapSettingModel> response = new ArtifactoryRestResponse<>();

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("id")).thenReturn("ldap");

        ldapSettingsService.execute(request, response);
        LdapSettingModel ldapSettingModel = response.getIModel();
        assertNotNull(ldapSettingModel);
        assertNotNull(ldapSettingModel.getSearch());
        assertEquals("encryptedPassword", ldapSettingModel.getSearch().getManagerPassword());
    }
}