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
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import org.artifactory.descriptor.repo.PomCleanupPolicy;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.util.CargoPackageTypeConfigMapper;
import org.jfrog.common.JsonUtils;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.artifactory.descriptor.repo.RepoType.Cargo;
import static org.artifactory.descriptor.repo.RepoType.Debian;
import static org.artifactory.descriptor.repo.RepoType.Helm;
import static org.artifactory.descriptor.repo.RepoType.HelmOCI;
import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;
import static org.artifactory.descriptor.repo.RepoType.Maven;
import static org.artifactory.descriptor.repo.RepoType.P2;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.CACHING_LOCAL_FOREIGN_LAYERS_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_DEFAULT_ARCHITECTURES;
import static org.artifactory.util.PackageTypeConfigFields.DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_ENABLED;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.util.PackageTypeConfigFields.EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF;
import static org.artifactory.util.PackageTypeConfigFields.FORCE_MAVEN_AUTHENTICATION;
import static org.artifactory.util.PackageTypeConfigFields.P2_URLS;
import static org.artifactory.util.PackageTypeConfigFields.POM_REPOSITORY_REFERENCES_CLEANUP_POLICY;
import static org.artifactory.util.PackageTypeConfigFields.RESOLVE_DOCKER_TAGS_BY_TIMESTAMP;
import static org.artifactory.util.PackageTypeConfigFields.USE_NAMESPACES;

/**
 * Virtual repository configuration
 *
 * @author Tomer Cohen
 * @see org.artifactory.descriptor.repo.VirtualRepoDescriptor
 */
@JsonFilter("typeSpecificFilter")
public class VirtualRepositoryConfigurationImpl extends RepositoryConfigurationBase
        implements VirtualRepositoryConfiguration {

    @IncludeTypeSpecific(repoType = VIRTUAL)
    private List<String> repositories;
    @IncludeTypeSpecific(repoType = VIRTUAL)
    private boolean hideUnauthorizedResources = false;
    @IncludeTypeSpecific(repoType = VIRTUAL)
    private boolean artifactoryRequestsCanRetrieveRemoteArtifacts = false;
    @IncludeTypeSpecific(packageType = {RepoType.Docker, RepoType.OCI,HelmOCI}, repoType = VIRTUAL)
    private boolean resolveDockerTagsByTimestamp = false;
    @IncludeTypeSpecific(packageType = {Maven, RepoType.Gradle, RepoType.Ivy, RepoType.SBT, P2}, repoType = VIRTUAL)
    private String keyPair = "";
    @IncludeTypeSpecific(packageType = {Maven, RepoType.Gradle, RepoType.Ivy, RepoType.SBT}, repoType = VIRTUAL)
    private String pomRepositoryReferencesCleanupPolicy = "discard_active_reference";
    @IncludeTypeSpecific(repoType = VIRTUAL)
    private String defaultDeploymentRepo;
    @IncludeTypeSpecific(packageType = {RepoType.Docker, RepoType.OCI,HelmOCI}, repoType = VIRTUAL)
    private boolean cachingLocalForeignLayersEnabled = false;
    @IncludeTypeSpecific(packageType = {RepoType.Bower, RepoType.Go, RepoType.Npm}, repoType = VIRTUAL)
    private boolean externalDependenciesEnabled = false;
    @IncludeTypeSpecific(packageType = {RepoType.Bower, RepoType.Go, RepoType.Npm}, repoType = VIRTUAL)
    private String externalDependenciesRemoteRepo;
    @IncludeTypeSpecific(packageType = {RepoType.Bower, RepoType.Go, RepoType.Npm}, repoType = VIRTUAL)
    private List<String> externalDependenciesPatterns;
    @IncludeTypeSpecific(packageType = {RepoType.Chef, RepoType.Conda, RepoType.CRAN, RepoType.Conan, Debian,
            RepoType.Helm, RepoType.Npm, RepoType.YUM}, repoType = VIRTUAL)
    private long virtualRetrievalCachePeriodSecs = 600;
    @IncludeTypeSpecific(packageType = Maven, repoType = VIRTUAL)
    private boolean forceMavenAuthentication = false;
    @IncludeTypeSpecific(packageType = Debian, repoType = VIRTUAL)
    private String debianDefaultArchitectures;
    @IncludeTypeSpecific(packageType = Debian, repoType = VIRTUAL)
    private List<String> optionalIndexCompressionFormats;
    @IncludeTypeSpecific(packageType = P2, repoType = VIRTUAL)
    private List<String> p2Urls;

    @IncludeTypeSpecific(packageType = Helm, repoType = VIRTUAL)
    @Getter
    @Setter
    private boolean useNamespaces = false;
    @IncludeTypeSpecific(packageType = Cargo, repoType = VIRTUAL)
    @Getter
    private final boolean cargoInternalIndex = true;

    @IncludeTypeSpecific(packageType = Cargo, repoType = {VIRTUAL})
    @Override
    public boolean isCargoAnonymousAccess() {
        return this.cargoConfiguration != null && this.cargoConfiguration.isCargoAnonymousAccess();
    }

    public void setCargoAnonymousAccess(boolean enableCargoAnonymousAccess) {
        getCargoConfiguration().setCargoAnonymousAccess(enableCargoAnonymousAccess);
    }

    public VirtualRepositoryConfigurationImpl() {
    }

    public VirtualRepositoryConfigurationImpl(VirtualRepoConfig virtualRepoConfig) {
        super(virtualRepoConfig, TYPE);
        setRepoTypeConfig(virtualRepoConfig);
        setPackageTypeConfig(virtualRepoConfig);
        setSecurityConfig(virtualRepoConfig);
    }

    private void setPackageTypeConfig(VirtualRepoConfig virtualRepoConfig) {
        setMavenConfigIfNeeded(virtualRepoConfig);
        setHelmConfigIfNeeded(virtualRepoConfig);
        setDockerConfigIfNeeded(virtualRepoConfig);
        setExternalDependenciesConfigIfNeeded(virtualRepoConfig);
        setP2ConfigIfNeeded(virtualRepoConfig);
        setDebianConfigIfNeeded(virtualRepoConfig);
        setCargoConfigIfNeeded(virtualRepoConfig);
        setHuggingFaceMLConfigIfNeeded(virtualRepoConfig);
    }

    private void setDebianConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        String debianFormats = virtualRepoConfig.getPackageTypeConfig().get(
                DEBIAN_OPTIONAL_INDEX_COMPRESSION_FORMATS.getKey());
        if (StringUtils.isBlank(debianFormats)) {
            setOptionalIndexCompressionFormats(null);
        } else {
            setOptionalIndexCompressionFormats(JsonUtils.getInstance().readValue(debianFormats, new TypeReference<>() {
            }));
        }
        setDebianDefaultArchitectures(
                virtualRepoConfig.getPackageTypeConfig().get(DEBIAN_DEFAULT_ARCHITECTURES.getKey()));
    }

    private void setP2ConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        if (virtualRepoConfig.getPackageTypeConfig().containsKey(P2_URLS.getKey())) {
            String p2UrlsJsonList = virtualRepoConfig.getPackageTypeConfig().get(P2_URLS.getKey());
            setP2Urls(JsonUtils.getInstance().readValue(p2UrlsJsonList, new TypeReference<>() {
            }));
        }
    }

    private void setExternalDependenciesConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        setExternalDependenciesRemoteRepo(
                virtualRepoConfig.getPackageTypeConfig().get(EXTERNAL_DEPENDENCIES_REMOTE_REPO_REF.getKey()));
        String dependenciesEnabled =
                virtualRepoConfig.getPackageTypeConfig().get(EXTERNAL_DEPENDENCIES_ENABLED.getKey());
        if (StringUtils.isNotBlank(dependenciesEnabled)) {
            setExternalDependenciesEnabled(Boolean.parseBoolean(dependenciesEnabled));
        }
        String dependenciesPatterns =
                virtualRepoConfig.getPackageTypeConfig().get(EXTERNAL_DEPENDENCIES_PATTERNS.getKey());
        if (StringUtils.isNotBlank(dependenciesPatterns)) {
            setExternalDependenciesPatterns(
                    JsonUtils.getInstance().readValue(dependenciesPatterns, new TypeReference<>() {
                    }));
        }
    }

    private void setMavenConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        if (virtualRepoConfig.getPackageType().isMavenGroup()) {
            Map<String, String> pomCleanupPolicies = extractXmlValueFromEnumAnnotations(PomCleanupPolicy.class);
            pomCleanupPolicies.entrySet().stream()
                    .filter(pomCleanupPolicy -> pomCleanupPolicy.getKey()
                            .equals(virtualRepoConfig.getPackageTypeConfig().get(
                                    POM_REPOSITORY_REFERENCES_CLEANUP_POLICY.getKey())))
                    .forEach(pomCleanupPolicy -> setPomRepositoryReferencesCleanupPolicy(pomCleanupPolicy.getKey()));
            setForceMavenAuthentication(Boolean.parseBoolean(
                    virtualRepoConfig.getPackageTypeConfig().get(FORCE_MAVEN_AUTHENTICATION.getKey())));
        }
    }

    private void setDockerConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        if (virtualRepoConfig.getPackageType().isDockerGroup()) {
            setResolveDockerTagsByTimestamp(Boolean.parseBoolean(virtualRepoConfig.getPackageTypeConfig().get(
                    RESOLVE_DOCKER_TAGS_BY_TIMESTAMP.getKey())));
            setCachingLocalForeignLayersEnabled(Boolean.parseBoolean(virtualRepoConfig.getPackageTypeConfig().get(
                    CACHING_LOCAL_FOREIGN_LAYERS_ENABLED.getKey())));
        }
    }

    private void setCargoConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        if (Cargo == virtualRepoConfig.getPackageType()) {
            cargoConfiguration =
                    CargoPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        }
    }

    private void setHuggingFaceMLConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        if (HuggingFaceML == virtualRepoConfig.getPackageType()) {
            setMlRepoLayout(Boolean.parseBoolean(virtualRepoConfig.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT)));
        }
    }

    private void setHelmConfigIfNeeded(VirtualRepoConfig virtualRepoConfig) {
        if (virtualRepoConfig.getPackageType() == Helm) {
            setUseNamespaces(
                    Boolean.parseBoolean(virtualRepoConfig.getPackageTypeConfig().get(USE_NAMESPACES.getKey())));
        }
    }

    private void setRepoTypeConfig(VirtualRepoConfig virtualRepoConfig) {
        setArtifactoryRequestsCanRetrieveRemoteArtifacts(
                virtualRepoConfig.getRepoTypeConfig().isArtifactoryRequestsCanRetrieveRemoteArtifacts());
        List<String> newRepositories = virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs();
        setRepositories(newRepositories);
        if (virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef() != null) {
            setDefaultDeploymentRepo(virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef());
        }
        if (virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig() != null) {
            setVirtualRetrievalCachePeriodSecs(
                    virtualRepoConfig.getRepoTypeConfig().getVirtualCacheConfig().getVirtualRetrievalCachePeriodSecs());
        }
    }

    private void setSecurityConfig(VirtualRepoConfig virtualRepoConfig) {
        setHideUnauthorizedResources(virtualRepoConfig.getSecurityConfig().isHideUnauthorizedResources());
        String newKeyPair = virtualRepoConfig.getSecurityConfig().getKeyPair();
        if (StringUtils.isNotBlank(newKeyPair)) {
            setKeyPair(newKeyPair);
        }
    }

    @Override
    public String getDebianDefaultArchitectures() {
        return debianDefaultArchitectures;
    }

    public void setDebianDefaultArchitectures(String debianDefaultArchitectures) {
        this.debianDefaultArchitectures = debianDefaultArchitectures;
    }

    @Override
    public boolean isArtifactoryRequestsCanRetrieveRemoteArtifacts() {
        return artifactoryRequestsCanRetrieveRemoteArtifacts;
    }

    public void setArtifactoryRequestsCanRetrieveRemoteArtifacts(
            boolean artifactoryRequestsCanRetrieveRemoteArtifacts) {
        this.artifactoryRequestsCanRetrieveRemoteArtifacts = artifactoryRequestsCanRetrieveRemoteArtifacts;
    }

    @Override
    public boolean isResolveDockerTagsByTimestamp() {
        return resolveDockerTagsByTimestamp;
    }

    public void setResolveDockerTagsByTimestamp(boolean resolveDockerTagsByTimestamp) {
        this.resolveDockerTagsByTimestamp = resolveDockerTagsByTimestamp;
    }

    @Override
    public String getKeyPair() {
        return keyPair;
    }

    public void setKeyPair(String keyPair) {
        this.keyPair = keyPair;
    }

    @Override
    public String getPomRepositoryReferencesCleanupPolicy() {
        return pomRepositoryReferencesCleanupPolicy;
    }

    public void setPomRepositoryReferencesCleanupPolicy(String pomRepositoryReferencesCleanupPolicy) {
        this.pomRepositoryReferencesCleanupPolicy = pomRepositoryReferencesCleanupPolicy;
    }

    @Override
    public String getDefaultDeploymentRepo() {
        return defaultDeploymentRepo;
    }

    public void setDefaultDeploymentRepo(String defaultDeploymentRepo) {
        this.defaultDeploymentRepo = defaultDeploymentRepo;
    }

    public boolean isCachingLocalForeignLayersEnabled() {
        return cachingLocalForeignLayersEnabled;
    }

    public void setCachingLocalForeignLayersEnabled(boolean cachingLocalForeignLayersEnabled) {
        this.cachingLocalForeignLayersEnabled = cachingLocalForeignLayersEnabled;
    }

    @Override
    public List<String> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<String> repositories) {
        this.repositories = repositories == null ? null : repositories.stream().distinct().collect(toList());
    }

    @Override
    public boolean isExternalDependenciesEnabled() {
        return externalDependenciesEnabled;
    }

    public void setExternalDependenciesEnabled(boolean externalDependenciesEnabled) {
        this.externalDependenciesEnabled = externalDependenciesEnabled;
    }

    @Override
    public String getExternalDependenciesRemoteRepo() {
        return externalDependenciesRemoteRepo;
    }

    public void setExternalDependenciesRemoteRepo(String externalDependenciesRemoteRepo) {
        this.externalDependenciesRemoteRepo = externalDependenciesRemoteRepo;
    }

    @Override
    public List<String> getExternalDependenciesPatterns() {
        return externalDependenciesPatterns;
    }

    @Override
    public long getVirtualRetrievalCachePeriodSecs() {
        return virtualRetrievalCachePeriodSecs;
    }

    public void setVirtualRetrievalCachePeriodSecs(long virtualRetrievalCachePeriodSecs) {
        this.virtualRetrievalCachePeriodSecs = virtualRetrievalCachePeriodSecs;
    }

    public void setExternalDependenciesPatterns(List<String> externalDependenciesPatterns) {
        this.externalDependenciesPatterns = externalDependenciesPatterns;
    }

    @Override
    public List<String> getOptionalIndexCompressionFormats() {
        return this.optionalIndexCompressionFormats;
    }

    public void setOptionalIndexCompressionFormats(List<String> optionalIndexCompressionFormats) {
        this.optionalIndexCompressionFormats = optionalIndexCompressionFormats;
    }

    @Override
    public boolean isForceMavenAuthentication() {
        return forceMavenAuthentication;
    }

    public void setForceMavenAuthentication(boolean forceMavenAuthentication) {
        this.forceMavenAuthentication = forceMavenAuthentication;
    }

    public List<String> getP2Urls() {
        return p2Urls;
    }

    public void setP2Urls(List<String> p2Urls) {
        this.p2Urls = p2Urls;
    }

    @Override
    public boolean isHideUnauthorizedResources() {
        return hideUnauthorizedResources;
    }

    public void setHideUnauthorizedResources(boolean hideUnauthorizedResources) {
        this.hideUnauthorizedResources = hideUnauthorizedResources;
    }
}
