package org.artifactory.version.converter.v326;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.config.AccessConfigKeys.LdapGroupConfigKeys;
import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_LDAP_GROUP_SETTINGS;

public class MoveLdapGroupSettingToConfigServiceConverter implements XmlConverter {

    @Override
    public void convert(Document doc) {
        removeElements(doc);
    }

    private void removeElements(Document doc) {
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        List<LdapGroupSetting> ldapGroupsToStore = new ArrayList<>();
        Element security = rootElement.getChild(SECURITY, namespace);
        if (security != null) {
            Element ldapGroupSettings = security.getChild(SECURITY_LDAP_GROUP_SETTINGS, namespace);
            if (ldapGroupSettings != null) {
                List<Element> ldapGroupSettingsList =
                        ldapGroupSettings.getChildren(LdapGroupConfigKeys.SECURITY_LDAP_GROUP_SETTING, namespace);
                if (ldapGroupSettingsList != null && !ldapGroupSettingsList.isEmpty()) {
                    for (Element ldapSettingObject : ldapGroupSettingsList) {
                        Element ldapGroupSetting = ldapSettingObject;
                        ldapGroupsToStore.add(parseLdapGroupSetting(ldapGroupSetting, namespace));
                    }
                    if (!ldapGroupsToStore.isEmpty()) {
                        AccessConfigHolder.getInstance().addLdapGroupSetting(ldapGroupsToStore);
                    }
                }
            }
            security.removeChild(SECURITY_LDAP_GROUP_SETTINGS, namespace);
        }
    }

    LdapGroupSetting parseLdapGroupSetting(Element groupElement, Namespace namespace) {
        LdapGroupSetting toSave = new LdapGroupSetting();

        Element name = groupElement.getChild(LdapGroupConfigKeys.LDAP_GROUP_NAME, namespace);
        if (null != name) {
            toSave.setName(name.getText());
        }

        Element groupBaseDn = groupElement.getChild(LdapGroupConfigKeys.GROUP_BASE_DN, namespace);
        if (null != groupBaseDn) {
            toSave.setGroupBaseDn(groupBaseDn.getText());
        }

        Element groupNameAttribute = groupElement.getChild(LdapGroupConfigKeys.GROUP_NAME_ATTR, namespace);
        if (null != groupNameAttribute) {
            toSave.setGroupNameAttribute(groupNameAttribute.getText());
        }

        Element groupMemberAttribute = groupElement.getChild(LdapGroupConfigKeys.GROUP_MEMBER_ATTR, namespace);
        if (null != groupMemberAttribute) {
            toSave.setGroupMemberAttribute(groupMemberAttribute.getText());
        }

        Element subTree = groupElement.getChild(LdapGroupConfigKeys.SUB_TREE, namespace);
        if (null != subTree) {
            toSave.setSubTree(Boolean.valueOf(subTree.getValue()));
        }

        Element filter = groupElement.getChild(LdapGroupConfigKeys.FILTER, namespace);
        if (null != filter) {
            toSave.setFilter(filter.getText());
        }

        Element descriptionAttribute = groupElement.getChild(LdapGroupConfigKeys.DESC_ATTR, namespace);
        if (null != descriptionAttribute) {
            toSave.setDescriptionAttribute(descriptionAttribute.getText());
        }

        Element strategy = groupElement.getChild(LdapGroupConfigKeys.STRATEGY, namespace);
        if (null != strategy) {
            LdapGroupPopulatorStrategies strategies;
            if (strategy.getText().equals(LdapGroupPopulatorStrategies.STATIC.getType())) {
                strategies = LdapGroupPopulatorStrategies.STATIC;
            } else if (strategy.getText().equals(LdapGroupPopulatorStrategies.HIERARCHICAL.getType())) {
                strategies = LdapGroupPopulatorStrategies.HIERARCHICAL;
            } else {
                strategies = LdapGroupPopulatorStrategies.DYNAMIC;
            }
            toSave.setStrategy(strategies);
        }

        Element forceAttributeSearch = groupElement.getChild(LdapGroupConfigKeys.FORCE_ATTR_SEARCH, namespace);
        if (null != forceAttributeSearch) {
            toSave.setForceAttributeSearch(Boolean.parseBoolean(forceAttributeSearch.getValue()));
        }

        Element enabledKey = groupElement.getChild(LdapGroupConfigKeys.ENABLED_LDAP, namespace);
        if (null != enabledKey) {
            toSave.setEnabledLdap(enabledKey.getText());
        }

        return toSave;
    }
}
