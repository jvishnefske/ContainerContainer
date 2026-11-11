/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.descriptor.security.ldap.group;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Objects;

/**
 * The descriptor which represents the LDAP group settings for integration between Artifactory and the specified LDAP
 * group.
 *
 * @author Tomer Cohen
 */
@XmlType(name = "LdapGroupSettingType",
        propOrder = {"name", "groupBaseDn", "groupNameAttribute", "groupMemberAttribute", "subTree", "filter",
                "descriptionAttribute", "strategy", "enabledLdap", "forceAttributeSearch"}, namespace = Descriptor.NS)
@GenerateDiffFunction
public class LdapGroupSetting implements Descriptor {

    @DiffKey
    @NotUnicode
    private String name;

    @NotUnicode
    private String groupBaseDn = "";

    @NotUnicode
    private String groupNameAttribute = "cn";

    @NotUnicode
    private String groupMemberAttribute;

    private boolean subTree;

    @XmlElement(defaultValue = "false")
    private boolean forceAttributeSearch = false;

    @NotUnicode
    private String filter;

    @NotUnicode
    private String descriptionAttribute;

    /**
     * The key of enabled LDAP server settings
     */
    @NotUnicode
    private String enabledLdap = "";

    @XmlElement(defaultValue = "STATIC", required = false)
    private LdapGroupPopulatorStrategies strategy = LdapGroupPopulatorStrategies.STATIC;

    public LdapGroupSetting() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGroupBaseDn() {
        return groupBaseDn == null ? "" : groupBaseDn;
    }

    public void setGroupBaseDn(String groupBaseDn) {
        this.groupBaseDn = groupBaseDn;
    }

    public String getGroupNameAttribute() {
        return groupNameAttribute;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public void setGroupNameAttribute(String groupNameAttribute) {
        this.groupNameAttribute = groupNameAttribute;
    }

    public String getGroupMemberAttribute() {
        return groupMemberAttribute;
    }

    public void setGroupMemberAttribute(String groupMemberAttribute) {
        this.groupMemberAttribute = groupMemberAttribute;
    }

    public boolean isSubTree() {
        return subTree;
    }

    public boolean isForceAttributeSearch() {
        return forceAttributeSearch;
    }

    public void setForceAttributeSearch(boolean forceAttributeSearch) {
        this.forceAttributeSearch = forceAttributeSearch;
    }

    public void setSubTree(boolean subTree) {
        this.subTree = subTree;
    }

    public String getDescriptionAttribute() {
        return descriptionAttribute;
    }

    public void setDescriptionAttribute(String descriptionAttribute) {
        this.descriptionAttribute = descriptionAttribute;
    }

    public LdapGroupPopulatorStrategies getStrategy() {
        return strategy;
    }

    public void setStrategy(LdapGroupPopulatorStrategies strategy) {
        this.strategy = strategy;
    }

    public boolean isEnabled() {
        return StringUtils.isNotBlank(enabledLdap);
    }

    public String getEnabledLdap() {
        return enabledLdap;
    }

    public void setEnabledLdap(String enabledLdap) {
        this.enabledLdap = enabledLdap;
    }

    public boolean identicalConfiguration(LdapGroupSetting that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (subTree != that.subTree) {
            return false;
        }
        if (!Objects.equals(descriptionAttribute, that.descriptionAttribute)) {
            return false;
        }
        if (!Objects.equals(enabledLdap, that.enabledLdap)) {
            return false;
        }
        if (!Objects.equals(filter, that.filter)) {
            return false;
        }
        if (!Objects.equals(groupBaseDn, that.groupBaseDn)) {
            return false;
        }
        if (!Objects.equals(groupMemberAttribute, that.groupMemberAttribute)) {
            return false;
        }
        if (!Objects.equals(groupNameAttribute, that.groupNameAttribute)) {
            return false;
        }
        if (!Objects.equals(name, that.name)) {
            return false;
        }
        if (strategy != that.strategy) {
            return false;
        }
        return forceAttributeSearch == that.forceAttributeSearch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LdapGroupSetting that = (LdapGroupSetting) o;

        return name.equals(that.name);

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
