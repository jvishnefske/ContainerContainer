package org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.security.ldap.LdapSettingModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class TestLdapSettingsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static MockedStatic<CryptoHelper> staticCryptoHelper;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private RestResponse response;
    @InjectMocks
    private TestLdapSettingsService testLdapSettingsService;

    private AutoCloseable mockitoAutoCloseable;

    @BeforeAll
    static void beforeAll() {
        staticCryptoHelper = Mockito.mockStatic(CryptoHelper.class);
    }

    @AfterAll
    static void afterAll() {
        staticCryptoHelper.close();
    }

    @BeforeEach
    void beforeEach() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void afterEach() throws Exception {
        mockitoAutoCloseable.close();
    }

    @Test
    void testLdapSettingsNullUsernameAndPasswordFails() {
        LdapSettingModel ldapSettingModel = new LdapSettingModel(new LdapSetting(), false);
        ldapSettingModel.setTestUsername(null);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        testLdapSettingsService.execute(request, response);

        ArgumentCaptor<String> errorArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).error(errorArgumentCaptor.capture());
        String error = errorArgumentCaptor.getValue();
        Assertions.assertEquals("Please enter test username and password to test the LDAP settings", error);

        ldapSettingModel.setTestUsername("testUser");
        ldapSettingModel.setTestPassword(null);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        testLdapSettingsService.execute(request, response);

        errorArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(2)).error(errorArgumentCaptor.capture());
        error = errorArgumentCaptor.getValue();
        Assertions.assertEquals("Please enter test username and password to test the LDAP settings", error);
    }

    @Test
    void testLdapSettingsDecryptedManagerPassword() {
        String testUser = "testUser";
        String testPassword = "testPassword";
        String decryptedPassword = "decryptedPassword";
        LdapSetting ldapSetting = new LdapSetting();
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword("encryptedPassword");
        ldapSetting.setSearch(searchPattern);
        ldapSetting.setKey("ldapKey");
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        ldapSettingModel.setTestUsername(testUser);
        ldapSettingModel.setTestPassword(testPassword);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        staticCryptoHelper.when(() -> CryptoHelper.decryptIfNeeded(any(), anyString())).thenReturn(decryptedPassword);

        when(securityService.testLdapConnection(ldapSetting, testUser, testPassword)).thenReturn(
                new BasicStatusHolder());

        testLdapSettingsService.execute(request, response);

        ArgumentCaptor<LdapSetting> ldapSettingArgumentCaptor = ArgumentCaptor.forClass(LdapSetting.class);

        verify(securityService).testLdapConnection(ldapSettingArgumentCaptor.capture(),
                ArgumentCaptor.forClass(String.class).capture(), ArgumentCaptor.forClass(String.class).capture());

        LdapSetting capturedLapSetting = ldapSettingArgumentCaptor.getValue();

        Assertions.assertEquals(decryptedPassword, capturedLapSetting.getSearch().getManagerPassword());
    }

    @Test
    void testLdapSettingsNoSearchPattern() {
        String testUser = "testUser";
        String testPassword = "testPassword";
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey("ldapKey");
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        ldapSettingModel.setTestUsername(testUser);
        ldapSettingModel.setTestPassword(testPassword);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        when(securityService.testLdapConnection(ldapSetting, testUser, testPassword)).thenReturn(
                new BasicStatusHolder());

        testLdapSettingsService.execute(request, response);

        ArgumentCaptor<LdapSetting> ldapSettingArgumentCaptor = ArgumentCaptor.forClass(LdapSetting.class);

        verify(securityService).testLdapConnection(ldapSettingArgumentCaptor.capture(),
                ArgumentCaptor.forClass(String.class).capture(), ArgumentCaptor.forClass(String.class).capture());

        LdapSetting capturedLapSetting = ldapSettingArgumentCaptor.getValue();

        Assertions.assertEquals("ldapKey", capturedLapSetting.getKey());
        Assertions.assertNull(capturedLapSetting.getSearch());
    }

    @Test
    void testLdapSettingReturnsError() {
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setSearch(new SearchPattern());
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        ldapSettingModel.setTestUsername("testUser");
        ldapSettingModel.setTestPassword("testPassword");
        when(request.getImodel()).thenReturn(ldapSettingModel);

        staticCryptoHelper.when(() -> CryptoHelper.decryptIfNeeded(any(), anyString())).thenReturn("decryptedPassword");

        BasicStatusHolder basicStatusHolder = new BasicStatusHolder();
        Logger logger = LoggerFactory.getLogger(SecurityService.class);
        basicStatusHolder.status("testInfoMsg", logger);
        basicStatusHolder.error("testErrorMsg", logger);
        basicStatusHolder.warn("testWarnMsg", logger);

        when(securityService.testLdapConnection(ldapSettingModel, "testUser", "testPassword")).thenReturn(
                basicStatusHolder);

        testLdapSettingsService.execute(request, response);

        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);

        verify(response).error(stringArgumentCaptor.capture());
        String errMsg = stringArgumentCaptor.getValue();
        Assertions.assertEquals("testErrorMsg", errMsg);

        verify(response).warn(stringArgumentCaptor.capture());
        String warnMsg = stringArgumentCaptor.getValue();
        Assertions.assertEquals("testWarnMsg", warnMsg);

        verify(response).info(stringArgumentCaptor.capture());
        String infoMsg = stringArgumentCaptor.getValue();
        Assertions.assertEquals("testInfoMsg", infoMsg);
    }

    
}