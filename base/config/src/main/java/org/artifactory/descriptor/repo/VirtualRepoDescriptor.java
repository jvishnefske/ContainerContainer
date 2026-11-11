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

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;

/**
 * @author Fred Simon
 */
@XmlType(name = "VirtualRepoType", propOrder = {"hideUnauthorizedResources",
        "artifactoryRequestsCanRetrieveRemoteArtifacts", "resolveDockerTagsByTimestamp", "repositories", "keyPair",
        "pomRepositoryReferencesCleanupPolicy", "p2", "defaultDeploymentRepo", "cachingLocalForeignLayersEnabled",
        "externalDependencies", "virtualCacheConfig", "forceMavenAuthentication", "debianDefaultArchitectures",
        "debianOptionalIndexCompressionFormats", "useNamespaces",},
        namespace = Descriptor.NS)
@GenerateDiffFunction
public class VirtualRepoDescriptor extends RepoBaseDescriptor {

    @XmlIDREF
    @XmlElementWrapper(name = "repositories")
    @XmlElement(name = "repositoryRef", type = RepoBaseDescriptor.class)
    @DiffReference
    @DiffAtomic
    private List<RepoDescriptor> repositories = new ArrayList<>();

    @XmlElement(defaultValue = "false")
    @Getter
    @Setter
    private boolean useNamespaces;

    @XmlElement(defaultValue = "false")
    private boolean hideUnauthorizedResources;

    @XmlElement(defaultValue = "false")
    private boolean artifactoryRequestsCanRetrieveRemoteArtifacts;

    @XmlElement(defaultValue = "false")
    private boolean resolveDockerTagsByTimestamp;

    @XmlElement(required = true)
    @NotUnicode
    private String keyPair;

    @XmlElement(defaultValue = "discard_active_reference", required = true)
    private PomCleanupPolicy pomRepositoryReferencesCleanupPolicy = PomCleanupPolicy.discard_active_reference;

    @XmlElement
    @Valid
    private P2Configuration p2;

    @XmlIDREF
    @XmlElement(name = "defaultDeploymentRepo", type = LocalRepoDescriptor.class)
    @DiffReference
    @Valid
    private LocalRepoDescriptor defaultDeploymentRepo;

    @XmlElement
    private boolean cachingLocalForeignLayersEnabled = false;

    @XmlElement
    @Valid
    private ExternalDependenciesConfig externalDependencies;

    @XmlElement
    private VirtualCacheConfigDescriptor virtualCacheConfig = new VirtualCacheConfigDescriptor();

    @XmlElement
    private boolean forceMavenAuthentication = false;

    @XmlElement
    @NotUnicode
    private String debianDefaultArchitectures = "i386,amd64";

    @XmlElementWrapper(name = "debianOptionalIndexCompressionFormats")
    @XmlElement(name = "debianFormat", type = String.class)
    private List<@NotUnicode String> debianOptionalIndexCompressionFormats;

    public VirtualRepoDescriptor() {
    }

    public VirtualRepoDescriptor(VirtualRepoConfig repositoryConfig, List<RepoLayout> repoLayouts,
            Map<String, VirtualRepoDescriptor> aggregatedVirtualRepoDescriptorMap,
            Map<String, VirtualRepoConfig> virtualReposMap, Map<String, RepoDescriptor> realReposMap,
            List<KeyPairDescriptor> keyPairs, List<PropertySet> propertySets, List<ProxyDescriptor> proxies) {
        setKey(repositoryConfig.getKey());
        setType(repositoryConfig.getPackageType());
        setBaseConfig(repositoryConfig.getBaseConfig(), repoLayouts);
        setRepoTypeConfig(repositoryConfig.getRepoTypeConfig(), aggregatedVirtualRepoDescriptorMap, virtualReposMap,
                realReposMap, repoLayouts, keyPairs, propertySets, proxies);
        setSecurityConfig(repositoryConfig.getSecurityConfig(), keyPairs);
        setPackageTypeConfig(repositoryConfig.getPackageTypeConfig(), repositoryConfig.getPackageType(), realReposMap);
    }

    public VirtualRepoConfig toRepositoryConfig() {
        VirtualRepoConfig repositoryConfig = VirtualRepoConfig.create(getKey(), getType());
        buildBaseConfig(repositoryConfig.getBaseConfig());
        buildRepoTypeConfig(repositoryConfig.getRepoTypeConfig());
        buildSecurityConfig(repositoryConfig.getSecurityConfig());
        buildPackageTypeConfig(getType(), repositoryConfig.getPackageTypeConfig());
        return repositoryConfig;
    }

    public List<String> getDebianOptionalIndexCompressionFormats() {
        return debianOptionalIndexCompressionFormats;
    }

    public void setDebianOptionalIndexCompressionFormats(List<String> debianOptionalIndexCompressionFormats) {
        this.debianOptionalIndexCompressionFormats = debianOptionalIndexCompressionFormats;
    }

    public List<RepoDescriptor> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<RepoDescriptor> repositories) {
        this.repositories = repositories == null ? Lists.newArrayList() : repositories;
    }

    public boolean isHideUnauthorizedResources() {
        return hideUnauthorizedResources;
    }

    public void setHideUnauthorizedResources(boolean hideUnauthorizedResources) {
        this.hideUnauthorizedResources = hideUnauthorizedResources;
    }

    public boolean isArtifactoryRequestsCanRetrieveRemoteArtifacts() {
        return artifactoryRequestsCanRetrieveRemoteArtifacts;
    }

    public void setArtifactoryRequestsCanRetrieveRemoteArtifacts(
            boolean artifactoryRequestsCanRetrieveRemoteArtifacts) {
        this.artifactoryRequestsCanRetrieveRemoteArtifacts = artifactoryRequestsCanRetrieveRemoteArtifacts;
    }

    public boolean isResolveDockerTagsByTimestamp() {
        return resolveDockerTagsByTimestamp;
    }

    public void setResolveDockerTagsByTimestamp(boolean resolveDockerTagsByTimestamp) {
        this.resolveDockerTagsByTimestamp = resolveDockerTagsByTimestamp;
    }

    public String getKeyPair() {
        return keyPair;
    }

    public void setKeyPair(String keyPair) {
        this.keyPair = keyPair;
    }

    @Override
    public boolean isReal() {
        return false;
    }

    public boolean removeRepository(RepoDescriptor repo) {
        return repositories.remove(repo);
    }

    public void removeKeyPair() {
        keyPair = null;
    }

    public PomCleanupPolicy getPomRepositoryReferencesCleanupPolicy() {
        return pomRepositoryReferencesCleanupPolicy;
    }

    public void setPomRepositoryReferencesCleanupPolicy(PomCleanupPolicy pomRepositoryReferencesCleanupPolicy) {
        this.pomRepositoryReferencesCleanupPolicy = pomRepositoryReferencesCleanupPolicy;
    }

    public P2Configuration getP2() {
        return p2;
    }

    public void setP2(P2Configuration p2) {
        this.p2 = p2;
    }

    public LocalRepoDescriptor getDefaultDeploymentRepo() {
        return defaultDeploymentRepo;
    }

    public void setDefaultDeploymentRepo(LocalRepoDescriptor defaultDeploymentRepo) {
        this.defaultDeploymentRepo = defaultDeploymentRepo;
    }

    public boolean isCachingLocalForeignLayersEnabled() {
        return cachingLocalForeignLayersEnabled;
    }

    public void setCachingLocalForeignLayersEnabled(boolean cachingLocalForeignLayersEnabled) {
        this.cachingLocalForeignLayersEnabled = cachingLocalForeignLayersEnabled;
    }

    public ExternalDependenciesConfig getExternalDependencies() {
        return externalDependencies;
    }

    public void setExternalDependencies(ExternalDependenciesConfig externalDependencies) {
        this.externalDependencies = externalDependencies;
    }

    public VirtualCacheConfigDescriptor getVirtualCacheConfig() {
        return virtualCacheConfig;
    }

    public void setVirtualCacheConfig(VirtualCacheConfigDescriptor virtualCacheConfig) {
        this.virtualCacheConfig = virtualCacheConfig;
    }

    public boolean isForceMavenAuthentication() {
        return forceMavenAuthentication;
    }

    public void setForceMavenAuthentication(boolean forceMavenAuthentication) {
        this.forceMavenAuthentication = forceMavenAuthentication;
    }

    public String getDebianDefaultArchitectures() {
        return debianDefaultArchitectures;
    }

    public void setDebianDefaultArchitectures(String debianDefaultArchitectures) {
        this.debianDefaultArchitectures = debianDefaultArchitectures;
    }

    public void setDebianDefaultArchitecturesList(List<String> debianDefaultArchitectures) {
        this.debianDefaultArchitectures = String.join(",", debianDefaultArchitectures);
    }

    public boolean isExternalDependenciesConfigEnabled() {
        return externalDependencies != null && externalDependencies.isEnabled();
    }

    public String getExternalDependenciesConfigPatterns() {
        return String.join(",", externalDependencies.getPatterns());
    }

    @Nullable
    public String getExternalDependenciesConfigRemoteRepo() {
        if (isExternalDependenciesConfigEnabled() && externalDependencies.getRemoteRepo() != null) {
            return externalDependencies.getRemoteRepo().getKey();
        }
        return null;
    }

    public long getVirtualRetrievalCachePeriodSecs() {
        return getVirtualCacheConfig().getVirtualRetrievalCachePeriodSecs();
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

    private void buildBaseConfig(BaseConfig baseConfig) {
        baseConfig.setDescription(getDescription());
        baseConfig.setNotes(getNotes());
        if (getRepoLayout() != null) {
            baseConfig.setRepoLayoutRef(getRepoLayout().getName());
        }
        baseConfig.setIncludesPattern(getIncludesPattern());
        baseConfig.setExcludesPattern(getExcludesPattern());
        baseConfig.setGridTopologyId(getGridTopologyId());
    }

    private void setRepoTypeConfig(VirtualRepositoryRepoTypeConfig virtualConfig,
            Map<String, VirtualRepoDescriptor> aggregatedVirtualRepoDescriptorMap,
            Map<String, VirtualRepoConfig> virtualReposMap,
            Map<String, RepoDescriptor> realReposMap,
            List<RepoLayout> repoLayouts, List<KeyPairDescriptor> keyPairs,
            List<PropertySet> propertySets, List<ProxyDescriptor> proxies) {
        if (virtualConfig.getVirtualCacheConfig() != null) {
            VirtualCacheConfigDescriptor virtualCacheConfigDescriptor = new VirtualCacheConfigDescriptor();
            virtualCacheConfigDescriptor.setVirtualRetrievalCachePeriodSecs(
                    virtualConfig.getVirtualCacheConfig().getVirtualRetrievalCachePeriodSecs());
            setVirtualCacheConfig(virtualCacheConfigDescriptor);
        }
        ArrayList<RepoDescriptor> newRepositories = new ArrayList<>();
        aggregatedVirtualRepoDescriptorMap.put(getKey(), this);
        for (String repositoryRef : virtualConfig.getRepositoryRefs()) {
            VirtualRepoDescriptor aggregatedVirtualRepo = aggregatedVirtualRepoDescriptorMap.get(repositoryRef);
            if (aggregatedVirtualRepo != null) {
                newRepositories.add(aggregatedVirtualRepo);
            } else {
                VirtualRepoConfig virtualRepoConfig = virtualReposMap.get(repositoryRef);
                if (virtualRepoConfig != null) {
                    VirtualRepoDescriptor virtualRepoDescriptor =
                            new VirtualRepoDescriptor(virtualRepoConfig, repoLayouts,
                                    aggregatedVirtualRepoDescriptorMap,
                                    virtualReposMap, realReposMap, keyPairs, propertySets, proxies);
                    newRepositories.add(virtualRepoDescriptor);
                } else {
                    RepoDescriptor repoDescriptor = realReposMap.get(repositoryRef);
                    if (repoDescriptor != null) {
                        newRepositories.add(repoDescriptor);
                    }
                }
            }
        }
        setRepositories(newRepositories);
        RepoDescriptor deploymentRepo = realReposMap.get(virtualConfig.getDefaultDeploymentRepoRef());
        if (deploymentRepo != null) {
            setDefaultDeploymentRepo((LocalRepoDescriptor) deploymentRepo);
        }

        setArtifactoryRequestsCanRetrieveRemoteArtifacts(
                virtualConfig.isArtifactoryRequestsCanRetrieveRemoteArtifacts());
    }

    private void buildRepoTypeConfig(
            @Nonnull VirtualRepositoryRepoTypeConfig virtualConfig) {
        virtualConfig
                .setRepositoryRefs(getRepositories().stream().map(RepoDescriptor::getKey).collect(Collectors.toList()));
        if (getDefaultDeploymentRepo() != null) {
            virtualConfig.setDefaultDeploymentRepoRef(getDefaultDeploymentRepo().getKey());
        }
        virtualConfig
                .setArtifactoryRequestsCanRetrieveRemoteArtifacts(isArtifactoryRequestsCanRetrieveRemoteArtifacts());
        if (getVirtualCacheConfig() != null) {
            org.artifactory.repo.virtual.VirtualCacheConfig virtualCacheConfigNew =
                    new org.artifactory.repo.virtual.VirtualCacheConfig();
            virtualCacheConfigNew
                    .setVirtualRetrievalCachePeriodSecs(getVirtualCacheConfig().getVirtualRetrievalCachePeriodSecs());
            virtualConfig.setVirtualCacheConfig(virtualCacheConfigNew);
        }
    }

    @VisibleForTesting
    void setPackageTypeConfig(Map<String, String> packageTypeConfig, RepoType packageType,
            Map<String, RepoDescriptor> realReposMap) {
        if (RepoType.Helm.equals(packageType)) {
            setUseNamespaces(Boolean.parseBoolean(packageTypeConfig.get("useNamespaces")));
        } else if (RepoType.Bower.equals(packageType)) {
            setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig, realReposMap));
        } else if (RepoType.Npm.equals(packageType)) {
            setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig, realReposMap));
        } else if (RepoType.Cargo.equals(packageType)) {
            CargoConfiguration cargoConfig = new CargoConfiguration();
            cargoConfig.setCargoAnonymousAccess(Boolean.parseBoolean(packageTypeConfig.get("cargoAnonymousAccess")));
            cargoConfig.setCargoInternalIndex(Boolean.parseBoolean(packageTypeConfig.get("cargoInternalIndex")));
            setCargoConfig(cargoConfig);
        } else if (RepoType.Debian.equals(packageType)) {
            setDdebSupported(Boolean.parseBoolean(packageTypeConfig.get("ddebSupported")));
            if (packageTypeConfig.get("debianOptionalIndexCompressionFormats") != null) {
                setDebianOptionalIndexCompressionFormats(
                        RepositoryConfigSource.jsonStringToList(packageTypeConfig,
                                "debianOptionalIndexCompressionFormats"));
            }
            Optional.ofNullable(packageTypeConfig.get("debianDefaultArchitectures"))
                    .ifPresent(this::setDebianDefaultArchitectures);
        } else if (packageType.isDockerGroup()) {
            setDockerApiVersion(packageTypeConfig.get("dockerApiVersion"));
            setResolveDockerTagsByTimestamp(
                    Boolean.parseBoolean(packageTypeConfig.get("resolveDockerTagsByTimestamp")));
            setCachingLocalForeignLayersEnabled(
                    Boolean.parseBoolean(packageTypeConfig.get("cachingLocalForeignLayersEnabled")));
        } else if (packageType.isMavenGroup()) {
            Optional.ofNullable(packageTypeConfig.get("pomRepositoryReferencesCleanupPolicy"))
                    .map(PomCleanupPolicy::valueOf)
                    .ifPresent(this::setPomRepositoryReferencesCleanupPolicy);
            setForceMavenAuthentication(
                    Boolean.parseBoolean(packageTypeConfig.get("forceMavenAuthentication")));
            if (RepoType.P2.equals(packageType)) {
                P2Configuration p2Configuration = new P2Configuration();
                p2Configuration.setUrls(RepositoryConfigSource.jsonStringToList(packageTypeConfig, "urls"));
                setP2(p2Configuration);
                setForceP2Authentication(
                        Boolean.parseBoolean(packageTypeConfig.get("forceP2Authentication")));
            }
        } else if (RepoType.NuGet.equals(packageType)) {
            setForceNugetAuthentication(
                    Boolean.parseBoolean(packageTypeConfig.get("forceNugetAuthentication")));
            setEnableNormalizedVersion(
                    Boolean.parseBoolean(packageTypeConfig.get(ENABLE_NUGET_NORMALIZED_VERSION.getKey())));
        } else if (RepoType.Go.equals(packageType)) {
            setExternalDependencies(externalDependenciesToDescriptor(packageTypeConfig, realReposMap));
        } else if (RepoType.Conan.equals(packageType)) {
            setForceConanAuthentication(Boolean.parseBoolean(packageTypeConfig.get("forceConanAuthentication")));
        }
    }

    @VisibleForTesting
    void buildPackageTypeConfig(RepoType packageType,
            Map<String, String> packageTypeConfig) {
        if (RepoType.Helm.equals(packageType)) {
            packageTypeConfig.put("useNamespaces", String.valueOf(isUseNamespaces()));
        } else if (RepoType.Bower.equals(packageType)) {
            externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
        } else if (RepoType.Npm.equals(packageType)) {
            externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
        } else if (RepoType.Cargo.equals(packageType)) {
            if (getCargoConfig() != null) {
                packageTypeConfig
                        .put("cargoAnonymousAccess", String.valueOf(getCargoConfig().isCargoAnonymousAccess()));
                packageTypeConfig
                        .put("cargoInternalIndex", String.valueOf(getCargoConfig().isCargoInternalIndex()));
            }
        } else if (RepoType.Debian.equals(packageType)) {
            packageTypeConfig.put("ddebSupported", String.valueOf(isDdebSupported()));
            RepositoryConfigSource.entityToFlatMapKeys(getDebianOptionalIndexCompressionFormats(),
                    "debianOptionalIndexCompressionFormats", packageTypeConfig);
            Optional.ofNullable(getDebianDefaultArchitectures())
                    .ifPresent(value -> packageTypeConfig.put("debianDefaultArchitectures", value));
        } else if (packageType.isDockerGroup()) {
            packageTypeConfig.put("dockerApiVersion", String.valueOf(getDockerApiVersion()));
            packageTypeConfig.put("resolveDockerTagsByTimestamp", String.valueOf(isResolveDockerTagsByTimestamp()));
            packageTypeConfig
                    .put("cachingLocalForeignLayersEnabled", String.valueOf(isCachingLocalForeignLayersEnabled()));
        } else if (packageType.isMavenGroup()) {
            packageTypeConfig.put("pomRepositoryReferencesCleanupPolicy",
                    String.valueOf(getPomRepositoryReferencesCleanupPolicy()));
            packageTypeConfig.put("forceMavenAuthentication",
                    String.valueOf(isForceMavenAuthentication()));
            if (RepoType.P2.equals(packageType) && getP2() != null && getP2().getUrls() != null) {
                RepositoryConfigSource.entityToFlatMapKeys(getP2().getUrls(), "urls", packageTypeConfig);
                packageTypeConfig.put("forceP2Authentication", String.valueOf(isForceP2Authentication()));
            }
        } else if (RepoType.NuGet.equals(packageType)) {
            buildNugetPackageTypeConfig(packageTypeConfig);
        } else if (RepoType.Go.equals(packageType)) {
            externalDependenciesToConfig(packageTypeConfig, getExternalDependencies());
        } else if (RepoType.Conan.equals(packageType)) {
            packageTypeConfig.put("forceConanAuthentication", String.valueOf(isForceConanAuthentication()));
        }
    }

    private void buildNugetPackageTypeConfig(Map<String, String> packageTypeConfig) {
        packageTypeConfig.put("forceNugetAuthentication",
                String.valueOf(isForceNugetAuthentication()));
        packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(),
                String.valueOf(isEnableNormalizedVersion()));
    }

    private void setSecurityConfig(SecurityConfig securityConfig,
            List<KeyPairDescriptor> keyPairs) {
        setSignedUrlTtl(securityConfig.getSignedUrlTtl());
        for (KeyPairDescriptor keyPair : keyPairs) {
            if (keyPair.getKey().equals(securityConfig.getPrimaryKeyPairRef())) {
                setPrimaryKeyPairDescriptor(keyPair);
            }
            if (keyPair.getKey().equals(securityConfig.getSecondaryKeyPairRef())) {
                setSecondaryKeyPairDescriptor(keyPair);
            }
        }
        setKeyPair(securityConfig.getKeyPair());
        setHideUnauthorizedResources(securityConfig.isHideUnauthorizedResources());
    }

    private void buildSecurityConfig(@Nonnull SecurityConfig securityConfig) {
        if (getPrimaryKeyPairDescriptor() != null) {
            securityConfig.setPrimaryKeyPairRef(getPrimaryKeyPairDescriptor().getKey());
        }
        if (getSecondaryKeyPairDescriptor() != null) {
            securityConfig.setSecondaryKeyPairRef(getSecondaryKeyPairDescriptor().getKey());
        }
        securityConfig.setSignedUrlTtl(getSignedUrlTtl());
        securityConfig.setHideUnauthorizedResources(isHideUnauthorizedResources());
        securityConfig.setKeyPair(getKeyPair());
    }

    private ExternalDependenciesConfig externalDependenciesToDescriptor(Map<String, String> packageTypeConfig,
            Map<String, RepoDescriptor> realReposMap) {
        ExternalDependenciesConfig externalDependenciesConfig = null;
        if (packageTypeConfig.containsKey("externalDependenciesEnabled")) {
            externalDependenciesConfig = new ExternalDependenciesConfig();
            externalDependenciesConfig
                    .setEnabled(Boolean.parseBoolean(packageTypeConfig.get("externalDependenciesEnabled")));
            List<String> patterns =
                    RepositoryConfigSource.jsonStringToList(packageTypeConfig, "externalDependenciesPatterns");
            externalDependenciesConfig.setPatterns(patterns);
            String remoteRepoRef =
                    packageTypeConfig.get("externalDependenciesRemoteRepoRef");
            if (remoteRepoRef != null) {
                RepoDescriptor repoDescriptor = realReposMap.get(remoteRepoRef);
                if (repoDescriptor instanceof RemoteRepoDescriptor remoteRepoDescriptor) {
                    externalDependenciesConfig.setRemoteRepo(remoteRepoDescriptor);
                }
            }
        }
        return externalDependenciesConfig;
    }

    private void externalDependenciesToConfig(Map<String, String> packageTypeConfig,
            ExternalDependenciesConfig externalDependencies) {
        if (externalDependencies != null) {
            packageTypeConfig.put("externalDependenciesEnabled",
                    String.valueOf(externalDependencies.isEnabled()));
            RepositoryConfigSource.entityToFlatMapKeys(externalDependencies.getPatterns(),
                    "externalDependenciesPatterns", packageTypeConfig);
            if (externalDependencies.getRemoteRepo() != null) {
                packageTypeConfig
                        .put("externalDependenciesRemoteRepoRef",
                                externalDependencies.getRemoteRepo().getKey());
            }
        } else {
            packageTypeConfig.remove("externalDependenciesEnabled");
            packageTypeConfig.remove("externalDependenciesPatterns");
            packageTypeConfig.remove("externalDependenciesRemoteRepoRef");
        }
    }
}