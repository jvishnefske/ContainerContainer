package org.artifactory.security.access.convertor;

import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.security.ldap.SearchPattern;
import org.artifactory.descriptor.security.ldap.group.LdapGroupSetting;
import org.jfrog.access.client.model.LdapGroupPopulatorStrategies;
@UtilityClass
public class LdapArtifactoryAndAccessConvertor {
    public static LdapSetting convertLdapAccessSettingToArtiFactoryLdapSetting(
            org.jfrog.access.client.model.LdapSetting clientLdapSetting) {
        LdapSetting setting = new LdapSetting();
        setting.setKey(clientLdapSetting.getKey());
        setting.setLdapUrl(clientLdapSetting.getLdapUrl());
        setting.setEnabled(clientLdapSetting.isEnabled());
        setting.setLdapPoisoningProtection(clientLdapSetting.isLdapPoisoningProtection());
        setting.setEmailAttribute(clientLdapSetting.getEmailAttribute());
        setting.setAutoCreateUser(clientLdapSetting.isAutoCreateUser());
        setting.setUserDnPattern(clientLdapSetting.getUserDnPattern());
        setting.setPagingSupportEnabled(clientLdapSetting.isPagingSupportEnabled());
        setting.setAllowUserToAccessProfile(clientLdapSetting.isAllowUserToAccessProfile());
        SearchPattern searchPattern = new SearchPattern();
        searchPattern.setSearchSubTree(clientLdapSetting.getSearch().isSearchSubTree());
        searchPattern.setSearchFilter(clientLdapSetting.getSearch().getSearchFilter() == null ? null :
                clientLdapSetting.getSearch().getSearchFilter());
        searchPattern.setSearchBase(clientLdapSetting.getSearch().getSearchBase() == null ? null :
                clientLdapSetting.getSearch().getSearchBase());
        searchPattern.setManagerPassword(clientLdapSetting.getSearch().getManagerPassword() == null ? null :
                clientLdapSetting.getSearch().getManagerPassword());
        searchPattern.setManagerDn(
                clientLdapSetting.getSearch().getManagerDn() == null ? null :
                        clientLdapSetting.getSearch().getManagerDn());
        setting.setSearch(searchPattern);

        return setting;
    }

    public static org.jfrog.access.client.model.LdapSetting convertLdapArtifactorySettingToAccessLdapSetting(
            LdapSetting setting) {
        org.jfrog.access.client.model.LdapSearchPattern searchPattern =
                org.jfrog.access.client.model.LdapSearchPattern.builder()
                        .searchBase(setting.getSearch() != null ? setting.getSearch().getSearchBase() : null)
                        .searchFilter(setting.getSearch() != null ? setting.getSearch().getSearchFilter() : "")
                        .searchSubTree(setting.getSearch() != null && setting.getSearch().isSearchSubTree())
                        .managerDn(setting.getSearch() != null ? setting.getSearch().getManagerDn() : null)
                        .managerPassword(setting.getSearch() != null ? setting.getSearch().getManagerPassword() : null)
                        .build();
        return org.jfrog.access.client.model.LdapSetting.builder().ldapUrl(setting.getLdapUrl())
                .allowUserToAccessProfile(setting.isAllowUserToAccessProfile())
                .autoCreateUser(setting.isAutoCreateUser()).emailAttribute(setting.getEmailAttribute())
                .enabled(setting.isEnabled()).key(setting.getKey())
                .ldapPoisoningProtection(setting.getLdapPoisoningProtection())
                .userDnPattern(setting.getUserDnPattern())
                .pagingSupportEnabled(setting.isPagingSupportEnabled()).search(searchPattern).build();
    }

    public static LdapGroupSetting convertAccessLdapGroupSettingToArtifactoryLdapGroupSetting(
            org.jfrog.access.client.model.LdapGroupSetting clientSetting) {
        LdapGroupSetting groupSetting = new LdapGroupSetting();
        groupSetting.setForceAttributeSearch(clientSetting.isForceAttributeSearch());
        groupSetting.setGroupBaseDn(clientSetting.getGroupBaseDn());
        groupSetting.setGroupMemberAttribute(clientSetting.getGroupMemberAttribute());
        groupSetting.setGroupNameAttribute(clientSetting.getGroupNameAttribute());
        groupSetting.setFilter(clientSetting.getFilter());
        groupSetting.setDescriptionAttribute(clientSetting.getDescriptionAttribute());
        groupSetting.setSubTree(clientSetting.isSubTree());
        groupSetting.setName(clientSetting.getName());
        groupSetting.setEnabledLdap(clientSetting.getLdapSettingKey());
        org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies strategies;
        if (clientSetting.getStrategy().equals(LdapGroupPopulatorStrategies.DYNAMIC)) {
            strategies = org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies.DYNAMIC;
        } else if (clientSetting.getStrategy().equals(LdapGroupPopulatorStrategies.HIERARCHICAL)) {
            strategies = org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies.HIERARCHICAL;
        } else {
            strategies = org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies.STATIC;
        }
        groupSetting.setStrategy(strategies);
        return groupSetting;
    }

    public static org.jfrog.access.client.model.LdapGroupSetting convertArtifactoryLdapGroupSettingTOAccessGroupSetting(
            LdapGroupSetting groupSetting) {
        LdapGroupPopulatorStrategies strategies;
        if (groupSetting.getStrategy()
                .equals(org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies.STATIC)) {
            strategies = LdapGroupPopulatorStrategies.STATIC;
        } else if (groupSetting.getStrategy()
                .equals(org.artifactory.descriptor.security.ldap.group.LdapGroupPopulatorStrategies.DYNAMIC)) {
            strategies = LdapGroupPopulatorStrategies.DYNAMIC;
        } else {
            strategies = LdapGroupPopulatorStrategies.HIERARCHICAL;
        }
        return org.jfrog.access.client.model.LdapGroupSetting.builder().strategy(strategies)
                .descriptionAttribute(groupSetting.getDescriptionAttribute()).filter(groupSetting.getFilter())
                .groupBaseDn(groupSetting.getGroupBaseDn()).forceAttributeSearch(groupSetting.isForceAttributeSearch())
                .groupMemberAttribute(groupSetting.getGroupMemberAttribute())
                .groupNameAttribute(groupSetting.getGroupNameAttribute()).name(groupSetting.getName())
                .ldapSettingKey(groupSetting.getEnabledLdap()).subTree(groupSetting.isSubTree()).build();
    }
}
