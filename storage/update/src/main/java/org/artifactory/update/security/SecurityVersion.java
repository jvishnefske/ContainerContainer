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

package org.artifactory.update.security;

import org.artifactory.update.security.v1.AclsConverter;
import org.artifactory.update.security.v1.UserPermissionsConverter;
import org.artifactory.update.security.v2.RepoPathAclConverter;
import org.artifactory.update.security.v2.SimpleUserConverter;
import org.artifactory.update.security.v3.AclRepoKeysConverter;
import org.artifactory.update.security.v3.AnyRemoteConverter;
import org.artifactory.update.security.v4.AnnotatePermissionXmlConverter;
import org.artifactory.update.security.v6.LdapGroupSettingXmlConverter;
import org.artifactory.update.security.v6.LowercaseUsernameXmlConverter;
import org.artifactory.update.security.v8.Md5TemplateHashPasswordConverter;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.VersionWrapper;
import org.artifactory.version.XmlConverterUtils;
import org.artifactory.version.converter.XmlConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author freds
 */
public enum SecurityVersion implements VersionWrapper {
    unsupported(ArtifactoryServiceVersionProvider.v122rc0.get()) {
        @Override
        public String convert(String in) {
            throw new IllegalStateException(
                    "Reading security data from backup of Artifactory version older than 1.2.5-rc6 is not supported!");
        }
    },
    v1(ArtifactoryServiceVersionProvider.v125.get(), new UserPermissionsConverter(), new AclsConverter()),
    v2(ArtifactoryServiceVersionProvider.v130beta1.get(),
            new SimpleUserConverter(), new RepoPathAclConverter()),
    v3(ArtifactoryServiceVersionProvider.v130beta3.get(), new AnyRemoteConverter(), new AclRepoKeysConverter()),
    v4(ArtifactoryServiceVersionProvider.v210.get(), new AnnotatePermissionXmlConverter()),
    v5(ArtifactoryServiceVersionProvider.v211.get()),
    // due to a bug 2.1.2 was released with new security version that is the same as 4
    v6(ArtifactoryServiceVersionProvider.v213.get(), new LdapGroupSettingXmlConverter(),
            new LowercaseUsernameXmlConverter()),
    v7(ArtifactoryServiceVersionProvider.v220.get()),
    v8(ArtifactoryServiceVersionProvider.v265.get(), new Md5TemplateHashPasswordConverter()),
    v9(ArtifactoryServiceVersionProvider.v560m001.get()),
    v10(ArtifactoryServiceVersionProvider.v660m001.get());

    private static final String VERSION_ATT = "version=\"";

    private ArtifactoryServiceVersion version;
    private final XmlConverter[] xmlConverters;

    /**
     * Represents Artifactory security version. For each change in the security files new security version is created.
     *
     * @param version       Artifactory version this security version started at
     * @param xmlConverters List of converters needed to convert the security.xml of this version to the next
     *                      one
     */
    SecurityVersion(ArtifactoryServiceVersion version, XmlConverter... xmlConverters) {
        this.version = version;
        this.xmlConverters = xmlConverters;
    }

    public boolean isLast() {
        return this == SecurityVersion.values()[SecurityVersion.values().length - 1];
    }

    public XmlConverter[] getXmlConverters() {
        return xmlConverters;
    }

    public String convert(String securityXmlAsString) {
        // First create the list of converters to apply
        List<XmlConverter> converters = new ArrayList<>();

        // All converters of versions above me needs to be executed in sequence
        SecurityVersion[] versions = SecurityVersion.values();
        for (SecurityVersion version : versions) {
            if (version.ordinal() >= ordinal() && version.getXmlConverters() != null) {
                converters.addAll(Arrays.asList(version.getXmlConverters()));
            }
        }

        return XmlConverterUtils.convertStrict(converters, securityXmlAsString);
    }

    public static SecurityVersion last() {
        SecurityVersion[] versions = SecurityVersion.values();
        for (SecurityVersion version : versions) {
            if (version.isLast()) {
                return version;
            }
        }
        throw new IllegalStateException("Should have a current version!");
    }

    @Override
    public ArtifactoryServiceVersion getVersion() {
        return version;
    }

    public static SecurityVersion findVersion(String securityData) {
        // Version exists since v4
        int versionIdx = securityData.indexOf(VERSION_ATT);
        if (versionIdx != -1) {
            int versionStartIndex = versionIdx + VERSION_ATT.length();
            String cutVersion = securityData.substring(versionStartIndex);
            String isolatedVersion = cutVersion.substring(0, cutVersion.indexOf('"'));
            return valueOf(isolatedVersion);
        } else {
            // Hack to find old versions
            int groupsIdx = securityData.indexOf("<groups>");
            int userIdx = securityData.indexOf("<user>");
            int simpleUserIdx = securityData.indexOf("SimpleUser>");
            int aclIdIdx = securityData.indexOf("<aclObjectIdentity");
            if (userIdx != -1 || groupsIdx != -1) {
                return v3;
            }
            if (aclIdIdx != -1) {
                return v1;
            }
            if (simpleUserIdx != -1) {
                return v2;
            }
            return unsupported;
        }
    }
}
