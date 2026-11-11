/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.artifactory.descriptor.repo;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@Slf4j
@XmlType(name = "XrayType", propOrder = {"enabled", "baseUrl", "user", "password", "artifactoryId", "xrayId",
        "allowOperationsXrayUnavailable", "allowBlockedArtifactsDownload", "blockUnscannedTimeoutSeconds",
        "blockUnfinishedScansTimeoutSeconds"})
@GenerateDiffFunction
public class XrayDescriptor implements Descriptor {

    @XmlElement(defaultValue = "true")
    private boolean enabled = true;

    @XmlElement(name = "baseUrl", required = true)
    @NotUnicode
    private String baseUrl;

    @XmlElement(name = "user", required = true)
    @NotUnicode
    private String user;

    @XmlElement(name = "password", required = true)
    @NotUnicode
    private String password;

    @XmlElement(name = "artifactoryId", required = true)
    @NotUnicode
    private String artifactoryId;

    @XmlElement(name = "xrayId", required = true)
    @NotUnicode
    private String xrayId;

    @XmlElement(defaultValue = "false")
    private boolean allowOperationsXrayUnavailable = false;

    @XmlElement(defaultValue = "false")
    private boolean allowBlockedArtifactsDownload = false;

    @XmlElement(defaultValue = "60")
    private int blockUnscannedTimeoutSeconds = 60;

    @XmlElement(defaultValue = "1800")
    private int blockUnfinishedScansTimeoutSeconds = 1800;

    @Deprecated(since = "7.45.0")//Remove when ServicelistLoader is set and use shared.security.servicelist.services'
    public String getBaseUrl() {
        return baseUrl;
    }

    @Deprecated(since = "7.45.0")//Remove when ServicelistLoader is set and use 'shared.security.servicelist.services'
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getArtifactoryId() {
        return artifactoryId;
    }

    public void setArtifactoryId(String artifactoryId) {
        this.artifactoryId = artifactoryId;
    }

    public String getXrayId() {
        return xrayId;
    }

    public void setXrayId(String xrayId) {
        this.xrayId = xrayId;
    }

    public boolean isAllowOperationsXrayUnavailable() {
        return allowOperationsXrayUnavailable;
    }

    public void setAllowOperationsXrayUnavailable(boolean allowOperationsXrayUnavailable) {
        this.allowOperationsXrayUnavailable = allowOperationsXrayUnavailable;
    }

    public boolean isAllowBlockedArtifactsDownload() {
        return allowBlockedArtifactsDownload;
    }

    public void setAllowBlockedArtifactsDownload(boolean allowBlockedArtifactsDownload) {
        this.allowBlockedArtifactsDownload = allowBlockedArtifactsDownload;
    }

    public int getBlockUnscannedTimeoutSeconds() {
        return blockUnscannedTimeoutSeconds;
    }

    public void setBlockUnscannedTimeoutSeconds(int blockUnscannedTimeoutSeconds) {
        this.blockUnscannedTimeoutSeconds = blockUnscannedTimeoutSeconds;
    }

    public int getBlockUnfinishedScansTimeoutSeconds() {
        return blockUnfinishedScansTimeoutSeconds;
    }

    public void setBlockUnfinishedScansTimeoutSeconds(int blockUnfinishedScansTimeoutSeconds) {
        this.blockUnfinishedScansTimeoutSeconds = blockUnfinishedScansTimeoutSeconds;
    }
}
