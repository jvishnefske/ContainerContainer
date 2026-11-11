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

import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.util.YumPackageTypeConfigMapper;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.artifactory.descriptor.repo.RepoType.fromType;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;

/**
 * @author Fred Simon
 */
@XmlType(name = "LocalRepoType",
        propOrder = {"snapshotVersionBehavior", "checksumPolicyType", "calculateYumMetadata", "yumRootDepth",
                "yumGroupFileNames", "debianTrivialLayout", "enableFileListsIndexing",
                "optionalIndexCompressionFormats", "dockerTagRetention", "enableComposerV1Indexing", "terraformType"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@XmlRootElement
public class LocalRepoDescriptor extends RealRepoDescriptor {

    public LocalRepoDescriptor() {
    }

    public LocalRepoDescriptor(LocalConfig repositoryConfig, List<RepoLayout> repoLayouts,
            List<PropertySet> propertySets, List<KeyPairDescriptor> keyPairs) {
        setKey(repositoryConfig.getKey());
        setType(repositoryConfig.getPackageType());
        setBaseConfig(repositoryConfig.getBaseConfig(), repoLayouts);
        setRepoTypeConfig(repositoryConfig.getRepoTypeConfig(), propertySets);
        setPackageTypeConfig(repositoryConfig.getPackageTypeConfig(), repositoryConfig.getPackageType());
        setSecurityConfig(repositoryConfig.getSecurityConfig(), keyPairs);
    }

    public LocalConfig toRepositoryConfig() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(getKey(), getType());
        buildBaseConfig(localRepoConfig.getBaseConfig());
        buildRepoTypeConfig(localRepoConfig.getRepoTypeConfig());
        buildSecurityConfig(localRepoConfig.getSecurityConfig());
        buildPackageTypeConfig(getType(), localRepoConfig.getPackageTypeConfig());
        return localRepoConfig;
    }

    public static LocalRepoDescriptor fromRepositoryConfig(CentralConfigDescriptor centralConfigDescriptor,
            LocalConfig sourceRepoConfig) {
        return new LocalRepoDescriptor(sourceRepoConfig, centralConfigDescriptor.getRepoLayouts(),
                centralConfigDescriptor.getPropertySets(),
                centralConfigDescriptor.getKeyPairs());
    }

    public static final int DEFAULT_DOCKER_TAG_RETENTION = 1;

    @XmlElement(defaultValue = "unique", required = false)
    private SnapshotVersionBehaviorDescriptor snapshotVersionBehavior = SnapshotVersionBehaviorDescriptor.UNIQUE;

    @XmlElement(name = "localRepoChecksumPolicyType", defaultValue = "client-checksums", required = false)
    private LocalRepoChecksumPolicyTypeDescriptor checksumPolicyType = LocalRepoChecksumPolicyTypeDescriptor.CLIENT;

    private boolean calculateYumMetadata;

    private boolean enableFileListsIndexing;

    private int yumRootDepth;

    @NotUnicode
    private String yumGroupFileNames;

    private boolean debianTrivialLayout = false;

    @XmlElement(defaultValue = "1", required = false)
    private int dockerTagRetention = DEFAULT_DOCKER_TAG_RETENTION;

    @XmlElementWrapper(name = "optionalIndexCompressionFormats")
    @XmlElement(name = "debianFormat", type = String.class)
    private List<@NotUnicode String> optionalIndexCompressionFormats;

    private boolean enableComposerV1Indexing = false;

    @NotUnicode
    private String terraformType = TerraformType.MODULE.getName();

    @XmlTransient
    @Getter
    @Setter
    private boolean encryptStates = true;
    @XmlTransient
    @Getter
    @Setter
    private boolean forceNonDuplicateChart;
    @XmlTransient
    @Getter
    @Setter
    private boolean forceMetadataNameVersion;

    @XmlTransient
    @Getter
    @Setter
    private boolean mlRepoLayout;

    public SnapshotVersionBehaviorDescriptor getSnapshotVersionBehavior() {
        return snapshotVersionBehavior;
    }

    public void setSnapshotVersionBehavior(SnapshotVersionBehaviorDescriptor snapshotVersionBehavior) {
        this.snapshotVersionBehavior = snapshotVersionBehavior;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isCache() {
        return false;
    }

    public LocalRepoChecksumPolicyTypeDescriptor getChecksumPolicyType() {
        return checksumPolicyType;
    }

    public void setChecksumPolicyType(LocalRepoChecksumPolicyTypeDescriptor checksumPolicyType) {
        this.checksumPolicyType = checksumPolicyType;
    }

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

    public int getYumRootDepth() {
        return yumRootDepth;
    }

    public void setYumRootDepth(int yumRootDepth) {
        this.yumRootDepth = yumRootDepth;
    }

    public String getYumGroupFileNames() {
        return yumGroupFileNames;
    }

    public void setYumGroupFileNames(String yumGroupFileNames) {
        this.yumGroupFileNames = yumGroupFileNames;
    }

    public boolean isDebianTrivialLayout() {
        return debianTrivialLayout;
    }

    public void setDebianTrivialLayout(boolean debianTrivialLayout) {
        this.debianTrivialLayout = debianTrivialLayout;
    }

    public void setOptionalIndexCompressionFormats(List<String> optionalIndexCompressionFormats) {
        this.optionalIndexCompressionFormats = optionalIndexCompressionFormats;
    }

    public List<String> getOptionalIndexCompressionFormats() {
        return this.optionalIndexCompressionFormats;
    }

    public int getDockerTagRetention() {
        return dockerTagRetention;
    }

    public void setDockerTagRetention(int dockerTagRetention) {
        this.dockerTagRetention = dockerTagRetention;
    }

    public boolean isEnableComposerV1Indexing() {
        return enableComposerV1Indexing;
    }

    public void setEnableComposerV1Indexing(boolean enableComposerV1Indexing) {
        this.enableComposerV1Indexing = enableComposerV1Indexing;
    }

    public String getTerraformType() {
        return terraformType;
    }

    public void setTerraformType(String terraformType) {
        this.terraformType = terraformType;
    }

    private void setBaseConfig(BaseConfig baseConfig, List<RepoLayout> repoLayouts) {
        setDescription(baseConfig.getDescription());
        setNotes(baseConfig.getNotes());
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(baseConfig.getRepoLayoutRef())) {
                setRepoLayout(repoLayout);
            }
        }
        setIncludesPattern(baseConfig.getIncludesPattern());
        setExcludesPattern(baseConfig.getExcludesPattern());
        setGridTopologyId(baseConfig.getFederationConfig().getGridTopologyId());
    }

    private void buildBaseConfig(@Valid BaseConfig baseConfig) {
        baseConfig.setDescription(getDescription());
        baseConfig.setNotes(getNotes());
        if (getRepoLayout() != null) {
            baseConfig.setRepoLayoutRef(getRepoLayout().getName());
        }
        baseConfig.setIncludesPattern(getIncludesPattern());
        baseConfig.setExcludesPattern(getExcludesPattern());
        baseConfig.setGridTopologyId(getGridTopologyId());
    }

    private void setRepoTypeConfig(@Nonnull LocalRepositoryRepoTypeConfig localConfig,
            @Nonnull List<PropertySet> propertySets) {
        setSnapshotVersionBehavior(
                SnapshotVersionBehaviorDescriptor.valueOf(localConfig.getSnapshotVersionBehavior().name()));
        setHandleReleases(localConfig.isHandleReleases());
        setHandleSnapshots(localConfig.isHandleSnapshots());
        setMaxUniqueSnapshots(localConfig.getMaxUniqueSnapshots());
        setCdnRedirectConfig(buildCdnRedirectConfig(localConfig.getCdnRedirectRepoConfig()));
        setDownloadRedirectConfig(buildDownloadRedirectConfigLocal(localConfig.getDownloadRedirectConfig()));
        setChecksumPolicyType(
                LocalRepoChecksumPolicyTypeDescriptor.valueOf(localConfig.getChecksumPolicyType().name()));

        List<String> propertySetRefs = localConfig.getPropertySetRefs();
        ArrayList<PropertySet> newPropertySets = new ArrayList<>();
        for (String propertySetRef : propertySetRefs) {
            for (PropertySet propertySet : propertySets) {
                if (propertySet.getName().equals(propertySetRef)) {
                    newPropertySets.add(propertySet);
                }
            }
        }
        setPropertySets(newPropertySets);
        setArchiveBrowsingEnabled(localConfig.isArchiveBrowsingEnabled());
        setBlackedOut(localConfig.isBlackedOut());
        setPriorityResolution(localConfig.isPriorityResolution());
    }

    protected void buildRepoTypeConfig(@Nonnull LocalRepositoryRepoTypeConfig localRepoConfig) {
        localRepoConfig.setSnapshotVersionBehavior(
                org.artifactory.repo.packagetype.SnapshotVersionBehavior.valueOf(getSnapshotVersionBehavior().name()));
        localRepoConfig.setMaxUniqueSnapshots(getMaxUniqueSnapshots());
        localRepoConfig.setHandleReleases(isHandleReleases());
        localRepoConfig.setHandleSnapshots(isHandleSnapshots());
        if (getCdnRedirectRepoConfig() != null) {
            org.artifactory.repo.CdnRedirectRepoConfig cdnRedirectRepoConfig =
                    new org.artifactory.repo.CdnRedirectRepoConfig();
            cdnRedirectRepoConfig.setEnabled(getCdnRedirectRepoConfig().isEnabled());
            localRepoConfig.setCdnRedirectRepoConfig(cdnRedirectRepoConfig);
        }
        if (getDownloadRedirectConfig() != null) {
            org.artifactory.repo.DownloadRedirectRepoConfig downloadRedirectConfig =
                    new org.artifactory.repo.DownloadRedirectRepoConfig();
            downloadRedirectConfig.setEnabled(getDownloadRedirectConfig().isEnabled());
            localRepoConfig.setDownloadRedirectConfig(downloadRedirectConfig);
        }
        List<String> propertySetRefs = new ArrayList<>();
        getPropertySets().forEach(propertySet -> propertySetRefs.add(propertySet.getName()));
        localRepoConfig.setPropertySetRefs(propertySetRefs);
        localRepoConfig.setBlackedOut(isBlackedOut());
        localRepoConfig.setArchiveBrowsingEnabled(isArchiveBrowsingEnabled());
        localRepoConfig.setChecksumPolicyType(
                org.artifactory.repo.local.LocalRepoChecksumPolicyType.valueOf(getChecksumPolicyType().name()));
        localRepoConfig.setPriorityResolution(isPriorityResolution());
    }

    protected void setPackageTypeConfig(Map<String, String> packageTypeConfig, RepoType packageType) {
        if (packageType.isMavenGroup()) {
            setSuppressPomConsistencyChecks(
                    Boolean.parseBoolean(packageTypeConfig.get("suppressPomConsistencyChecks")));
            if (RepoType.P2.equals(packageType)) {
                setForceP2Authentication(Boolean.parseBoolean(packageTypeConfig.get("forceP2Authentication")));
            }
        } else if (RepoType.Cargo.equals(packageType)) {
            CargoConfiguration cargoConfiguration = new CargoConfiguration();
            cargoConfiguration
                    .setCargoAnonymousAccess(Boolean.parseBoolean(packageTypeConfig.get("cargoAnonymousAccess")));
            cargoConfiguration
                    .setCargoInternalIndex(Boolean.parseBoolean(packageTypeConfig.get("cargoInternalIndex")));
            setCargoConfig(cargoConfiguration);
        } else if (RepoType.Composer.equals(packageType)) {
            setEnableComposerV1Indexing(
                    Boolean.parseBoolean(packageTypeConfig.get("enableComposerV1Indexing")));
        } else if (RepoType.Debian.equals(packageType)) {
            setDebianTrivialLayout(Boolean.parseBoolean(packageTypeConfig.get("debianTrivialLayout")));
            if (packageTypeConfig.get("optionalIndexCompressionFormats") != null) {
                setOptionalIndexCompressionFormats(
                        RepositoryConfigSource.jsonStringToList(packageTypeConfig, "optionalIndexCompressionFormats"));
            }
            setDdebSupported(Boolean.parseBoolean(packageTypeConfig.get("ddebSupported")));
        } else if (packageType.isDockerGroup()) {
            setDockerTagRetention(Integer.parseInt(packageTypeConfig
                    .getOrDefault("dockerTagRetention", String.valueOf(DEFAULT_DOCKER_TAG_RETENTION))));
            setDockerApiVersion(packageTypeConfig.get("dockerApiVersion"));
            setBlockPushingSchema1(Boolean.parseBoolean(packageTypeConfig.get("blockPushingSchema1")));
            setMaxUniqueTags(Integer.parseInt(packageTypeConfig
                    .getOrDefault("maxUniqueTags", "0")));
        } else if (RepoType.NuGet.equals(packageType)) {
            setNugetSpecificConfig(packageTypeConfig);
        } else if (RepoType.YUM.equals(fromType(packageType.getType()))) {
            setCalculateYumMetadata(
                    Boolean.parseBoolean(packageTypeConfig.get(YumPackageTypeConfigMapper.CALCULATE_YUM_METADATA)));
            setEnableFileListsIndexing(
                    Boolean.parseBoolean(packageTypeConfig.get(YumPackageTypeConfigMapper.ENABLE_FILE_LISTS_INDEXING)));
            setYumRootDepth(
                    Integer.parseInt(packageTypeConfig.getOrDefault(YumPackageTypeConfigMapper.YUM_ROOT_DEPTH, "0")));
            setYumGroupFileNames(packageTypeConfig.get(YumPackageTypeConfigMapper.YUM_GROUP_FILE_NAMES));
        } else if (RepoType.Terraform.equals(packageType)) {
            setTerraformType(packageTypeConfig.get("terraformType"));
        } else if (RepoType.Conan.equals(packageType)) {
            setForceConanAuthentication(Boolean.parseBoolean(packageTypeConfig.get("forceConanAuthentication")));
        } else if (RepoType.TerraformBackend.equals(packageType)) {
            setEncryptStates(Boolean.parseBoolean(packageTypeConfig.get(ENCRYPT_STATES.getKey())));
        } else if (RepoType.Helm.equals(packageType)) {
            setForceMetadataNameVersion(Boolean.parseBoolean(packageTypeConfig.get(FORCE_METADATA_NAME_VERSION)));
            setForceNonDuplicateChart(Boolean.parseBoolean(packageTypeConfig.get(FORCE_NON_DUPLICATE_CHART)));
        } else if (RepoType.HuggingFaceML.equals(packageType)) {
            setMlRepoLayout(Boolean.parseBoolean(packageTypeConfig.get(IS_ML_REPO_LAYOUT)));
        }
    }

    private void setNugetSpecificConfig(Map<String, String> packageTypeConfig) {
        setForceNugetAuthentication(
                Boolean.parseBoolean(packageTypeConfig.get("forceNugetAuthentication")));
        setEnableNormalizedVersion(
                Boolean.parseBoolean(
                        packageTypeConfig.getOrDefault(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), "false")));
    }

    protected void buildPackageTypeConfig(RepoType packageType,
            Map<String, String> packageTypeConfig) {
        if (packageType.isMavenGroup()) {
            packageTypeConfig
                    .put("suppressPomConsistencyChecks", String.valueOf(isSuppressPomConsistencyChecks()));
            if (RepoType.P2.equals(packageType)) {
                packageTypeConfig.put("forceP2Authentication", String.valueOf(isForceP2Authentication()));
            }
        } else if (RepoType.Cargo.equals(packageType)) {
            packageTypeConfig.put("cargoAnonymousAccess", String.valueOf(isCargoAnonymousAccess()));
            packageTypeConfig.put("cargoInternalIndex", String.valueOf(isCargoInternalIndex()));
        } else if (RepoType.Composer.equals(packageType)) {
            packageTypeConfig.put("enableComposerV1Indexing", String.valueOf(isEnableComposerV1Indexing()));
        } else if (RepoType.Debian.equals(packageType)) {
            packageTypeConfig.put("debianTrivialLayout", String.valueOf(isDebianTrivialLayout()));
            packageTypeConfig.put("ddebSupported", String.valueOf(isDdebSupported()));
            RepositoryConfigSource.entityToFlatMapKeys(getOptionalIndexCompressionFormats(),
                    "optionalIndexCompressionFormats", packageTypeConfig);
        } else if (packageType.isDockerGroup()) {
            packageTypeConfig.put("dockerTagRetention", String.valueOf(getDockerTagRetention()));
            packageTypeConfig.put("dockerApiVersion", getDockerApiVersion().toString());
            packageTypeConfig.put("blockPushingSchema1", String.valueOf(isBlockPushingSchema1()));
            packageTypeConfig.put("maxUniqueTags", String.valueOf(getMaxUniqueTags()));
        } else if (RepoType.NuGet.equals(packageType)) {
            buildNugetSpecificConfig(packageTypeConfig);
        } else if (RepoType.YUM.equals(fromType(packageType.getType()))) {
            packageTypeConfig.put(YumPackageTypeConfigMapper.CALCULATE_YUM_METADATA,
                    String.valueOf(isCalculateYumMetadata()));
            packageTypeConfig.put(YumPackageTypeConfigMapper.YUM_ROOT_DEPTH, String.valueOf(getYumRootDepth()));
            packageTypeConfig.put(YumPackageTypeConfigMapper.ENABLE_FILE_LISTS_INDEXING,
                    String.valueOf(isEnableFileListsIndexing()));
            if (getYumGroupFileNames() != null) {
                packageTypeConfig.put(YumPackageTypeConfigMapper.YUM_GROUP_FILE_NAMES,
                        String.valueOf(getYumGroupFileNames()));
            }
        } else if (RepoType.Terraform.equals(packageType)) {
            packageTypeConfig.put("terraformType", getTerraformType());
        } else if (RepoType.Conan.equals(packageType)) {
            packageTypeConfig.put("forceConanAuthentication", String.valueOf(isForceConanAuthentication()));
        } else if (RepoType.TerraformBackend.equals(packageType)) {
            packageTypeConfig.put(ENCRYPT_STATES.getKey(), String.valueOf(isEncryptStates()));
        } else if (RepoType.Helm.equals(packageType)) {
            packageTypeConfig.put(FORCE_METADATA_NAME_VERSION, String.valueOf(isForceMetadataNameVersion()));
            packageTypeConfig.put(FORCE_NON_DUPLICATE_CHART, String.valueOf(isForceNonDuplicateChart()));
        } else if (RepoType.HuggingFaceML.equals(packageType)) {
            packageTypeConfig.put(IS_ML_REPO_LAYOUT, String.valueOf(isMlRepoLayout()));
        }
    }

    private void buildNugetSpecificConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("forceNugetAuthentication", String.valueOf(isForceNugetAuthentication()));
        packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(), String.valueOf(isEnableNormalizedVersion()));
    }

    private void setSecurityConfig(SecurityConfig securityConfig,
            List<KeyPairDescriptor> keyPairs) {
        if (securityConfig.getXrayConfig() != null) {
            XrayRepoConfigDescriptor xrayRepoConfig = new XrayRepoConfigDescriptor();
            xrayRepoConfig.setDataTtl(securityConfig.getXrayConfig().getDataTtl());
            xrayRepoConfig.setEnabled(securityConfig.getXrayConfig().isEnabled());
            xrayRepoConfig.setXrayNotAvailableMode(securityConfig.getXrayConfig().getXrayNotAvailableMode());
            setXrayConfig(xrayRepoConfig);
        }
        setSignedUrlTtl(securityConfig.getSignedUrlTtl());

        for (KeyPairDescriptor keyPair : keyPairs) {
            if (keyPair.getKey().equals(securityConfig.getPrimaryKeyPairRef())) {
                setPrimaryKeyPairDescriptor(keyPair);
            }
            if (keyPair.getKey().equals(securityConfig.getSecondaryKeyPairRef())) {
                setSecondaryKeyPairDescriptor(keyPair);
            }
        }
    }

    protected void buildSecurityConfig(@Nonnull SecurityConfig securityConfig) {
        if (getXrayConfig() != null) {
            org.artifactory.model.XrayRepoConfig xrayConfig =
                    new org.artifactory.model.XrayRepoConfig();
            XrayRepoConfigDescriptor xrayConfigDescriptor = getXrayConfig();
            xrayConfig.setDataTtl(xrayConfigDescriptor.getDataTtl());
            xrayConfig.setEnabled(xrayConfigDescriptor.isEnabled());
            xrayConfig.setXrayNotAvailableMode(xrayConfigDescriptor.getXrayNotAvailableMode());
            securityConfig.setXrayConfig(xrayConfig);
        }
        if (getPrimaryKeyPairDescriptor() != null) {
            securityConfig.setPrimaryKeyPairRef(getPrimaryKeyPairDescriptor().getKey());
        }
        if (getSecondaryKeyPairDescriptor() != null) {
            securityConfig.setSecondaryKeyPairRef(getSecondaryKeyPairDescriptor().getKey());
        }
        securityConfig.setSignedUrlTtl(getSignedUrlTtl());
    }

    private DownloadRedirectRepoConfigDescriptor buildDownloadRedirectConfigLocal(
            org.artifactory.repo.DownloadRedirectRepoConfig downloadRedirectConfig) {
        if (downloadRedirectConfig == null) {
            return null;
        }
        DownloadRedirectRepoConfigDescriptor downloadRedirectRepoConfigDescriptor =
                new DownloadRedirectRepoConfigDescriptor();
        downloadRedirectRepoConfigDescriptor.setEnabled(downloadRedirectConfig.isEnabled());
        return downloadRedirectRepoConfigDescriptor;
    }

    private org.artifactory.descriptor.repo.CdnRedirectRepoConfigDescriptor buildCdnRedirectConfig(
            org.artifactory.repo.CdnRedirectRepoConfig cdnRedirectRepoConfig) {
        if (cdnRedirectRepoConfig == null) {
            return null;
        }
        org.artifactory.descriptor.repo.CdnRedirectRepoConfigDescriptor cdnRedirectRepoConfigDescriptor =
                new org.artifactory.descriptor.repo.CdnRedirectRepoConfigDescriptor();
        cdnRedirectRepoConfigDescriptor.setEnabled(cdnRedirectRepoConfig.isEnabled());
        return cdnRedirectRepoConfigDescriptor;
    }
}