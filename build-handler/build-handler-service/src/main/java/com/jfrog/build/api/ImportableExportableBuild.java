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

package com.jfrog.build.api;

import com.google.common.collect.Sets;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.io.Serializable;
import java.util.Set;

/**
 * @author Noam Y. Tenne
 */
@XmlRootElement(name = "exported-build")
@XmlAccessorType(XmlAccessType.FIELD)
public class ImportableExportableBuild implements Serializable {
    @XmlAttribute
    private String version;

    private String buildName;
    private String buildNumber;
    private String buildStarted;
    private String buildRepo;
    private String json;
    private Set<String> artifactChecksums;
    private Set<String> dependencyChecksums;

    private long created;
    private long lastModified;
    private String createdBy;
    private String lastModifiedBy;
    private String mimeType;

    public ImportableExportableBuild() {
        // for JAXB
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBuildName() {
        return buildName;
    }

    public void setBuildName(String buildName) {
        this.buildName = buildName;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getBuildStarted() {
        return buildStarted;
    }

    public void setBuildStarted(String buildStarted) {
        this.buildStarted = buildStarted;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public Set<String> getArtifactChecksums() {
        return artifactChecksums;
    }

    public void addArtifactChecksum(String artifactChecksum) {
        if (artifactChecksums == null) {
            artifactChecksums = Sets.newHashSet();
        }
        artifactChecksums.add(artifactChecksum);
    }

    public void setArtifactChecksums(Set<String> artifactChecksums) {
        this.artifactChecksums = artifactChecksums;
    }

    public Set<String> getDependencyChecksums() {
        return dependencyChecksums;
    }

    public void addDependencyChecksum(String dependencyChecksum) {
        if (dependencyChecksums == null) {
            dependencyChecksums = Sets.newHashSet();
        }
        dependencyChecksums.add(dependencyChecksum);
    }

    public void setDependencyChecksums(Set<String> dependencyChecksums) {
        this.dependencyChecksums = dependencyChecksums;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getBuildRepo() {
        return buildRepo;
    }

    public void setBuildRepo(String buildRepo) {
        this.buildRepo = buildRepo;
    }
}
