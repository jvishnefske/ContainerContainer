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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import jakarta.xml.bind.annotation.XmlEnumValue;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.artifactory.common.ConstantValues;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.XrayRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.configuration.CargoConfiguration;
import org.artifactory.util.FederatedMemberUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_CLOSE;
import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_OPEN;
import static org.artifactory.repo.xray.XrayNotAvailableMode.USE_SYSTEM_PROPERTY;
import static org.artifactory.repo.xray.XrayNotAvailableMode.XRAY_NOT_AVAILABLE_MODE;
import static org.artifactory.descriptor.repo.RepoType.Alpine;
import static org.artifactory.descriptor.repo.RepoType.Cargo;
import static org.artifactory.descriptor.repo.RepoType.Conan;
import static org.artifactory.descriptor.repo.RepoType.Debian;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.Helm;
import static org.artifactory.descriptor.repo.RepoType.HelmOCI;
import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;
import static org.artifactory.descriptor.repo.RepoType.NuGet;
import static org.artifactory.descriptor.repo.RepoType.OCI;
import static org.artifactory.descriptor.repo.RepoType.Opkg;
import static org.artifactory.descriptor.repo.RepoType.P2;
import static org.artifactory.descriptor.repo.RepoType.YUM;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.util.PackageTypeConfigFields.BLOCK_PUSHING_SCHEMA_1;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_API_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.DOCKER_PROJECT_ID;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_CONAN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_NUGET_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_P2_AUTHENTICATION;

/**
 * Base class for the repository
 *
 * @author Tomer Cohen
 */
public abstract class RepositoryConfigurationBase implements RepositoryConfiguration, CommonRepoConfig {

    @IncludeTypeSpecific
    private String key;
    @IncludeTypeSpecific
    private String type;
    @IncludeTypeSpecific
    private String packageType;
    @IncludeTypeSpecific
    private String description = "";
    @IncludeTypeSpecific
    private String notes = "";
    @IncludeTypeSpecific
    private String includesPattern;
    @IncludeTypeSpecific
    private String excludesPattern = "";
    @IncludeTypeSpecific
    private String repoLayoutRef;
    @IncludeTypeSpecific
    private long signedUrlTtl = 90;
    private boolean enableComposerSupport = false;
    private boolean enableNuGetSupport = false;
    private boolean enableGemsSupport = false;
    private boolean enableNpmSupport = false;
    private boolean enableBowerSupport = false;
    private boolean enableChefSupport = false;
    private boolean enableCocoaPodsSupport = false;
    private boolean enableConanSupport = false;
    private boolean enableDebianSupport = false;
    @IncludeTypeSpecific(packageType = Debian, repoType = {LOCAL, FEDERATED})
    private boolean debianTrivialLayout = false;
    @IncludeTypeSpecific(packageType = Debian)
    private boolean ddebSupported = false;
    private boolean enablePypiSupport = false;
    private boolean enablePuppetSupport = false;
    private boolean enableDockerSupport = false;
    @IncludeTypeSpecific(packageType = {Docker, OCI, HelmOCI})
    private DockerApiVersion dockerApiVersion = DockerApiVersion.V2;
    @IncludeTypeSpecific(packageType = {Docker, OCI, HelmOCI}, repoType = {LOCAL, REMOTE, FEDERATED})
    private boolean blockPushingSchema1 = true;
    @IncludeTypeSpecific(packageType = NuGet)
    private boolean forceNugetAuthentication = false;
    @IncludeTypeSpecific(packageType = {NuGet})
    private boolean enableNormalizedVersion;
    @IncludeTypeSpecific(packageType = P2)
    private boolean forceP2Authentication = false;
    @IncludeTypeSpecific(packageType = Conan)
    private boolean forceConanAuthentication = false;
    private boolean enableVagrantSupport = false;
    private boolean enableGitLfsSupport = false;
    private boolean enableDistRepoSupport = false;
    @IncludeTypeSpecific(packageType = {Docker, OCI, HelmOCI}, repoType = REMOTE)
    private String dockerProjectId = StringUtils.EMPTY;
    XrayRepoConfig xrayRepoConfig; //used by local and remote only
    DownloadRedirectRepoConfig downloadRedirectConfig; //used by local and remote only
    CdnRedirectRepoConfig cdnRedirectRepoConfig; //used by local and remote only
    @IncludeTypeSpecific(packageType = {Cargo})
    CargoConfiguration cargoConfiguration;
    @IncludeTypeSpecific(packageType = {Alpine, YUM, Debian, Opkg})
    private String primaryKeyPairRef; // used by local and virtual only
    @IncludeTypeSpecific(packageType = {Alpine, YUM, Debian, Opkg})
    private String secondaryKeyPairRef; // used by local and virtual only
    @IncludeTypeSpecific(repoType = {LOCAL, REMOTE, FEDERATED})
    private boolean priorityResolution = false;
    @IncludeTypeSpecific
    private String projectKey = null;
    @IncludeTypeSpecific
    private Set<String> environments = new HashSet<>();
    @IncludeTypeSpecific
    protected RepoConfig repoConfig;
    @IncludeTypeSpecific(packageType = {Helm}, repoType = {LOCAL})
    private boolean forceNonDuplicateChart = false;
    @IncludeTypeSpecific(packageType = {Helm}, repoType = {LOCAL})
    private boolean forceMetadataNameVersion = false;
    @IncludeTypeSpecific(packageType = {HuggingFaceML}, repoType = {LOCAL})
    private boolean mlRepoLayout = false;
    @IncludeTypeSpecific(repoType = {LOCAL, FEDERATED})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String gridTopologyId;
    @IncludeTypeSpecific(repoType = {VIRTUAL, REMOTE, FEDERATED})
    @Setter
    @Getter
    private List<FederatedMemberConfiguration> members;
    @IncludeTypeSpecific(repoType = {LOCAL, REMOTE, VIRTUAL})
    @Setter
    @Getter
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean federationOnGrid;

    RepositoryConfigurationBase() {
    }

    RepositoryConfigurationBase(RepoConfig repoConfig, String type) {
        this.repoConfig = repoConfig;
        this.key = repoConfig.getKey();
        this.type = type;
        this.packageType = repoConfig.getPackageType().toString().toLowerCase();

        setBaseConfig(repoConfig);
        setSecurityConfig(repoConfig);
        setPackageTypeConfig(repoConfig);
        setFederationMembers(repoConfig);

    }

    private void setFederationMembers(RepoConfig repoConfig) {
        // Short-circuit non-federated repos to avoid resolving federation feature flags during basic config mapping.
        if (!repoConfig.isFederated() || !isFederationMembersEnabledForRepoType(repoConfig)) {
            return;
        }
        List<FederatedMember> federationMembers = repoConfig.getBaseConfig().getFederationConfig().getMembers();
        if (CollectionUtils.isEmpty(federationMembers)) {
            return;
        }

        setMembers(federationMembers.stream()
                .map(member -> new FederatedMemberConfiguration(
                        FederatedMemberUtils.resolveUrl(member), member.getMode(), member.isEnabled()))
                .toList());
    }

    private boolean isFederationMembersEnabledForRepoType(RepoConfig repoConfig) {
        return switch (repoConfig.getRepoType()) {
            case FEDERATED -> true;
            case REMOTE -> ConstantValues.remoteRepoFederationEnabled.getBoolean();
            case VIRTUAL -> ConstantValues.virtualRepoFederationEnabled.getBoolean();
            default -> false;
        };
    }

    private void setPackageTypeConfig(RepoConfig repoConfig) {
        Map<String, String> packageTypeConfig = repoConfig.getPackageTypeConfig();
        switch (repoConfig.getPackageType()) {
            case NuGet -> setNugetSpecificConfig(packageTypeConfig);
            case P2 -> {
                String forceP2AuthenticationStr =
                        packageTypeConfig.get(FORCE_P2_AUTHENTICATION.getKey());
                if (StringUtils.isNotBlank(forceP2AuthenticationStr)) {
                    setForceP2Authentication(Boolean.parseBoolean(forceP2AuthenticationStr));
                }
            }
            case Gems -> setEnableGemsSupport(true);
            case Npm -> setEnableNpmSupport(true);
            case Bower -> setEnableBowerSupport(true);
            case Chef -> setEnableChefSupport(true);
            case CocoaPods -> setEnableCocoaPodsSupport(true);
            case Conan -> {
                setEnableConanSupport(true);
                setForceConanAuthentication(Boolean.parseBoolean(
                        packageTypeConfig.get(FORCE_CONAN_AUTHENTICATION.getKey())));
            }
            case Debian -> setEnableDebianSupport(true);
            case Distribution -> setEnableDistRepoSupport(true);
            case Pypi -> setEnablePypiSupport(true);
            case Puppet -> setEnablePuppetSupport(true);
            case Docker, OCI, HelmOCI -> {
                setEnableDockerSupport(true);
                setDockerApiVersion(packageTypeConfig.get(DOCKER_API_VERSION.getKey()));
                setDockerProjectId(packageTypeConfig.get(DOCKER_PROJECT_ID.getKey()));
                if (repoConfig instanceof RealRepoConfig) {
                    String dockerBlockPushingSchema1 = packageTypeConfig.get(BLOCK_PUSHING_SCHEMA_1.getKey());
                    setBlockPushingSchema1(Boolean.parseBoolean(dockerBlockPushingSchema1));
                }
            }
            case Vagrant -> setEnableVagrantSupport(true);
            case GitLfs -> setEnableGitLfsSupport(true);
            case Composer -> setEnableComposerSupport(true);
            default -> {
            }
            // do nothing
        }
    }

    private void setNugetSpecificConfig(Map<String, String> packageTypeConfig) {
        setEnableNuGetSupport(true);
        String forceNugetAuthenticationStr =
                packageTypeConfig.get(FORCE_NUGET_AUTHENTICATION.getKey());
        if (StringUtils.isNotBlank(forceNugetAuthenticationStr)) {
            setForceNugetAuthentication(Boolean.parseBoolean(forceNugetAuthenticationStr));
        }
        Optional.ofNullable(packageTypeConfig.get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()))
                .map(Boolean::parseBoolean)
                .ifPresent(this::setEnableNormalizedVersion);
    }

    private void setSecurityConfig(RepoConfig repoConfig) {
        setSignedUrlTtl(repoConfig.getSecurityConfig().getSignedUrlTtl());
        String newPrimaryKeyPairRef = repoConfig.getSecurityConfig().getPrimaryKeyPairRef();
        if (newPrimaryKeyPairRef != null) {
            setPrimaryKeyPairRef(newPrimaryKeyPairRef);
        }

        String newSecondaryKeyPairRef = repoConfig.getSecurityConfig().getSecondaryKeyPairRef();
        if (newSecondaryKeyPairRef != null) {
            setSecondaryKeyPairRef(newSecondaryKeyPairRef);
        }
    }

    private void setBaseConfig(RepoConfig repoConfig) {
        if (StringUtils.isNotBlank(repoConfig.getBaseConfig().getDescription())) {
            setDescription(repoConfig.getBaseConfig().getDescription());
        }
        if (StringUtils.isNotBlank(repoConfig.getBaseConfig().getNotes())) {
            setNotes(repoConfig.getBaseConfig().getNotes());
        }
        if (StringUtils.isNotBlank(repoConfig.getBaseConfig().getExcludesPattern())) {
            setExcludesPattern(repoConfig.getBaseConfig().getExcludesPattern());
        }
        if (!isNull(repoConfig.getBaseConfig().getIncludesPattern())) {
            setIncludesPattern(repoConfig.getBaseConfig().getIncludesPattern());
        }
        String newRepoLayoutRef = repoConfig.getBaseConfig().getRepoLayoutRef();
        if (newRepoLayoutRef != null) {
            setRepoLayoutRef(newRepoLayoutRef);
        }
        if (StringUtils.isNotBlank(repoConfig.getBaseConfig().getFederationConfig().getGridTopologyId())) {
            setGridTopologyId(repoConfig.getBaseConfig().getFederationConfig().getGridTopologyId());
        }
        this.federationOnGrid = repoConfig.getBaseConfig().getFederationConfig().isFederationOnGrid();
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    @JsonProperty(TYPE_KEY)
    public String getType() {
        return type;
    }

    @Override
    public String getPackageType() {
        if (StringUtils.isNotBlank(packageType)) {
            return packageType;
        }
        RepoType repoType = Generic;
        if (isEnableNuGetSupport()) {
            repoType = RepoType.NuGet;
        } else if (isEnableGemsSupport()) {
            repoType = RepoType.Gems;
        } else if (isEnableNpmSupport()) {
            repoType = RepoType.Npm;
        } else if (isEnableBowerSupport()) {
            repoType = RepoType.Bower;
        } else if (isEnableCocoaPodsSupport()) {
            repoType = RepoType.CocoaPods;
        } else if (isEnableDebianSupport()) {
            repoType = RepoType.Debian;
        } else if (isEnableDistRepoSupport()) {
            repoType = RepoType.Distribution;
        } else if (isEnablePypiSupport()) {
            repoType = RepoType.Pypi;
        } else if (isEnablePuppetSupport()) {
            repoType = RepoType.Puppet;
        } else if (isEnableDockerSupport()) {
            repoType = RepoType.Docker;
        } else if (isEnableVagrantSupport()) {
            repoType = RepoType.Vagrant;
        } else if (isEnableGitLfsSupport()) {
            repoType = RepoType.GitLfs;
        } else if (isEnableComposerSupport()) {
            repoType = RepoType.Composer;
        } else if (isEnableConanSupport()) {
            repoType = RepoType.Conda;
        } else if (isEnableChefSupport()) {
            repoType = RepoType.Chef;
        }
        return repoType.getType();
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType.toLowerCase();
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getExcludesPattern() {
        return excludesPattern;
    }

    public void setExcludesPattern(String excludesPattern) {
        this.excludesPattern = excludesPattern;
    }

    @Override
    public String getIncludesPattern() {
        return includesPattern;
    }

    public void setIncludesPattern(String includesPattern) {
        this.includesPattern = includesPattern;
    }

    @Override
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String getRepoLayoutRef() {
        return repoLayoutRef;
    }

    public void setRepoLayoutRef(String repoLayoutRef) {
        this.repoLayoutRef = repoLayoutRef;
    }

    public String getPrimaryKeyPairRef() {
        return primaryKeyPairRef;
    }

    public void setPrimaryKeyPairRef(String primaryKeyPairRef) {
        this.primaryKeyPairRef = primaryKeyPairRef;
    }

    public String getSecondaryKeyPairRef() {
        return secondaryKeyPairRef;
    }

    public void setSecondaryKeyPairRef(String secondaryKeyPairRef) {
        this.secondaryKeyPairRef = secondaryKeyPairRef;
    }

    @Override
    public boolean isEnableNuGetSupport() {
        return enableNuGetSupport;
    }

    public void setEnableNuGetSupport(boolean enableNuGetSupport) {
        this.enableNuGetSupport = enableNuGetSupport;
    }

    @Override
    public boolean isEnableGemsSupport() {
        return enableGemsSupport;
    }

    public void setEnableGemsSupport(boolean enableGemsSupport) {
        this.enableGemsSupport = enableGemsSupport;
    }

    @Override
    public boolean isEnableNpmSupport() {
        return enableNpmSupport;
    }

    @Override
    public void setEnableNpmSupport(boolean enableNpmSupport) {
        this.enableNpmSupport = enableNpmSupport;
    }

    @Override
    public boolean isEnableBowerSupport() {
        return enableBowerSupport;
    }

    @Override
    public boolean isEnableChefSupport() {
        return enableChefSupport;
    }

    @Override
    public void setEnableChefSupport(boolean enableChefSupport) {
        this.enableChefSupport = enableChefSupport;
    }

    @Override
    public void setEnableBowerSupport(boolean enableBowerSupport) {
        this.enableBowerSupport = enableBowerSupport;
    }

    @Override
    public boolean isEnableCocoaPodsSupport() {
        return enableCocoaPodsSupport;
    }

    @Override
    public void setEnableCocoaPodsSupport(boolean enableCocoaPodsSupport) {
        this.enableCocoaPodsSupport = enableCocoaPodsSupport;
    }

    @Override
    public boolean isEnableConanSupport() {
        return enableConanSupport;
    }

    @Override
    public void setEnableConanSupport(boolean enableConanSupport) {
        this.enableConanSupport = enableConanSupport;
    }

    @Override
    public void setEnableDebianSupport(boolean enableDebianSupport) {
        this.enableDebianSupport = enableDebianSupport;
    }

    @Override
    public boolean isEnableDebianSupport() {
        return enableDebianSupport;
    }

    @Override
    public boolean isDebianTrivialLayout() {
        return debianTrivialLayout;
    }

    @Override
    public boolean isDdebSupported() {
        return ddebSupported;
    }

    public void setDdebSupported(boolean ddebSupported) {
        this.ddebSupported = ddebSupported;
    }

    @Override
    public boolean isEnableDistRepoSupport() {
        return enableDistRepoSupport;
    }

    @Override
    public void setEnableDistRepoSupport(boolean enableDistRepoSupport) {
        this.enableDistRepoSupport = enableDistRepoSupport;
    }

    public void setDebianTrivialLayout(boolean debianTrivialLayout) {
        this.debianTrivialLayout = debianTrivialLayout;
    }

    @Override
    public boolean isEnablePypiSupport() {
        return enablePypiSupport;
    }

    @Override
    public void setEnablePypiSupport(boolean enablePypiSupport) {
        this.enablePypiSupport = enablePypiSupport;
    }

    @Override
    public boolean isEnablePuppetSupport() {
        return enablePuppetSupport;
    }

    @Override
    public void setEnablePuppetSupport(boolean enablePuppetSupport) {
        this.enablePuppetSupport = enablePuppetSupport;
    }

    @Override
    public boolean isEnableDockerSupport() {
        return enableDockerSupport;
    }

    @Override
    public void setEnableDockerSupport(boolean enableDockerSupport) {
        this.enableDockerSupport = enableDockerSupport;
    }

    @Override
    public String getDockerApiVersion() {
        return dockerApiVersion.name();
    }

    public void setDockerApiVersion(String dockerApiVersion) {
        this.dockerApiVersion = DockerApiVersion.valueOf(dockerApiVersion);
    }

    public boolean isBlockPushingSchema1() {
        return blockPushingSchema1;
    }

    public void setBlockPushingSchema1(boolean blockPushingSchema1) {
        this.blockPushingSchema1 = blockPushingSchema1;
    }

    @Override
    public boolean isForceNugetAuthentication() {
        return forceNugetAuthentication;
    }

    public void setForceNugetAuthentication(boolean forceNugetAuthentication) {
        this.forceNugetAuthentication = forceNugetAuthentication;
    }

    public boolean isForceP2Authentication() {
        return forceP2Authentication;
    }

    public void setForceP2Authentication(boolean setForceP2Authentication) {
        this.forceP2Authentication = setForceP2Authentication;
    }

    @Override
    public boolean isForceConanAuthentication() {
        return forceConanAuthentication;
    }

    public void setForceConanAuthentication(boolean forceConanAuthentication) {
        this.forceConanAuthentication = forceConanAuthentication;
    }

    @Override
    public boolean isEnableVagrantSupport() {
        return enableVagrantSupport;
    }

    @Override
    public void setEnableVagrantSupport(boolean enableVagrantSupport) {
        this.enableVagrantSupport = enableVagrantSupport;
    }

    @Override
    public boolean isEnableGitLfsSupport() {
        return enableGitLfsSupport;
    }

    @Override
    public void setEnableGitLfsSupport(boolean enableGitLfsSupport) {
        this.enableGitLfsSupport = enableGitLfsSupport;
    }

    public boolean isEnableComposerSupport() {
        return enableComposerSupport;
    }

    @Override
    public void setEnableComposerSupport(boolean enableComposerSupport) {
        this.enableComposerSupport = enableComposerSupport;
    }

    @Override
    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    @Override
    public Set<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Collection<String> environments) {
        if (environments == null) {
            this.environments = null;
        } else {
            this.environments = new HashSet<>(environments);
        }
    }

    /**
     * Extract from an Enum the {@link javax.xml.bind.annotation.XmlEnumValue} that are associated with its fields.
     *
     * @param clazz The class that is to be introspected
     * @return A map that maps {@link javax.xml.bind.annotation.XmlEnumValue#value()} to the enum name itself.
     */
    Map<String, String> extractXmlValueFromEnumAnnotations(Class clazz) {
        Map<String, String> annotationToName = Maps.newHashMap();
        Field[] fields = clazz.getFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(XmlEnumValue.class)) {
                XmlEnumValue annotation = field.getAnnotation(XmlEnumValue.class);
                annotationToName.put(annotation.value(), field.getName());
            }
        }
        return annotationToName;
    }

    @IncludeTypeSpecific(repoType = {LOCAL, REMOTE, FEDERATED})
    public void setXrayNotAvailableModeFromConfig(Map<String, Object> repositoryConfig) {
        if (repositoryConfig.containsKey(XRAY_NOT_AVAILABLE_MODE)) {
            String xrayAvailabilityBehavior = (String) repositoryConfig.get(XRAY_NOT_AVAILABLE_MODE);
            if (Strings.isNullOrEmpty(xrayAvailabilityBehavior)) {
                this.setXrayNotAvailableMode(USE_SYSTEM_PROPERTY);
            } else {
                switch (xrayAvailabilityBehavior.toLowerCase()) {
                    case "fail_close" -> this.setXrayNotAvailableMode(FAIL_CLOSE);
                    case "fail_open" -> this.setXrayNotAvailableMode(FAIL_OPEN);
                    default -> this.setXrayNotAvailableMode(USE_SYSTEM_PROPERTY);
                }
            }
            repositoryConfig.remove(XRAY_NOT_AVAILABLE_MODE);
        }
    }

    public void setXrayNotAvailableMode(XrayNotAvailableMode xrayNotAvailableMode) {
        getLazyXrayConfig().setXrayNotAvailableMode(xrayNotAvailableMode);
    }

    XrayRepoConfig getLazyXrayConfig() {
        if (this.xrayRepoConfig == null) {
            this.xrayRepoConfig = new XrayRepoConfig();
        }
        return this.xrayRepoConfig;
    }

    DownloadRedirectRepoConfig getLazyDownloadRedirectConfig() {
        if (this.downloadRedirectConfig == null) {
            this.downloadRedirectConfig = new DownloadRedirectRepoConfig();
        }
        return this.downloadRedirectConfig;
    }

    @NonNull
    CdnRedirectRepoConfig getLazyCdnRedirectConfig() {
        if (this.cdnRedirectRepoConfig == null) {
            this.cdnRedirectRepoConfig = new CdnRedirectRepoConfig();
        }
        return this.cdnRedirectRepoConfig;
    }

    CargoConfiguration getCargoConfiguration() {
        if (cargoConfiguration == null) {
            this.cargoConfiguration = new CargoConfiguration();
        }
        return cargoConfiguration;
    }

    public boolean isPriorityResolution() {
        return priorityResolution;
    }

    public void setPriorityResolution(boolean priorityResolution) {
        this.priorityResolution = priorityResolution;
    }

    @Override
    public long getSignedUrlTtl() {
        return this.signedUrlTtl;
    }

    public void setSignedUrlTtl(long signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }

    public String getDockerProjectId() {
        return dockerProjectId;
    }

    public void setDockerProjectId(String dockerProjectId) {
        this.dockerProjectId = dockerProjectId;
    }

    public boolean isEnableNormalizedVersion() {
        return enableNormalizedVersion;
    }

    public void setEnableNormalizedVersion(boolean enableNormalizedVersion) {
        this.enableNormalizedVersion = enableNormalizedVersion;
    }

    @Override
    public boolean isForceMetadataNameVersion() {
        return forceMetadataNameVersion;
    }

    public void setForceMetadataNameVersion(boolean forceMetadataNameVersion) {
        this.forceMetadataNameVersion = forceMetadataNameVersion;
    }

    @Override
    public boolean isForceNonDuplicateChart() {
        return forceNonDuplicateChart;
    }

    public void setForceNonDuplicateChart(boolean forceNonDuplicateChart) {
        this.forceNonDuplicateChart = forceNonDuplicateChart;
    }

    @Override
    public boolean isMlRepoLayout() {
        return this.mlRepoLayout;
    }

    @Override
    public void setMlRepoLayout(boolean mlRepoLayout) {
        this.mlRepoLayout = mlRepoLayout;
    }

    public String getGridTopologyId() {
        return gridTopologyId;
    }

    public void setGridTopologyId(String gridTopologyId) {
        this.gridTopologyId = gridTopologyId;
    }
}
