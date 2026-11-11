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

import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.ModuleInfoUtils;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigCopyFactory;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.cache.expirable.UniqueSnapshotArtifactExpirableOverridable;
import org.artifactory.repo.service.MutableRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.util.PathMatcher;
import org.jfrog.client.util.PathUtils;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Fred Simon
 */
public abstract class RepoBase<T extends RepoDescriptor, S extends RepoConfig>
        implements Repo<T, S>, MutableRepo<S> {
    private final T descriptor;
    private final S repoConfig;
    // No need for thread-safety as this should only be used during repo service cache reload, which is
    // single-threaded and not concurrent with repo access.
    private S mutableRepoConfig;
    private final InternalRepositoryService repositoryService;
    private List<String> includes;
    protected Pattern patternExcludes;
    protected List<String> excludes;
    @Getter
    protected CentralConfigService configService;
    private boolean isCaseInSensitive;

    protected RepoBase(T descriptor, S repoConfig, InternalRepositoryService repositoryService,
            CentralConfigService configService) {
        this.descriptor = descriptor;
        this.repoConfig = repoConfig;
        this.repositoryService = repositoryService;
        this.isCaseInSensitive = isPackageCaseInSensitive();
        this.includes = getPattern(repoConfig.getBaseConfig().getIncludesPattern());
        this.excludes = getPattern(repoConfig.getBaseConfig().getExcludesPattern());
        this.configService = configService;
    }

    private List<String> getPattern(String pattern) {
        if (isCaseInSensitive && pattern != null) {
            pattern = pattern.toLowerCase();
        }
        return PathUtils.includesExcludesPatternToStringList(pattern);
    }

    private boolean isPackageCaseInSensitive() {
        return isPackageTypePresent() && ConstantValues.caseInsensitivePackages.getList()
                .contains(this.getDescriptor().getType().getType());
    }

    private boolean isPackageTypePresent() {
        return this.getDescriptor() != null && this.getDescriptor().getType() != null &&
                this.getDescriptor().getType().getType() != null;
    }

    @Override
    public T getDescriptor() {
        return descriptor;
    }

    @Override
    public S getRepoConfig() {
        if (mutableRepoConfig != null) {
            return mutableRepoConfig;
        }
        return repoConfig;
    }

    @Override
    public S getMutableRepoConfig() {
        if (mutableRepoConfig == null) {
            mutableRepoConfig = RepoConfigCopyFactory.copy(repoConfig);
        }
        return mutableRepoConfig;
    }

    @Override
    public RepoPath getRepoPath(String path) {
        return InternalRepoPathFactory.create(getKey(), path);
    }

    @Override
    public InternalRepositoryService getRepositoryService() {
        return repositoryService;
    }

    @Override
    public ModuleInfo getItemModuleInfo(String itemPath) {
        ModuleInfo moduleInfo = getDescriptorModuleInfo(itemPath);

        if (!moduleInfo.isValid()) {
            moduleInfo = getArtifactModuleInfo(itemPath);
        }

        return moduleInfo;
    }

    @Override
    public ModuleInfo getArtifactModuleInfo(String artifactPath) {
        String repoLayoutRef = getRepoConfig().getBaseConfig().getRepoLayoutRef();
        RepoLayout repoLayout = null;
        if (repoLayoutRef != null) {
            repoLayout = configService.getDescriptor().getRepoLayout(repoLayoutRef);
        }
        if (StringUtils.isBlank(artifactPath) || (repoLayout == null)) {
            return new ModuleInfo();
        }

        return ModuleInfoUtils.moduleInfoFromArtifactPath(artifactPath, repoLayout);
    }

    @Override
    public ModuleInfo getDescriptorModuleInfo(String descriptorPath) {
        String repoLayoutRef = getRepoConfig().getBaseConfig().getRepoLayoutRef();
        RepoLayout repoLayout = null;
        if (repoLayoutRef != null) {
            repoLayout = configService.getDescriptor().getRepoLayout(repoLayoutRef);
        }
        if (StringUtils.isBlank(descriptorPath) || (repoLayout == null)) {
            return new ModuleInfo();
        }
        return ModuleInfoUtils.moduleInfoFromDescriptorPath(descriptorPath, repoLayout);
    }

    @Override
    public String getKey() {
        return repoConfig.getKey();
    }

    @Override
    public String getDescription() {
        return repoConfig.getBaseConfig().getDescription();
    }

    @Override
    public boolean isReal() {
        return getRepoConfig().isReal();
    }

    @Override
    public boolean isCache() {
        return false;
    }

    public boolean accepts(RepoPath repoPath) {
        if (repoPath.isRoot()) {
            return true;    // always accepts the root path
        }
        String path = repoPath.getPath();
        if (NamingUtils.isSystem(path)) {
            // includes/excludes should not affect system paths
            return true;
        }

        // Internal metadata should avoid including/excluding
        if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath())) {
            return true;
        }

        String toCheck = path;
        //For artifactory metadata the pattern apply to the object it represents
        if (NamingUtils.isProperties(path)) {
            toCheck = NamingUtils.getMetadataParentPath(path);
        } else if (NamingUtils.isChecksum(path)) {
            toCheck = FilenameUtils.removeExtension(path);
        }
        return isPathPatternValid(repoPath, toCheck);
    }

    /**
     * @param repoPath repo path to validate
     * @param path     folder path
     * @return True if the path pattern is valid according to include / exclude policy
     */
    public boolean isPathPatternValid(RepoPath repoPath, String path) {
        if (StringUtils.isBlank(path)) {
            return true;
        }
        if (isCaseInSensitive){
            path = path.toLowerCase();
        }
        return patternExcludes != null ? PathMatcher.matches(path, includes, patternExcludes, repoPath.isFolder()) :
                PathMatcher.matches(path, includes, excludes, repoPath.isFolder());
    }

    @Override
    public String getChecksum(String checksumPath, RepoResource resource) throws IOException {
        if (resource == null || !resource.isFound()) {
            throw new IOException("Could not get resource stream. Path not found: " + checksumPath + ".");
        }
        ChecksumType checksumType = ChecksumType.forFilePath(checksumPath);
        if (checksumType == null) {
            throw new IllegalArgumentException("Checksum type not found for path " + checksumPath);
        }
        return getChecksumPolicy().getChecksum(checksumType, resource.getInfo().getChecksums(), resource.getRepoPath());
    }

    @Override
    public String toString() {
        return getKey();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RepoBase)) {
            return false;
        }
        RepoBase base = (RepoBase) o;
        return repoConfig.equals(base.repoConfig);
    }

    @Override
    public int hashCode() {
        return repoConfig.hashCode();
    }

    @Override
    public void destroy() {
    }

    protected final AuthorizationService getAuthorizationService() {
        return InternalContextHelper.get().getAuthorizationService();
    }

    protected final SecurityService getSecurityService() {
        return ContextHelper.get().beanForType(SecurityService.class);
    }

    /**
     * Checks if the resource should not be cached by the requesting end, and marks it expirable if so.
     *
     * @param res resource to be checked
     */
    protected void checkAndMarkExpirableResource(RepoResource res, RepoPath requestArtifactPath) {
        if (res.isFound() && !res.isExpirable()) {
            String path = res.getRepoPath().getPath();
            RepoConfig internalRepoConfig = repositoryService.getRepositoryConfigByKey(res.getRepoPath().getRepoKey());
            if (internalRepoConfig != null) {
                CacheExpiry cacheExpiry = ContextHelper.get().beanForType(CacheExpiry.class);

                RepoType repoPackageType = internalRepoConfig.getPackageType();
                boolean markAsExpired = cacheExpiry
                        .isExpirable(repoPackageType, internalRepoConfig.getKey(), path);
                if (!markAsExpired) {
                    String repoLayoutRef = getRepoConfig().getBaseConfig().getRepoLayoutRef();
                    RepoLayout repoLayout = configService.getDescriptor().getRepoLayout(repoLayoutRef);
                    RepoType packageType = getRepoConfig().getPackageType();
                    UniqueSnapshotArtifactExpirableOverridable uniqueSnapshotArtifactExpirableOverridable
                            = new UniqueSnapshotArtifactExpirableOverridable();
                    markAsExpired = uniqueSnapshotArtifactExpirableOverridable
                            .isExpirable(packageType, repoLayout, requestArtifactPath);
                }
                if (markAsExpired) {
                    res.expirable();
                }
            }
        }
    }
}