package org.artifactory.version.converter.v326;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.config.AccessConfigKeys;
import org.artifactory.config.AccessConfigKeys.LdapConfigKeys;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_LDAP_SETTINGS;

@Slf4j
public class MoveLdapSettingToConfigServiceConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            moveLdapSetting(security, namespace);
        }
    }

    private void moveLdapSetting(Element security, Namespace namespace) {
        List<LdapSetting> ldapListToStore = new ArrayList<>();
        Element ldapSettings = security.getChild(SECURITY_LDAP_SETTINGS, namespace);
        if (ldapSettings != null) {
            List<Element> ldapSettingList = ldapSettings.getChildren(LdapConfigKeys.LDAP_SETTING, namespace);
            if (ldapSettingList != null && !ldapSettingList.isEmpty()) {
                for (Element ldapSettingObject : ldapSettingList) {
                    Element ldapSetting = ldapSettingObject;
                    ldapListToStore.add(parseLdapSetting(ldapSetting, namespace));
                }
                if (!ldapListToStore.isEmpty()) {
                    AccessConfigHolder.getInstance().addLdapSetting(ldapListToStore);
                }
                log.info("Successfully removed " + SECURITY_LDAP_SETTINGS);
            }
        }
        security.removeChild(SECURITY_LDAP_SETTINGS, namespace);
    }

    LdapSetting parseLdapSetting(Element ldapSetting, Namespace namespace) {
        LdapSetting toSave = new LdapSetting();

        Element key = ldapSetting.getChild("key", namespace);
        if(null != key) {
            toSave.setKey(key.getText());
        }

        Element allowUserToAccessProfile = ldapSetting.getChild(LdapConfigKeys.ALLOW_USER_TO_ACCESS_PROFILE, namespace);
        if (null != allowUserToAccessProfile) {
            toSave.setAllowUserToAccessProfile(
                    Boolean.parseBoolean(allowUserToAccessProfile.getValue()));
        }

        Element autoCreateUser = ldapSetting.getChild(LdapConfigKeys.AUTO_CREATE_USER, namespace);
        if (null != autoCreateUser) {
            toSave.setAutoCreateUser(Boolean.parseBoolean(autoCreateUser.getValue()));
        }

        Element ldapUrl = ldapSetting.getChild(AccessConfigKeys.LdapConfigKeys.URL, namespace);
        if (null != ldapUrl) {
            toSave.setLdapUrl(ldapUrl.getText());
        }

        Element enabled = ldapSetting.getChild(LdapConfigKeys.ENABLED, namespace);
        if (null != enabled) {
            toSave.setEnabled(Boolean.parseBoolean(enabled.getValue()));
        }

        Element email = ldapSetting.getChild(LdapConfigKeys.EMAIL_ATTR, namespace);
        if (null != email) {
            toSave.setEmailAttribute(email.getText());
        }

        Element userDnPattern = ldapSetting.getChild(LdapConfigKeys.USER_DN_PATTERN, namespace);
        if (null != userDnPattern) {
            toSave.setUserDnPattern(userDnPattern.getText());
        }

        Element poisoningProtection = ldapSetting.getChild(LdapConfigKeys.POISONING_PROTECTION, namespace);
        if (null != poisoningProtection) {
            toSave.setLdapPoisoningProtection(Boolean.parseBoolean(poisoningProtection.getValue()));
        }

        Element pagingSupportEnabled = ldapSetting.getChild(LdapConfigKeys.PAGING_SUPPORT_ENABLED, namespace);
        if (null != pagingSupportEnabled) {
            toSave.setPagingSupportEnabled(Boolean.parseBoolean(pagingSupportEnabled.getValue()));
        }

        toSave.setSearch(parseSearchPattern(ldapSetting, namespace));
        return toSave;
    }

    SearchPattern parseSearchPattern(Element ldapSetting,  Namespace namespace) {
        Element searchSetting = ldapSetting.getChild(LdapConfigKeys.SEARCH, namespace);
        SearchPattern searchPattern = new SearchPattern();
        if (null != searchSetting) {
            Element base = searchSetting.getChild(LdapConfigKeys.Search.SEARCH_BASE, namespace);
            if (null != base) {
                searchPattern.setSearchBase(base.getText());
            }

            Element filter = searchSetting.getChild(LdapConfigKeys.Search.SEARCH_FILTER, namespace);
            if (null != filter) {
                searchPattern.setSearchFilter(filter.getText());
            }

            Element subTree = searchSetting.getChild(LdapConfigKeys.Search.SEARCH_SUB_TREE, namespace);
            if (null != subTree) {
                searchPattern.setSearchSubTree(Boolean.parseBoolean(subTree.getValue()));
            }

            Element managerDn = searchSetting.getChild(LdapConfigKeys.Search.MANAGER_DN, namespace);
            if (null != managerDn) {
                searchPattern.setManagerDn(managerDn.getText());
            }

            Element managerPassword = searchSetting.getChild(LdapConfigKeys.Search.MANAGER_PASSWORD, namespace);
            if (null != managerPassword) {
                searchPattern.setManagerPassword(managerPassword.getValue());
            }
        }
        return searchPattern;
    }

}
