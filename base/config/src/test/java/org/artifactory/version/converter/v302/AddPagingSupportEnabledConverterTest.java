package org.artifactory.version.converter.v302;

import org.artifactory.common.ConstantValues;
import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.artifactory.version.converter.v305.AddPagingSupportEnabledConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author  Igor Usenko
 */
@Test
public class AddPagingSupportEnabledConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    public void whenPaginationSettingExistsItIsNotChangedMissingSettingSetToGlobalValue() throws Exception {
        whenPaginationSettingExistsItIsNotChangedMissingSettingSetToGlobalValue(false);
        whenPaginationSettingExistsItIsNotChangedMissingSettingSetToGlobalValue(true);
    }

    @Test
    public void whenNoLdapSettingsThenNothingChangedForBothLdapPagingSupportSettingValue() throws Exception {
        whenNoLdapSettingsThenNothingChanged(false);
        whenNoLdapSettingsThenNothingChanged(true);
    }

    @Test
    public void whenLdapSettingExistsThenItIsChangedToLdapPagingSupportSettingValue() throws Exception {
        whenLdapSettingExistsThenItIsChanged(false);
        whenLdapSettingExistsThenItIsChanged(true);
    }

    private void whenPaginationSettingExistsItIsNotChangedMissingSettingSetToGlobalValue(boolean ldapPagingSupport)
            throws Exception {
        homeStub.setProperty(ConstantValues.ldapPagingSupport, Boolean.toString(ldapPagingSupport));

        Document document = convertXml(
                "/config/test/config.3.0.2_with_already_exists_ldap_settings_with_pagination.xml",
                new AddPagingSupportEnabledConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element securityElement = rootElement.getChild("security", namespace);
        Element ldapSettingsElement = securityElement.getChild("ldapSettings", namespace);
        List<Element> ldapSettingElements = ldapSettingsElement.getChildren();

        assertEquals(ldapSettingElements.size(), 3);

        Map<String, Boolean> map = new HashMap<>();

        for (int i = 0; i <= 2; ++i) {
            Element ldapSettingElement = ldapSettingElements.get(i);
            Element ldapSettingsKeyElement = ldapSettingElement.getChild("key", namespace);
            String ldapSettingsKey = ldapSettingsKeyElement.getContent(0).getValue();
            List<Element> pagingSupportEnabledElements =
                    ldapSettingElement.getChildren("pagingSupportEnabled", namespace);
            assertEquals(pagingSupportEnabledElements.size(), 1);
            String pagingSupportEnabled = pagingSupportEnabledElements.get(0).getContent(0).getValue();

            map.put(ldapSettingsKey, Boolean.valueOf(pagingSupportEnabled));
        }

        assertEquals(map.size(), 3);
        assertTrue(map.get("paging-true"));
        assertFalse(map.get("paging-false"));
        assertEquals(map.get("paging-not-defined").booleanValue(), ldapPagingSupport);
    }

    private void whenNoLdapSettingsThenNothingChanged(boolean ldapPagingSupport) throws Exception {
        homeStub.setProperty(ConstantValues.ldapPagingSupport, Boolean.toString(ldapPagingSupport));

        Document document = convertXml("/config/test/config.3.0.2_without_ldap_settings.xml",
                new AddPagingSupportEnabledConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element securityElement = rootElement.getChild("security", namespace);
        Element ldapSettingsElement = securityElement.getChild("ldapSettings", namespace);
        List<Element> ldapSettingElements = ldapSettingsElement.getChildren();

        assertTrue(ldapSettingElements.isEmpty());
    }

    private void whenLdapSettingExistsThenItIsChanged(boolean ldapPagingSupport) throws Exception {
        homeStub.setProperty(ConstantValues.ldapPagingSupport, Boolean.toString(ldapPagingSupport));

        Document document = convertXml("/config/test/config.3.0.2_with_ldap_settings.xml",
                new AddPagingSupportEnabledConverter());
        Element rootElement = document.getRootElement();
        Namespace namespace = rootElement.getNamespace();

        Element securityElement = rootElement.getChild("security", namespace);
        Element ldapSettingsElement = securityElement.getChild("ldapSettings", namespace);
        List<Element> ldapSettingElements = ldapSettingsElement.getChildren();

        assertEquals(1, ldapSettingElements.size());

        Element ldapSettingElement = ldapSettingElements.get(0);
        Element pagingSupportEnabledElement = ldapSettingElement.getChild("pagingSupportEnabled", namespace);
        String value = pagingSupportEnabledElement.getContent(0).getValue();

        assertEquals(ldapPagingSupport, Boolean.parseBoolean(value));
    }

}