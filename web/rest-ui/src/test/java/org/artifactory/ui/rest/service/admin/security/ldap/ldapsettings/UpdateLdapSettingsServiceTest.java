package org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings;


import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.security.ldap.LdapSettingModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.LdapSettingsValidator.INVALID_PASSWORD_MSG;
import static org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.LdapSettingsValidator.KEYS_ARE_DIFFERENT_MSG;
import static org.artifactory.ui.utils.RequestUtils.PASSWORD_PLACEHOLDER;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class UpdateLdapSettingsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AccessService accessService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private RestResponse response;

    @InjectMocks
    private UpdateLdapSettingsService updateLdapSettingsService;
    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void beforeEach() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void afterEach() throws Exception {
        mockitoAutoCloseable.close();
    }

    @Test
    void testUpdateLdapSettingsLdapKeyNullOrEmptyFails() {
        when(request.getPathParamByKey("id")).thenReturn(null);
        LdapSetting ldapSetting = new LdapSetting();
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        updateLdapSettingsService.execute(request, response);

        ArgumentCaptor<String> errorArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).error(errorArgumentCaptor.capture());
        String error = errorArgumentCaptor.getValue();
        Assertions.assertEquals("XML names cannot be null", error);

        when(request.getPathParamByKey("id")).thenReturn(StringUtils.EMPTY);
        ldapSetting.setKey(StringUtils.EMPTY);
        ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        updateLdapSettingsService.execute(request, response);

        errorArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(2)).error(errorArgumentCaptor.capture());
        error = errorArgumentCaptor.getValue();
        Assertions.assertEquals("XML names cannot be empty", error);
    }

    @Test
    void testUpdateLdapSettingsInvalidEntityAndPathKeyFails() {
        when(request.getPathParamByKey("id")).thenReturn("pathKey");
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey("entityKey");
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        updateLdapSettingsService.execute(request, response);

        ArgumentCaptor<String> errorArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).error(errorArgumentCaptor.capture());
        String error = errorArgumentCaptor.getValue();
        Assertions.assertEquals(KEYS_ARE_DIFFERENT_MSG, error);
    }

    @Test
    void testUpdateLdapSettingsManagerPasswordIsPlaceholderFails() {
        String ldapKey = "ldapKey";
        when(request.getPathParamByKey("id")).thenReturn(ldapKey);
        LdapSetting ldapSetting = new LdapSetting();
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword(PASSWORD_PLACEHOLDER);
        ldapSetting.setSearch(searchPattern);
        ldapSetting.setKey(ldapKey);
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        updateLdapSettingsService.execute(request, response);

        ArgumentCaptor<String> errorArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).error(errorArgumentCaptor.capture());
        String error = errorArgumentCaptor.getValue();
        Assertions.assertEquals(INVALID_PASSWORD_MSG, error);
    }

    @Test
    void testUpdateLdapSettingsSuccess() {
        String ldapKey = "ldapKey";
        String managerPassword = "managerPassword";
        when(request.getPathParamByKey("id")).thenReturn(ldapKey);
        LdapSetting ldapSetting = new LdapSetting();
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword(managerPassword);
        ldapSetting.setSearch(searchPattern);
        ldapSetting.setKey(ldapKey);
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        updateLdapSettingsService.execute(request, response);

        ArgumentCaptor<LdapSetting> ldapSettingArgumentCaptor = ArgumentCaptor.forClass(LdapSetting.class);
        verify(accessService).upsertLdapSetting(ldapSettingArgumentCaptor.capture());
        LdapSetting capturedLdapSetting = ldapSettingArgumentCaptor.getValue();
        Assertions.assertEquals(managerPassword, capturedLdapSetting.getSearch().getManagerPassword());
    }

    @Test
    void testUpdateLdapSettingsNoSearchPattern() {
        String ldapKey = "ldapKey";
        when(request.getPathParamByKey("id")).thenReturn(ldapKey);
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(ldapKey);
        LdapSettingModel ldapSettingModel = new LdapSettingModel(ldapSetting, false);
        when(request.getImodel()).thenReturn(ldapSettingModel);

        updateLdapSettingsService.execute(request, response);

        ArgumentCaptor<LdapSetting> ldapSettingArgumentCaptor = ArgumentCaptor.forClass(LdapSetting.class);
        verify(accessService).upsertLdapSetting(ldapSettingArgumentCaptor.capture());
        LdapSetting capturedLdapSetting = ldapSettingArgumentCaptor.getValue();
        Assertions.assertEquals(ldapKey, capturedLdapSetting.getKey());
        Assertions.assertNull(capturedLdapSetting.getSearch());
    }
}