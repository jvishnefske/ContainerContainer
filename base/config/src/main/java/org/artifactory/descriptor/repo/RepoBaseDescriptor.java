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

package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.common.config.diff.DiffElement;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Fred Simon
 */
@XmlType(name = "RepoType", propOrder = {"key", "type", "description", "notes", "includesPattern", "excludesPattern",
        "repoLayout", "gridTopologyId", "dockerApiVersion", "forceNugetAuthentication", "forceConanAuthentication",
        "ddebSupported", "cargoConfig", "primaryKeyPairDescriptor", "secondaryKeyPairDescriptor", "signedUrlTtl"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public abstract class RepoBaseDescriptor implements RepoDescriptor {

    public static final long DEFAULT_SIGNED_URL_TTL = 90;

    @XmlID
    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String key;

    @XmlElement(required = true)
    private RepoType type = RepoType.Generic;

    @XmlElement(required = false)
    @NotUnicode
    private String description;

    @XmlElement(required = false)
    @NotUnicode
    private String notes;

    @XmlElement(defaultValue = "**/*", required = false)
    @NotUnicode
    private String includesPattern = "**/*";

    @XmlElement(defaultValue = "", required = false)
    @NotUnicode
    private String excludesPattern;

    @XmlIDREF
    @XmlElement(name = "repoLayoutRef")
    @DiffReference
    @Valid
    private RepoLayout repoLayout;

    @XmlElement(required = false)
    @NotUnicode
    private String gridTopologyId;

    @XmlElement(defaultValue = "V2", required = false)
    private DockerApiVersion dockerApiVersion = DockerApiVersion.V2;

    private boolean forceNugetAuthentication;
    @XmlTransient
    @Getter
    @Setter
    private boolean enableNormalizedVersion;
    @XmlTransient
    private boolean forceP2Authentication;
    private boolean forceConanAuthentication;

    private boolean ddebSupported;

    @Getter
    @Setter
    @XmlTransient
    private String dockerProjectId = StringUtils.EMPTY;

    @XmlElement(name = "cargoConfig", required = false)
    @DiffElement(name = "cargoConfig")
    private CargoConfiguration cargoConfig;

    @XmlIDREF
    @XmlElement(name = "primaryKeyPairRef")
    @DiffReference
    @Valid
    private KeyPairDescriptor primaryKeyPairDescriptor;

    @XmlIDREF
    @XmlElement(name = "secondaryKeyPairRef")
    @DiffReference
    @Valid
    private KeyPairDescriptor secondaryKeyPairDescriptor;

    @XmlElement(defaultValue = "90", required = false)
    private long signedUrlTtl = DEFAULT_SIGNED_URL_TTL;

    @Override
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public RepoType getType() {
        return type;
    }

    public void setType(RepoType type) {
        this.type = type;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String getIncludesPattern() {
        return includesPattern;
    }

    public void setIncludesPattern(String includesPattern) {
        this.includesPattern = includesPattern;
    }

    @Override
    public String getExcludesPattern() {
        return excludesPattern;
    }

    public void setExcludesPattern(String excludesPattern) {
        this.excludesPattern = excludesPattern;
    }

    @Override
    public RepoLayout getRepoLayout() {
        return repoLayout;
    }

    public void setRepoLayout(RepoLayout repoLayout) {
        this.repoLayout = repoLayout;
    }

    public String getGridTopologyId() {
        return gridTopologyId;
    }

    public void setGridTopologyId(String gridTopologyId) {
        this.gridTopologyId = gridTopologyId;
    }

    @Override
    public DockerApiVersion getDockerApiVersion() {
        return dockerApiVersion;
    }

    public void setDockerApiVersion(String dockerApiVersion) {
        this.dockerApiVersion = DockerApiVersion.valueOf(dockerApiVersion);
    }

    @Override
    public boolean isForceNugetAuthentication() {
        return forceNugetAuthentication;
    }

    @Override
    public boolean isForceConanAuthentication() {
        return forceConanAuthentication;
    }

    @Override
    public boolean isDdebSupported() {
        return ddebSupported;
    }

    public void setDdebSupported(boolean ddebSupported) {
        this.ddebSupported = ddebSupported;
    }

    public void setForceNugetAuthentication(boolean forceNugetAuthentication) {
        this.forceNugetAuthentication = forceNugetAuthentication;
    }

    public boolean isForceP2Authentication() {
        return forceP2Authentication;
    }

    public void setForceP2Authentication(boolean forceP2Authentication) {
        this.forceP2Authentication = forceP2Authentication;
    }

    public void setForceConanAuthentication(boolean forceConanAuthentication) {
        this.forceConanAuthentication = forceConanAuthentication;
    }

    public CargoConfiguration getCargoConfig() {
        return cargoConfig;
    }

    public void setCargoConfig(CargoConfiguration cargoConfig) {
        this.cargoConfig = cargoConfig;
    }

    public boolean isCargoAnonymousAccess() {
        return this.cargoConfig != null && this.cargoConfig.isCargoAnonymousAccess();
    }

    /**
     * Unlike {@link #isCargoAnonymousAccess()}, a missing {@code cargoConfig} (e.g. a pre-existing repo persisted
     * before this field existed, or one that never touched Cargo-specific settings) must resolve to {@code true} -
     * the sparse HTTP index is the default index type, not the legacy Git index - to match
     * {@link CargoConfiguration}'s own default. Otherwise every such repo would silently be treated as being on
     * the deprecated Git index.
     */
    public boolean isCargoInternalIndex() {
        return this.cargoConfig == null || this.cargoConfig.isCargoInternalIndex();
    }

    @Override
    public KeyPairDescriptor getPrimaryKeyPairDescriptor() {
        return primaryKeyPairDescriptor;
    }

    public void setPrimaryKeyPairDescriptor(KeyPairDescriptor keyPair) {
        this.primaryKeyPairDescriptor = keyPair;
    }

    @Override
    public KeyPairDescriptor getSecondaryKeyPairDescriptor() {
        return secondaryKeyPairDescriptor;
    }

    public void setSecondaryKeyPairDescriptor(KeyPairDescriptor keyPair) {
        this.secondaryKeyPairDescriptor = keyPair;
    }

    @Override
    public boolean isMavenRepoLayout() {
        return RepoLayoutUtils.isDefaultM2(repoLayout);
    }

    @Override
    public long getSignedUrlTtl() {
        return this.signedUrlTtl;
    }

    public void setSignedUrlTtl(long signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }

    public void promoteKeyPair() {
        if (secondaryKeyPairDescriptor != null) {
            primaryKeyPairDescriptor = secondaryKeyPairDescriptor;
            secondaryKeyPairDescriptor = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RepoBaseDescriptor)) {
            return false;
        }
        RepoBaseDescriptor that = (RepoBaseDescriptor) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public int compareTo(Object o) {
        if (o instanceof RepoDescriptor) {
            return key.compareTo(((RepoDescriptor) o).getKey());
        } else {
            return key.compareTo(o.toString());
        }
    }

    public abstract RepoConfig toRepositoryConfig();

}
