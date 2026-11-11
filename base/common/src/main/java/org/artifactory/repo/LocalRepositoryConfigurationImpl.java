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

package org.artifactory.repo;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;
import org.artifactory.repo.packagetype.configuration.CargoConfiguration;
import org.artifactory.util.CargoPackageTypeConfigMapper;
import org.artifactory.util.PackageTypeConfigFields;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.common.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.artifactory.descriptor.repo.LocalRepoDescriptor.DEFAULT_DOCKER_TAG_RETENTION;
import static org.artifactory.descriptor.repo.RepoType.Cargo;
import static org.artifactory.descriptor.repo.RepoType.Composer;
import static org.artifactory.descriptor.repo.RepoType.Debian;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Gradle;
import static org.artifactory.descriptor.repo.RepoType.Helm;
import static org.artifactory.descriptor.repo.RepoType.HelmOCI;
import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;
import static org.artifactory.descriptor.repo.RepoType.Ivy;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.NuGet;
import static org.artifactory.descriptor.repo.RepoType.OCI;
import static org.artifactory.descriptor.repo.RepoType.SBT;
import static org.artifactory.descriptor.repo.RepoType.Terraform;
import static org.artifactory.descriptor.repo.RepoType.TerraformBackend;
import static org.artifactory.descriptor.repo.RepoType.YUM;
import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.CALCULATE_YUM_METADATA;
import static org.artifactory.util.PackageTypeConfigFields.DDEB_SUPPORTED;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_TRIVIAL_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_COMPOSER_V1_INDEXING;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_FILE_LISTS_INDEXING;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;
import static org.artifactory.util.PackageTypeConfigFields.OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.artifactory.util.PackageTypeConfigFields.SUPPRESS_POM_CONSISTENCY_CHECKS;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_TYPE;
import static org.artifactory.util.PackageTypeConfigFields.YUM_GROUP_FILENAMES;
import static org.artifactory.util.PackageTypeConfigFields.YUM_ROOT_DEPTH;

/**
 * Local repository configuration
 *
 * @author Tomer Cohen
 * @see org.artifactory.descriptor.repo.LocalRepoDescriptor
 */
@JsonFilter("typeSpecificFilter")
public class LocalRepositoryConfigurationImpl extends RepositoryConfigurationBase
        implements LocalRepositoryConfiguration {

    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = {LOCAL, FEDERATED})
    private String checksumPolicyType = "client-checksums";
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = {LOCAL, FEDERATED})
    private boolean handleReleases = true;
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = {LOCAL, FEDERATED})
    private boolean handleSnapshots = true;
    @IncludeTypeSpecific(packageType = {Maven, NuGet, Gradle, Ivy, SBT}, repoType = {LOCAL, FEDERATED})
    private int maxUniqueSnapshots = 0;
    @IncludeTypeSpecific(packageType = {Docker, OCI, HelmOCI}, repoType = {LOCAL, FEDERATED})
    private int maxUniqueTags = 0;
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = {LOCAL, FEDERATED})
    private String snapshotVersionBehavior = "unique";
    @IncludeTypeSpecific(packageType = {Maven, Gradle, Ivy, SBT}, repoType = {LOCAL, FEDERATED})
    private boolean suppressPomConsistencyChecks = false;
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    private boolean blackedOut = false;
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    private List<String> propertySets;
    @IncludeTypeSpecific(packageType = Debian, repoType = {LOCAL, FEDERATED})
    private List<String> optionalIndexCompressionFormats;
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    private boolean archiveBrowsingEnabled = false;
    @IncludeTypeSpecific(packageType = YUM, repoType = {LOCAL, FEDERATED})
    private boolean calculateYumMetadata = false;
    @IncludeTypeSpecific(packageType = YUM, repoType = {LOCAL, FEDERATED})
    private boolean enableFileListsIndexing = false;
    @IncludeTypeSpecific(packageType = YUM, repoType = {LOCAL, FEDERATED})
    private int yumRootDepth = 0;
    @IncludeTypeSpecific(packageType = YUM, repoType = {LOCAL, FEDERATED})
    private String yumGroupFileNames;
    @IncludeTypeSpecific(packageType = {Docker, OCI, HelmOCI}, repoType = {LOCAL, FEDERATED})
    private int dockerTagRetention = DEFAULT_DOCKER_TAG_RETENTION;
    @IncludeTypeSpecific(packageType = Composer, repoType = {LOCAL, FEDERATED})
    private boolean enableComposerV1Indexing = false;
    @IncludeTypeSpecific(packageType = Terraform, repoType = {LOCAL, FEDERATED})
    private String terraformType = TerraformType.MODULE.getName();

    @IncludeTypeSpecific(packageType = TerraformBackend, repoType = {LOCAL})
    private boolean encryptStates = true;

    // Federation fields - exposed for local repos with federation capability
    // Only serialized when they have actual values (not null/empty/default)
    @Getter
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<FederatedMemberConfiguration> federatedMemberConfigurations;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String proxy;
    @Setter
    @Getter
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean disableProxy;

    public LocalRepositoryConfigurationImpl() {
        setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
    }

    public LocalRepositoryConfigurationImpl(LocalConfig localRepoConfig) {
        this(localRepoConfig, TYPE);
    }

    public LocalRepositoryConfigurationImpl(LocalConfig localRepoConfig, String type) {
        super(localRepoConfig, type);

        setRepoTypeConfig(localRepoConfig);
        xrayRepoConfig = localRepoConfig.getSecurityConfig().getXrayConfig();
        setPackageTypeConfig(localRepoConfig);
    }

    @Override
    public boolean isBlackedOut() {
        return blackedOut;
    }

    public void setBlackedOut(boolean blackedOut) {
        this.blackedOut = blackedOut;
    }

    @Override
    public String getChecksumPolicyType() {
        return checksumPolicyType;
    }

    public void setChecksumPolicyType(String checksumPolicyType) {
        this.checksumPolicyType = checksumPolicyType;
    }

    @Override
    public boolean isHandleReleases() {
        return handleReleases;
    }

    public void setHandleReleases(boolean handleReleases) {
        this.handleReleases = handleReleases;
    }

    @Override
    public boolean isHandleSnapshots() {
        return handleSnapshots;
    }

    public void setHandleSnapshots(boolean handleSnapshots) {
        this.handleSnapshots = handleSnapshots;
    }

    @Override
    public int getMaxUniqueSnapshots() {
        return maxUniqueSnapshots;
    }

    public void setMaxUniqueSnapshots(int maxUniqueSnapshots) {
        this.maxUniqueSnapshots = maxUniqueSnapshots;
    }

    @Override
    public int getMaxUniqueTags() {
        return maxUniqueTags;
    }

    public void setMaxUniqueTags(int maxUniqueTags) {
        this.maxUniqueTags = maxUniqueTags;
    }

    @Override
    public List<String> getPropertySets() {
        return propertySets;
    }

    public void setOptionalIndexCompressionFormats(List<String> optionalIndexCompressionFormats) {
        this.optionalIndexCompressionFormats = optionalIndexCompressionFormats;
    }

    @Override
    public List<String> getOptionalIndexCompressionFormats() {
        return this.optionalIndexCompressionFormats;
    }

    public void setPropertySets(List<String> propertySetKeys) {
        this.propertySets = propertySetKeys;
    }

    @Override
    public String getSnapshotVersionBehavior() {
        return snapshotVersionBehavior;
    }

    public void setSnapshotVersionBehavior(String snapshotVersionBehavior) {
        this.snapshotVersionBehavior = snapshotVersionBehavior;
    }

    @Override
    public boolean isSuppressPomConsistencyChecks() {
        return suppressPomConsistencyChecks;
    }

    public void setSuppressPomConsistencyChecks(boolean suppressPomConsistencyChecks) {
        this.suppressPomConsistencyChecks = suppressPomConsistencyChecks;
    }

    @Override
    public boolean isArchiveBrowsingEnabled() {
        return archiveBrowsingEnabled;
    }

    public void setArchiveBrowsingEnabled(boolean archiveBrowsingEnabled) {
        this.archiveBrowsingEnabled = archiveBrowsingEnabled;
    }

    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @Override
    public boolean isXrayIndex() {
        return xrayRepoConfig != null && xrayRepoConfig.isEnabled();
    }

    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @Override
    public String getXrayNotAvailableMode() {
        if (xrayRepoConfig != null && xrayRepoConfig.getXrayNotAvailableMode() != null) {
            return xrayRepoConfig.getXrayNotAvailableMode().getValue();
        }
        return null;
    }

    public void setXrayIndex(boolean xrayIndex) {
        getLazyXrayConfig().setEnabled(xrayIndex);
    }

    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @Override
    public int getXrayDataTtl() {
        if (xrayRepoConfig != null) {
            return xrayRepoConfig.getDataTtl();
        }
        return DEFAULT_DATA_TTL;
    }

    public void setXrayDataTtl(int xrayDataTtl) {
        getLazyXrayConfig().setDataTtl(xrayDataTtl);
    }

    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @Override
    public boolean isDownloadRedirect() {
        return downloadRedirectConfig != null && downloadRedirectConfig.isEnabled();
    }

    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @Override
    public boolean isCdnRedirect() {
        return cdnRedirectRepoConfig != null && cdnRedirectRepoConfig.isEnabled();
    }

    public void setDownloadRedirect(boolean downloadRedirect) {
        getLazyDownloadRedirectConfig().setEnabled(downloadRedirect);
    }

    public void setCdnRedirect(boolean cdnRedirect) {
        getLazyCdnRedirectConfig().setEnabled(cdnRedirect);
    }

    @IncludeTypeSpecific(packageType = Cargo, repoType = {LOCAL, FEDERATED})
    @Override
    public boolean isCargoAnonymousAccess() {
        return this.cargoConfiguration != null && this.cargoConfiguration.isCargoAnonymousAccess();
    }

    public void setCargoAnonymousAccess(boolean enableCargoAnonymousAccess) {
        getCargoConfiguration().setCargoAnonymousAccess(enableCargoAnonymousAccess);
    }

    /**
     * Unlike other Cargo boolean getters here, a missing {@code cargoConfiguration} (e.g. a create/update request
     * that never touched Cargo-specific fields) must resolve to {@code true} - the sparse HTTP index is the
     * default index type, not the legacy Git index - to match {@link CargoConfiguration}'s own default. Otherwise
     * every such request would silently be treated as requesting the deprecated Git index.
     */
    @IncludeTypeSpecific(packageType = Cargo, repoType = {LOCAL, FEDERATED})
    @Override
    public boolean isCargoInternalIndex() {
        return this.cargoConfiguration == null || this.cargoConfiguration.isCargoInternalIndex();
    }

    public void setCargoInternalIndex(boolean cargoInternalIndex) {
        getCargoConfiguration().setCargoInternalIndex(cargoInternalIndex);
    }

    @Override
    public boolean isCalculateYumMetadata() {
        return calculateYumMetadata;
    }

    public void setCalculateYumMetadata(boolean calculateYumMetadata) {
        this.calculateYumMetadata = calculateYumMetadata;
    }

    public boolean isEnableFileListsIndexing() {
        return enableFileListsIndexing;
    }

    public void setEnableFileListsIndexing(boolean enableFileListsIndexing) {
        this.enableFileListsIndexing = enableFileListsIndexing;
    }

    @Override
    public int getYumRootDepth() {
        return yumRootDepth;
    }

    public void setYumRootDepth(int yumRootDepth) {
        this.yumRootDepth = yumRootDepth;
    }

    @Override
    public String getYumGroupFileNames() {
        return yumGroupFileNames;
    }

    @Override
    public int getDockerTagRetention() {
        return dockerTagRetention;
    }

    @Override
    public String getPackageType() {
        String packageType = super.getPackageType();
        if (packageType.equals(Generic.getType()) && isCalculateYumMetadata()) {
            return YUM.getType();
        }
        return packageType;
    }

    public void setDockerTagRetention(int dockerTagRetention) {
        this.dockerTagRetention = dockerTagRetention;
    }

    public void setYumGroupFileNames(String yumGroupFileNames) {
        this.yumGroupFileNames = yumGroupFileNames;
    }

    public boolean isEnableComposerV1Indexing() {
        return enableComposerV1Indexing;
    }

    public void setEnableComposerV1Indexing(boolean enableComposerV1Indexing) {
        this.enableComposerV1Indexing = enableComposerV1Indexing;
    }

    @Override
    public String getTerraformType() {
        return terraformType;
    }

    @Override
    public boolean isEncryptStates() {
        return encryptStates;
    }

    public void setEncryptStates(boolean encryptStates) {
        this.encryptStates = encryptStates;
    }

    public void setTerraformType(String terraformType) {
        this.terraformType = terraformType;
    }

    private void setRepoTypeConfig(LocalConfig localRepoConfig) {
        LocalRepoChecksumPolicyType newChecksumPolicyType = localRepoConfig.getRepoTypeConfig().getChecksumPolicyType();
        setChecksumPolicyType(newChecksumPolicyType.getMessage());
        setHandleReleases(localRepoConfig.getRepoTypeConfig().isHandleReleases());
        setHandleSnapshots(localRepoConfig.getRepoTypeConfig().isHandleSnapshots());
        setMaxUniqueSnapshots(localRepoConfig.getRepoTypeConfig().getMaxUniqueSnapshots());
        setBlackedOut(localRepoConfig.getRepoTypeConfig().isBlackedOut());
        setPriorityResolution(localRepoConfig.getRepoTypeConfig().isPriorityResolution());
        setArchiveBrowsingEnabled(localRepoConfig.getRepoTypeConfig().isArchiveBrowsingEnabled());
        downloadRedirectConfig = localRepoConfig.getRepoTypeConfig().getDownloadRedirectConfig();
        cdnRedirectRepoConfig = localRepoConfig.getRepoTypeConfig().getCdnRedirectRepoConfig();

        Map<String, String> snapshotBehaviours = extractXmlValueFromEnumAnnotations(SnapshotVersionBehavior.class);
        for (Map.Entry<String, String> annotationToField : snapshotBehaviours.entrySet()) {
            if (annotationToField.getValue()
                    .equals(localRepoConfig.getRepoTypeConfig().getSnapshotVersionBehavior().name())) {
                setSnapshotVersionBehavior(annotationToField.getKey());
            }
        }

        List<String> newPropertySets = localRepoConfig.getRepoTypeConfig().getPropertySetRefs();
        if (newPropertySets != null && !newPropertySets.isEmpty()) {
            setPropertySets(newPropertySets);
        } else {
            setPropertySets(Lists.newArrayList());
        }
    }

    private void setDebianConfigIfNeeded(LocalConfig localRepoConfig) {
        if (Debian == localRepoConfig.getPackageType()) {
            String optionalIndexFormats = localRepoConfig.getPackageTypeConfig()
                    .get(OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey());
            if (StringUtils.isBlank(optionalIndexFormats)) {
                setOptionalIndexCompressionFormats(null);
            } else {
                setOptionalIndexCompressionFormats(
                        JsonUtils.getInstance().readValue(optionalIndexFormats, new TypeReference<>() {
                        }));
            }
            String debianTrivialLayout = localRepoConfig.getPackageTypeConfig().get(DEBIAN_TRIVIAL_LAYOUT.getKey());
            if (StringUtils.isNotBlank(debianTrivialLayout)) {
                setDebianTrivialLayout(Boolean.parseBoolean(debianTrivialLayout));
            }
            String ddebSupported = localRepoConfig.getPackageTypeConfig().get(DDEB_SUPPORTED.getKey());
            if (StringUtils.isNotBlank(ddebSupported)) {
                setDdebSupported(Boolean.parseBoolean(ddebSupported));
            }
        }
    }

    private void setMavenConfigIfNeeded(LocalConfig localRepoConfig) {
        if (localRepoConfig.getPackageType().isMavenGroup()) {
            String newSuppressPomConsistencyChecks =
                    localRepoConfig.getPackageTypeConfig().get(SUPPRESS_POM_CONSISTENCY_CHECKS.getKey());
            if (StringUtils.isNotBlank(newSuppressPomConsistencyChecks)) {
                setSuppressPomConsistencyChecks(Boolean.parseBoolean(newSuppressPomConsistencyChecks));
            }
        }
    }

    private void setDockerConfigIfNeeded(LocalConfig localRepoConfig) {
        if (localRepoConfig.getPackageType().isDockerGroup()) {
            String newMaxUniqueTags =
                    localRepoConfig.getPackageTypeConfig().get(PackageTypeConfigFields.MAX_UNIQUE_TAGS.getKey());
            if (StringUtils.isNotBlank(newMaxUniqueTags)) {
                setMaxUniqueTags(Integer.parseInt(newMaxUniqueTags));
            }
            String newDockerTagRetention =
                    localRepoConfig.getPackageTypeConfig().get(PackageTypeConfigFields.DOCKER_TAG_RETENTION.getKey());
            if (StringUtils.isNotBlank(newDockerTagRetention)) {
                setDockerTagRetention(Integer.parseInt(newDockerTagRetention));
            }
        }
    }

    private void setYumConfigIfNeeded(LocalConfig localRepoConfig) {
        if (YUM == localRepoConfig.getPackageType()) {
            String newCalculateYumMetadata =
                    localRepoConfig.getPackageTypeConfig().get(CALCULATE_YUM_METADATA.getKey());
            if (StringUtils.isNotBlank(newCalculateYumMetadata)) {
                setCalculateYumMetadata(Boolean.parseBoolean(newCalculateYumMetadata));
            }
            String newEnableFileListsIndexing =
                    localRepoConfig.getPackageTypeConfig().get(ENABLE_FILE_LISTS_INDEXING.getKey());
            if (StringUtils.isNotBlank(newEnableFileListsIndexing)) {
                setEnableFileListsIndexing(Boolean.parseBoolean(newEnableFileListsIndexing));
            }
            String newYumRootDepth = localRepoConfig.getPackageTypeConfig().get(YUM_ROOT_DEPTH.getKey());
            if (StringUtils.isNotBlank(newYumRootDepth)) {
                setYumRootDepth(Integer.parseInt(newYumRootDepth));
            }
            setYumGroupFileNames(
                    localRepoConfig.getPackageTypeConfig().get(YUM_GROUP_FILENAMES.getKey()));
        }
    }

    private void setCargoConfigIfNeeded(LocalConfig localRepoConfig) {
        if (Cargo == localRepoConfig.getPackageType()) {
            cargoConfiguration =
                    CargoPackageTypeConfigMapper.INSTANCE.map(localRepoConfig.getPackageTypeConfig());
        }
    }

    private void setTerraformTypeIfNeeded(LocalConfig localRepoConfig) {
        if (Terraform == localRepoConfig.getPackageType()) {
            setTerraformType(localRepoConfig.getPackageTypeConfig().get(TERRAFORM_TYPE.getKey()));
        }
    }

    private void setEncryptStatesIfNeeded(LocalConfig localRepoConfig) {
        if (TerraformBackend == localRepoConfig.getPackageType()) {
            String encryptStatesStr = localRepoConfig.getPackageTypeConfig().get(ENCRYPT_STATES.getKey());
            if (StringUtils.isNotBlank(encryptStatesStr)) {
                setEncryptStates(Boolean.parseBoolean(encryptStatesStr));
            }
        }
    }

    private void setComposerConfigIfNeeded(LocalConfig localRepoConfig) {
        if (Composer == localRepoConfig.getPackageType()) {
            String newEnableComposerV1Indexing = localRepoConfig.getPackageTypeConfig()
                    .get(ENABLE_COMPOSER_V1_INDEXING.getKey());
            setEnableComposerV1Indexing(Boolean.parseBoolean(newEnableComposerV1Indexing));
        }
    }

    private void setPackageTypeConfig(LocalConfig localRepoConfig) {
        setDockerConfigIfNeeded(localRepoConfig);
        setMavenConfigIfNeeded(localRepoConfig);
        setDebianConfigIfNeeded(localRepoConfig);
        setCargoConfigIfNeeded(localRepoConfig);
        setYumConfigIfNeeded(localRepoConfig);
        setTerraformTypeIfNeeded(localRepoConfig);
        setComposerConfigIfNeeded(localRepoConfig);
        setEncryptStatesIfNeeded(localRepoConfig);
        setNugetTypeSpecificConfigIfNeeded(localRepoConfig);
        setEnforceLayoutConfigIfNeeded(localRepoConfig);
        setMLRepoLayoutConfigIfNeeded(localRepoConfig);
    }

    private void setNugetTypeSpecificConfigIfNeeded(LocalConfig localRepoConfig) {
        if (NuGet == localRepoConfig.getPackageType()) {
            Optional.of(localRepoConfig)
                    .map(LocalConfig::getPackageTypeConfig)
                    .map(packageTypeConfig -> packageTypeConfig.get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()))
                    .map(Boolean::parseBoolean)
                    .ifPresent(this::setEnableNormalizedVersion);
        }
    }

    protected void setEnforceLayoutConfigIfNeeded(LocalConfig localRepoConfig) {
        if (Helm == localRepoConfig.getPackageType()) {
            setForceNonDuplicateChart(Boolean.parseBoolean(
                    localRepoConfig.getPackageTypeConfig().get(FORCE_NON_DUPLICATE_CHART)));
            setForceMetadataNameVersion(Boolean.parseBoolean(
                    localRepoConfig.getPackageTypeConfig().get(FORCE_METADATA_NAME_VERSION)));
        }
    }

    protected void setMLRepoLayoutConfigIfNeeded(LocalConfig localRepoConfig) {
        if (HuggingFaceML == localRepoConfig.getPackageType()) {
            setMlRepoLayout(
                    Boolean.parseBoolean(localRepoConfig.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT)));
        }
    }

    public void setFederatedMembers(List<FederatedMemberConfiguration> members) {
        this.federatedMemberConfigurations = members;
    }

}