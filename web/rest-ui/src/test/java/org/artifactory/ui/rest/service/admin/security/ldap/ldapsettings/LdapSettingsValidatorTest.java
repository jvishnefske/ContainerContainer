package org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings;

import com.google.common.collect.Lists;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.security.access.AccessService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.LdapSettingsValidator.INVALID_PASSWORD_MSG;
import static org.artifactory.ui.rest.service.admin.security.ldap.ldapsettings.LdapSettingsValidator.KEYS_ARE_DIFFERENT_MSG;
import static org.artifactory.ui.utils.RequestUtils.PASSWORD_PLACEHOLDER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Dor Solomon
 */
class LdapSettingsValidatorTest {

    private static final List<Character> forbiddenChars = Lists
            .newArrayList('/', '\\', '|', '?', '*', '"', '<', '>');

    AccessService accessService =Mockito.mock(AccessService.class);

    @Test
    void testValidateCreateWithEmptyXMLNames() {

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey("");
        LdapSettingsValidator.validateCreate(ldapSetting, statusHolder,
                accessService);
        Assertions.assertTrue(statusHolder.hasErrors());
        Assertions.assertEquals("XML names cannot be empty", statusHolder.getStatusMsg());
    }

    @Test
    void testValidateCreateWithInvalidXMLNameCharacter() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();

        for (Character character : forbiddenChars) {
            ldapSetting.setKey("test" + character);
            LdapSettingsValidator.validateCreate(ldapSetting, statusHolder,
                    accessService);
            Assertions.assertEquals("XML names cannot contain the character \"" + character + "\"",
                    statusHolder.getStatusMsg());
        }
    }

    @Test
    void testValidateCreateWithInvalidXMLNameStartsWithNumber() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey("1-test");
        LdapSettingsValidator.validateCreate(ldapSetting, statusHolder,
                accessService);
        Assertions.assertEquals("XML names cannot begin with the character \"1\"", statusHolder.getStatusMsg());
    }

    @Test
    void testCreateValidLdapSettings() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey("valid-1-key-2");
        LdapSettingsValidator.validateCreate(ldapSetting, statusHolder,
                accessService);
        Assertions.assertFalse(statusHolder.hasErrors());
    }

    @Test
    void testValidateCreateWhenKeyIsUsedByAnotherLdap() {
        String ldapKey = "key1";
        LdapSetting existingLdapSetting = new LdapSetting();
        existingLdapSetting.setKey(ldapKey);
        accessService.upsertLdapSetting(existingLdapSetting);

        when(accessService.isLdapSettingsExist(anyString())).thenReturn(true);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(ldapKey);
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword(PASSWORD_PLACEHOLDER);
        ldapSetting.setSearch(searchPattern);
        LdapSettingsValidator.validateCreate(ldapSetting, statusHolder, accessService);
        Assertions.assertEquals("Ldap with key key1 already exists", statusHolder.getStatusMsg());
    }



    @Test
    void testValidateUpdateWithEmptyXMLNames() {
        String repoKey = "";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(repoKey);
        LdapSettingsValidator.validateUpdate(repoKey, ldapSetting, statusHolder);
        Assertions.assertTrue(statusHolder.hasErrors());
        Assertions.assertEquals("XML names cannot be empty", statusHolder.getStatusMsg());
    }

    @Test
    void testValidateUpdateWithInvalidXMLNameCharacter() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        for (Character character : forbiddenChars) {
            String repoKey = "test" + character;
            ldapSetting.setKey(repoKey);
            LdapSettingsValidator.validateUpdate(repoKey, ldapSetting, statusHolder);
            Assertions.assertEquals("XML names cannot contain the character \"" + character + "\"",
                    statusHolder.getStatusMsg());
        }
    }

    @Test
    void testValidateUpdateWithInvalidXMLNameStartsWithNumber() {
        String repoKey = "1-test";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(repoKey);
        LdapSettingsValidator.validateUpdate(repoKey, ldapSetting, statusHolder);
        Assertions.assertEquals("XML names cannot begin with the character \"1\"", statusHolder.getStatusMsg());
    }

    @Test
    void testValidateUpdateCorrectLdapSettings() {
        String repoKey = "valid-1-key-2";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(repoKey);
        LdapSettingsValidator.validateUpdate(repoKey, ldapSetting, statusHolder);
        Assertions.assertFalse(statusHolder.hasErrors());
    }

    @Test
    void testValidateUpdateWithInvalidPassword() {
        String repoKey = "valid-1-key-2";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey(repoKey);
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword(PASSWORD_PLACEHOLDER);
        ldapSetting.setSearch(searchPattern);
        LdapSettingsValidator.validateUpdate(repoKey, ldapSetting, statusHolder);
        Assertions.assertEquals(INVALID_PASSWORD_MSG, statusHolder.getStatusMsg());
    }

    @Test
    void testValidateUpdateWithDifferentKeys() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        LdapSetting ldapSetting = new LdapSetting();
        ldapSetting.setKey("key1");
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setManagerPassword("password");
        ldapSetting.setSearch(searchPattern);
        LdapSettingsValidator.validateUpdate("key2", ldapSetting, statusHolder);
        Assertions.assertEquals(KEYS_ARE_DIFFERENT_MSG, statusHolder.getStatusMsg());
    }
}
