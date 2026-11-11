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

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.delegation.ContentSynchronisationDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.jfrog.common.config.diff.DiffElement;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Fred Simon
 */
@Setter
@Getter
@XmlType(name = "RemoteRepoBaseType", propOrder = {"url", "offline", "hardFail", "storeArtifactsLocally",
        "fetchJarsEagerly", "fetchSourcesEagerly", "externalDependencies", "retrievalCachePeriodSecs",
        "metadataRetrievalTimeoutSecs", "assumedOfflinePeriodSecs",
        "missedRetrievalCachePeriodSecs", "checksumPolicyType",
        "unusedArtifactsCleanupPeriodHours", "shareConfiguration", "synchronizeProperties", "listRemoteFolderItems",
        "remoteRepoLayout", "rejectInvalidJars", "nuget", "pypi", "bower", "cocoaPods", "p2OriginalUrl", "vcs", "helm",
        "git", "contentSynchronisation", "blockMismatchingMimeTypes", "mismatchingMimeTypesOverrideList",
        "bypassHeadRequests", "composer", "terraform", "hex", "huggingFace", "go",
        "ansible"},
        namespace = Descriptor.NS)
@GenerateDiffFunction(defaultImpl = HttpRepoDescriptor.class)
public abstract class RemoteRepoDescriptor extends RealRepoDescriptor {

    @XmlElement(defaultValue = "true")
    protected boolean storeArtifactsLocally = true;

    @XmlElement(defaultValue = "false")
    protected boolean fetchJarsEagerly = false;

    @XmlElement(defaultValue = "false")
    protected boolean fetchSourcesEagerly = false;

    @XmlElement
    @Valid
    private ExternalDependenciesConfig externalDependencies;

    @XmlElement(defaultValue = "false")
    protected boolean shareConfiguration = false;

    @XmlElement(required = true)
    @NotUnicode
    @NotBlank
    private String url;

    @XmlElement(defaultValue = "false")
    private boolean hardFail;

    @XmlElement(defaultValue = "false")
    private boolean offline;

    @XmlElement(defaultValue = "600")
    private long retrievalCachePeriodSecs = 7200;//2 hr.

    @XmlElement(defaultValue = "60")
    private long metadataRetrievalTimeoutSecs = 60;//1 min.

    @XmlElement(defaultValue = "300")
    private long assumedOfflinePeriodSecs = 300;   //5 minutes

    @XmlElement(defaultValue = "1800")
    private long missedRetrievalCachePeriodSecs = 1800;//30 min.

    @XmlElement(name = "remoteRepoChecksumPolicyType", defaultValue = "generate-if-absent")
    @DiffElement(name = "remoteRepoChecksumPolicyType")
    private ChecksumPolicyTypeDescriptor checksumPolicyType = ChecksumPolicyTypeDescriptor.GEN_IF_ABSENT;

    @XmlElement(defaultValue = "0")
    private int unusedArtifactsCleanupPeriodHours = 0;

    @XmlElement(defaultValue = "false")
    private boolean synchronizeProperties;

    @XmlElement(defaultValue = "false")
    private boolean listRemoteFolderItems = false;

    @XmlElement(defaultValue = "false")
    private boolean bypassHeadRequests;

    @XmlIDREF
    @XmlElement(name = "remoteRepoLayoutRef")
    @DiffReference
    @Valid
    private RepoLayout remoteRepoLayout;

    @XmlElement(defaultValue = "false")
    private boolean rejectInvalidJars;

    @XmlElement
    @NotUnicode
    private String p2OriginalUrl;

    @Valid
    private NuGetConfiguration nuget;

    @Valid
    private PypiConfiguration pypi;

    @Valid
    private VcsConfiguration vcs;

    @Valid
    private BowerConfiguration bower;

    @Valid
    private ComposerConfiguration composer;

    @Valid
    private CocoaPodsConfiguration cocoaPods;

    @Valid
    private HelmConfiguration helm;

    @Valid
    private GitConfiguration git;

    @Valid
    private TerraformConfiguration terraform;

    @Valid
    private HexConfiguration hex;

    @Valid
    private GoConfiguration go;

    @Valid
    private HuggingFaceMLRemoteConfiguration huggingFace;

    @Valid
    private AnsibleRemoteConfiguration ansible;

    @XmlElement(name = "contentSynchronisation")
    private ContentSynchronisationDescriptor contentSynchronisation;

    @XmlElement(defaultValue = "true", required = true)
    private boolean blockMismatchingMimeTypes = true;

    @XmlElement
    @NotUnicode
    private String mismatchingMimeTypesOverrideList;

    @XmlTransient
    private boolean sendContext = false;
    @XmlTransient
    private boolean passThrough = false;
    @XmlTransient
    private boolean curated = false;

    public boolean isExternalDependenciesConfigEnabled() {
        return externalDependencies != null && externalDependencies.isEnabled();
    }

    public String getExternalDependenciesConfigPatterns() {
        return String.join(",", externalDependencies.getPatterns());
    }

    public String getSymbolServerUrl() {
        return nuget.getSymbolServerUrl();
    }

    public String getV3FeedUrl() {
        return nuget.getV3FeedUrl();
    }

    public String getNugetFeedContextPath() {
        return nuget.getFeedContextPath();
    }

    public String getNugetDownloadContextPath() {
        return nuget.getDownloadContextPath();
    }

    public String getPodsCdnUrl() {
        return this.cocoaPods != null ? this.cocoaPods.getCocoaPodsCdnUrl() : null;
    }

    public String getHexPublicKey() {
        return this.hex != null ? this.hex.getHexPublicKey() : null;
    }

    public String getHexRepositoryUrl() {
        return this.hex != null ? this.hex.getHexRepositoryUrl() : null;
    }

    public String getChartsBaseUrl() {
        return helm != null ? helm.getChartsBaseUrl() : null;
    }

    public boolean isMlRepoLayout() {
        return this.huggingFace != null && this.huggingFace.isMlRepoLayout();
    }

    public boolean isGitLabResolveSubgroups() {
        return this.go != null && this.go.isGitLabResolveSubgroups();
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isCache() {
        return false;
    }

    /**
     * Returns ContentForwarding configuration
     *
     * @return {@link ContentSynchronisationDescriptor}
     */
    public ContentSynchronisationDescriptor getContentSynchronisation() {
        if (contentSynchronisation == null) {
            contentSynchronisation = new ContentSynchronisationDescriptor();
        }
        return contentSynchronisation;
    }

    /**
     * @return whether {@link ContentSynchronisationDescriptor} is enabled (used through reflection by package-handler)
     */
    public boolean isContentSynchronisationEnabled() {
        return getContentSynchronisation().isEnabled();
    }

    public Long getRetrievalCachePeriodSecs() {
        return retrievalCachePeriodSecs;
    }

    public String getPypiRegistryUrl() {
        if (getPypi() == null) {
            setPypi(new PypiConfiguration());
        }
        return getPypi().getPyPIRegistryUrl();
    }

    public String getPypiRepositorySuffix() {
        if (getPypi() == null) {
            setPypi(new PypiConfiguration());
        }
        return getPypi().getRepositorySuffix();
    }

    public void processUrl() {
        // does nothing by default.
    }

    @Override
    public RemoteRepoConfig toRepositoryConfig() {
        throw new UnsupportedOperationException(
                "RemoteRepoDescriptor has no repo config model equivalent. HttpRepoDescriptor#toRepositoryConfig " +
                        "should be used instead.");
    }

    @Nullable
    public String getTerraformRegistryUrl() {
        return terraform != null ? terraform.getTerraformRegistryUrl() : null;
    }

    @Nullable
    public String getTerraformProvidersUrl() {
        return terraform != null ? terraform.getTerraformProvidersUrl() : null;
    }
}