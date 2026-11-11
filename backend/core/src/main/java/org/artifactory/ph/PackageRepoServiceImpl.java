package org.artifactory.ph;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import com.jfrog.ph.acl.virtual.resolution.PackageRequestContext;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.UpstreamRepoKind;
import org.artifactory.api.request.ArtifactoryHeaders;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.common.repo.RepoType;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.event.EventHintType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.repo.virtual.VirtualRepositoriesResolver;
import org.artifactory.util.DoesNotExistException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.artifactory.ph.PackageRepoTypeMapper.packageType;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.util.RepoPathUtils.removeDashCache;

/**
 * The service contains the minimal repository services needed for the packages
 */
@Slf4j
public class PackageRepoServiceImpl implements PackageRepoService {
    public static final String DOES_NOT_EXIST = " doesn't exist";
    private final InternalRepositoryService repositoryService;
    private final PropertiesService propertiesService;

    public PackageRepoServiceImpl(InternalRepositoryService repositoryService, PropertiesService propertiesService) {
        this.repositoryService = repositoryService;
        this.propertiesService = propertiesService;
    }

    @Override
    public boolean isVirtual(String repoKey) {
        return repositoryService.virtualRepositoryByKey(repoKey) != null;
    }

    @Override
    public boolean isRemote(String repoKey) {
        return repositoryService.remoteRepositoryByKey(repoKey) != null;
    }

    @Override
    public boolean isLocal(String repoKey) {
        return repositoryService.localOrFederatedRepositoryByKey(repoKey) != null;
    }

    @Override
    public void move(String sourceRepoKey, String targetRepoKey, String sourcePath, String targetPath)
            throws PackageException {
        RepoPath source = RepoPathFactory.create(sourceRepoKey, sourcePath);
        RepoPath target = RepoPathFactory.create(targetRepoKey, targetPath);
        try {
            repositoryService.move(source, target, false, true, true, false);
        } catch (Exception e) {
            log.error("Failed to execute move", e);
            throw new PackageException(SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage(), e);
        }
    }

    @Override
    public void copy(@NonNull String sourceRepoKey, @NonNull String targetRepoKey, @NonNull String sourcePath,
            @NonNull String targetPath) throws PackageException {
        RepoPath source = RepoPathFactory.create(sourceRepoKey, sourcePath);
        RepoPath target = RepoPathFactory.create(targetRepoKey, targetPath);
        try {
            repositoryService.copy(source, target, false, true, true);
        } catch (Exception e) {
            log.error("Failed to execute copy", e);
            throw new PackageException(SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage(), e);
        }
    }

    @Override
    public void copyToCache(@NonNull String sourceRepoKey, @NonNull String targetRepoKey, @NonNull String sourcePath,
            @NonNull String targetPath) throws PackageException {
        RepoPath source = RepoPathFactory.create(sourceRepoKey, sourcePath);
        RepoPath target = RepoPathFactory.create(targetRepoKey, targetPath);
        try {
            repositoryService.copyToCache(source, target, false, true, true, false, null, true);
        } catch (Exception e) {
            log.error("Failed to execute copy from {}:{} to {}:{} message: {}", sourceRepoKey, sourcePath,
                    targetRepoKey, targetPath, e.getMessage());
            log.debug("Failed to execute copy from {}:{} to {}:{}", sourceRepoKey, sourcePath,
                    targetRepoKey, targetPath, e);
            throw new PackageException(SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage(), e);
        }
    }

    @NonNull
    public RepoInfo getRepoInfo(String repoKey) throws PackageException {
        RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(repoKey);
        if (repoConfig != null) {
            if (repoConfig instanceof LocalConfig) {
                return new RepoInfo(RepoType.LOCAL, packageType(repoConfig));
            } else if (repoConfig.isReal()) {
                return new RepoInfo(RepoType.REMOTE, packageType(repoConfig));
            }
            return new RepoInfo(RepoType.VIRTUAL, packageType(repoConfig));
        }
        String msg = String.format("Couldn't find repo info for repo:%s", repoKey);
        throw new PackageRepoNotFoundException(msg);

    }

    @Override
    @Nullable
    public PackageType getPackageType(String repoKey) {
        Repo<?, ?> repo = getRepo(repoKey);
        if (repo != null) {
            return packageType(repo.getRepoConfig());
        }
        return null;
    }

    @Nullable
    private Repo<?, ?> getRepo(String repoKey) {
        Repo<?, ?> repo = repositoryService.localOrFederatedRepositoryByKey(repoKey);
        if (repo == null) {
            repo = repositoryService.remoteRepositoryByKey(repoKey);
            if (repo == null) {
                repo = repositoryService.virtualRepositoryByKey(repoKey);
            }
        }
        return repo;
    }

    @Override
    public void setAttributes(String repoKey, String path, Multimap<String, String> properties)
            throws PackageException {
        setAttributes(repoKey, path, properties, true);
    }

    @Override
    public void setAttributes(String repoKey, String path, Multimap<String, String> properties,
            boolean isInternalAttribute) throws PackageException {
        LocalRepo<?, ?> localRepo =
                getLocalRepoForPropertiesOperation(repoKey, "Cannot set properties on repository " + repoKey);
        if (localRepo == null) {
            return;
        }
        RepoPath repoPath = RepoPathFactory.create(localRepo.getKey(), path);
        Properties finalExistingProperties = propertiesService.getProperties(repoPath);
        properties.asMap().forEach((key, collection) -> {
            finalExistingProperties.removeAll(key);
            if (collection != null) {
                finalExistingProperties.putAll(key, collection);
            }
        });
        propertiesService.setProperties(repoPath, finalExistingProperties, isInternalAttribute);
    }

    @Override
    public void addAttribute(String repoKey, String path, String key, Set<String> valuesToAdd)
            throws PackageException {
        addAttribute(repoKey, path, key, valuesToAdd, true);
    }

    @Override
    public void addAttribute(String repoKey, String path, String key, Set<String> valuesToAdd,
            boolean isInternalAttribute) throws PackageException {
        LocalRepo<?, ?> localRepo = getLocalRepoForPropertiesOperation(repoKey,
                "Cannot add values for property " + key + " on repository " + repoKey);
        if (localRepo == null) {
            return;
        }
        propertiesService.addProperties(RepoPathFactory.create(localRepo.getKey(), path), key, valuesToAdd,
                isInternalAttribute);
    }

    @Override
    public void addAttributeWithMirroringExclusionHandling(String repoKey, String path, String key,
            String valueToExclude, Set<String> valuesToAdd, boolean allowReplication) throws PackageException {
        handleMirroringExclusion(key, valueToExclude, allowReplication, false);
        try {
            addAttribute(repoKey, path, key, valuesToAdd);
        } catch (PackageException e) {
            handleMirroringExclusion(key, valueToExclude, allowReplication, true);
            throw e;
        }
    }

    @NonNull
    public Map<String, Collection<String>> getAttributes(String repoKey, String path) throws PackageNotFoundException {
        LocalRepo<?, ?> localRepo =
                getLocalRepoForPropertiesOperation(repoKey, "Cannot read properties on repository " + repoKey);
        if (localRepo == null) {
            return Map.of();
        }
        RepoPath repoPath = RepoPathFactory.create(localRepo.getKey(), path);
        Properties properties = propertiesService.getProperties(repoPath);
        return properties.asMap();
    }

    @Override
    public void removeAttributes(String repoKey, String path, String propKey, @Nullable Set<String> valuesToRemove)
            throws PackageException {
        removeAttributes(repoKey, path, propKey, valuesToRemove, true);
    }

    @Override
    public void removeAttributes(String repoKey, String path, String propKey,
            @Nullable Set<String> valuesToRemove, boolean isInternalAttribute)
            throws PackageException {
        LocalRepo<?, ?> localRepo =
                getLocalRepoForPropertiesOperation(repoKey, "Cannot remove properties from repository " + repoKey);
        if (localRepo == null) {
            return;
        }
        RepoPath repoPath = RepoPathFactory.create(localRepo.getKey(), path);
        if (valuesToRemove == null) {
            propertiesService.deleteProperty(repoPath, propKey);
        } else {
            propertiesService.removePropertyValues(repoPath, propKey, valuesToRemove, isInternalAttribute);
        }
    }

    @Override
    public void removeAttributeWithMirroringExclusionHandling(String repoKey, String path, String key,
            String valueToExclude, Set<String> valuesToRemove, boolean allowReplication) throws PackageException {
        handleMirroringExclusion(key, valueToExclude, allowReplication, false);
        try {
            removeAttributes(repoKey, path, key, valuesToRemove);
        } catch (PackageException e) {
            handleMirroringExclusion(key, valueToExclude, allowReplication, true);
            throw e;
        }
    }

    private void handleMirroringExclusion(String key, String valueToExclude, boolean allowReplication,
            boolean isRemove) {
        EventHintType type = allowReplication ? EventHintType.ReplicationAllowed : EventHintType.FederatedReplication;
        String hintSuffix = "%s.%s".formatted(key, valueToExclude);
        String eventName = "%s%s".formatted(type.getPrefix(), hintSuffix);
        if (isRemove) {
            log.debug("Removing event hint to handle replicate dist-tag latest with EventHintType: {}",
                    eventName);
            EventLogHintManager.remove(type);
        } else {
            log.debug("Adding event hint to handle replicate dist-tag latest with EventHintType: {}", eventName);
            EventLogHintManager.put(type, hintSuffix);
        }
    }


    @Override
    public boolean existLocally(String repoKey, String path) {
        boolean exists;
        VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(repoKey);
        if (virtualRepo != null) {
            exists = virtualRepo.itemExists(path);
        } else {
            // in case of local, federated, and remote cache
            exists = repositoryService.exists(RepoPathFactory.create(repoKey, path));
        }
        return exists;
    }

    @Override
    public void undeployArtifact(String repoKey, String path) {
        repositoryService.undeploy(RepoPathFactory.create(repoKey, path));
    }

    @Override
    public void undeployArtifact(String repoKey, String path, boolean pruneEmptyFolders) {
        repositoryService.undeploy(RepoPathFactory.create(repoKey, path), true, pruneEmptyFolders);
    }

    @Override
    public void undeployArtifactsAtomically(String repoKey, Set<String> paths) throws PackageException {
        Set<RepoPath> sourceRepoPaths = paths.stream().
                map(p -> RepoPathFactory.create(repoKey, p))
                .collect(Collectors.toSet());
        try {
            repositoryService.move(sourceRepoPaths, TRASH_REPO_KEY, null, false, false);
        } catch (Exception e) {
            log.error("Failed to execute undeploy", e);
            throw new PackageException(SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage(), e);
        }
    }

    @Override
    public List<? extends PackageRepo> getSubRepositories(PackageRequestContext requestContext,
            boolean filterCacheRepositoriesDuplication) throws PackageException {
        VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(requestContext.getRepoKey());
        if (virtualRepo != null) {
            VirtualRepositoriesResolver resolver = virtualRepo.getVirtualRepositoriesResolver();
            RepoPath repoPath = RepoPathFactory.create(requestContext.getRepoKey(), requestContext.getPath());
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.putAll(requestContext.getRequestHeaders());
            boolean isFromAnotherArtifactory = headers.get(ArtifactoryHeaders.ARTIFACTORY_ORIGINATED) != null ||
                    headers.get(ArtifactoryHeaders.ORIGIN_ARTIFACTORY) != null;
            List<RealRepo<?, ?>> res = resolver.assembleSearchRepositoriesList(repoPath, isFromAnotherArtifactory);
            // filter remote cache repos only if the corresponding remote repositories are part of the list
            if (filterCacheRepositoriesDuplication && res.stream().anyMatch(realRepo -> !realRepo.isLocal())) {
                res = res.stream().filter(realRepo -> !realRepo.isCache()).collect(Collectors.toList());
            }
            return res;
        }
        throw new PackageRepoNotFoundException(
                "Virtual repo with name:" + requestContext.getRepoKey() + DOES_NOT_EXIST);
    }

    @Override
    public String getDefaultDeploymentRepoKey(String repoKey) throws PackageException {
        try {
            if (repositoryService.isVirtualRepoExist(repoKey)) {
                return repositoryService.getDefaultDeploymentRepoKey(repoKey);
            }
            return repoKey;
        } catch (DoesNotExistException e) {
            throw new PackageNotFoundException(e.getMessage(), e);
        }
    }

    @Override
    public void undeployVirtualMetadataCache(String localRepoKey, String virtualRepoMetadataRoot) {
        log.info("Triggering virtual metadata cache eviction for local repository '{}' for path '{}'",
                localRepoKey, virtualRepoMetadataRoot);
        try {
            getVirtualReposContainingRepo(localRepoKey)
                    .forEach(repo -> evictVirtualCache(repo.getKey(), virtualRepoMetadataRoot));
        } catch (PackageException e) {
            log.error("Failed to retrieve virtual repositories list containing repo '{}' with an error: {}",
                    localRepoKey, e.getPublicReason());
        }
    }

    @Override
    public void zapVirtualMetadataCache(String localRepoKey, String path) {
        log.info("Triggering virtual metadata cache zap (expire) for local repository '{}' for path '{}'",
                localRepoKey, path);
        try {
            getVirtualReposContainingRepo(localRepoKey)
                    .forEach(repo -> zapVirtualCache(repo.getKey(), path));
        } catch (PackageException e) {
            log.error("Failed to retrieve virtual repositories list containing repo '{}' with an error: {}",
                    localRepoKey, e.getPublicReason());
        }
    }

    @Override
    public String getRemoteRepoBaseUrl(String remoteRepoKey) throws PackageException {
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
        if (remoteRepo == null) {
            String message = "Remote repo " + remoteRepoKey + " does not exist";
            throw new PackageRepoNotFoundException(message);
        }
        return remoteRepo.getUrl();
    }

    @Override
    public List<PackageRepo> getAllReposByType(@NonNull PackageType packageType, @NonNull RepoType repoType) {
        Stream<? extends Repo> reposStream;
        switch (repoType) {
            case LOCAL:
            case REMOTE:
                reposStream = repositoryService.getLocalFederatedAndRemoteRepositories().stream()
                        .filter(repo -> !repo.isCache() &&
                                ((repo.isLocal() && repoType.isLocal()) || !repo.isLocal() && repoType.isRemote()));
                break;
            case VIRTUAL:
                reposStream = repositoryService.getVirtualRepositories().stream();
                break;
            default:
                reposStream = Stream.empty();
        }

        return reposStream.filter(repo -> packageType.equals(packageType(repo.getDescriptor().getType())))
                .collect(Collectors.toList());
    }

    public void removeFromRemoteRepoCaches(String repoKey, String path, boolean removeSubPaths)
            throws PackageException {
        RemoteRepo<?, ?> repo = getRemoteWithCacheRepo(repoKey);
        if (repo == null) {
            String message =
                    String.format(
                            "Remove path %s from caches must be with remote repo with cache, %s is not remote repo or" +
                                    " not have cache", path, repoKey);
            throw new PackageException(HttpStatus.SC_BAD_REQUEST, message, message);
        }
        repo.removeFromCaches(path, removeSubPaths);
    }

    @Nullable
    private RemoteRepo<?, ?> getRemoteWithCacheRepo(@NonNull String remoteRepoKey) {
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
        if (isRemoteWithCacheRepo(remoteRepo)) {
            return remoteRepo;
        }
        return null;
    }

    private boolean isRemoteWithCacheRepo(RemoteRepo<?, ?> remoteRepo) {
        return remoteRepo != null && remoteRepo.isStoreArtifactsLocally() && remoteRepo.getLocalCacheRepo() != null;
    }

    /**
     * Check if index exists in virtual repo. Index could never been created or already evicted.
     * if exists delete all indexes under path
     */
    private void evictVirtualCache(String virtualKey, String metadataPath) {
        if (existLocally(virtualKey, metadataPath)) {
            log.debug("Evicting virtual cache at {}:{}", virtualKey, metadataPath);
            undeployArtifact(virtualKey, metadataPath);
        }
    }

    /**
     * Expire every cached metadata file under the given virtual cache folder while keeping the bytes on disk.
     * The folder holds one file per permission-hash/strategy variant, so each child file is zapped
     * individually. Unlike {@link #evictVirtualCache}, the previous content survives and can be served as a
     * stale fallback if a rebuild fails.
     */
    private void zapVirtualCache(String virtualKey, String metadataRoot) {
        if (!existLocally(virtualKey, metadataRoot)) {
            log.debug("No virtual cache to zap at {}:{}", virtualKey, metadataRoot);
            return;
        }
        RepoPath rootPath = RepoPathFactory.create(virtualKey, metadataRoot);
        // Enumerate the cached nodes directly (node/file service) rather than via the browsing tree, which for
        // a virtual repo aggregates member content instead of exposing the internal virtual cache folder.
        List<ItemInfo> children = repositoryService.getChildrenStream(rootPath).toList();
        int zapped = 0;
        for (ItemInfo child : children) {
            if (!child.isFolder()) {
                repositoryService.zapFileFromVirtual(child.getRepoPath());
                zapped++;
            }
        }
        if (zapped == 0) {
            log.warn("No virtual cache metadata files were zapped at {}:{} (found {} children)",
                    virtualKey, metadataRoot, children.size());
        } else {
            log.debug("Zapped {} virtual cache metadata file(s) at {}:{}", zapped, virtualKey, metadataRoot);
        }
    }

    @VisibleForTesting
    List<PackageRepo> getVirtualReposContainingRepo(String repoKey)
            throws PackageException {
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (repoDescriptor == null) {
            throw new PackageRepoNotFoundException("Repo with name:" + repoKey + DOES_NOT_EXIST);
        }
        try {
            return repositoryService.getVirtualReposContainingRepo(repoDescriptor)
                    .stream()
                    .filter(virtualDescriptor -> repoDescriptor.getType().equals(virtualDescriptor.getType()))
                    .map(virtualDescriptor -> repositoryService.repositoryByKey(virtualDescriptor.getKey()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new PackageNotFoundException(e.getMessage(), e);
        }
    }

    public boolean isRemoteRepoOffline(@NonNull String repoKey) throws PackageNotFoundException {
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(repoKey);
        if (remoteRepo == null) {
            throw new PackageRepoNotFoundException("Remote repository " + repoKey + " was not found");
        }
        return remoteRepo.isOffline() || remoteRepo.isAssumedOffline() || remoteRepo.isBlackedOut();
    }

    @Override
    @NonNull
    public String getRemoteCacheRepoKey(@NonNull String remoteRepoKey) throws PackageNotFoundException {
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
        if (remoteRepo == null) {
            throw new PackageRepoNotFoundException("Remote repository " + remoteRepoKey + " was not found");
        }

        if (remoteRepo.isStoreArtifactsLocally()) {
            LocalCacheRepo localCacheRepo = remoteRepo.getLocalCacheRepo();
            if (localCacheRepo != null) {
                return localCacheRepo.getKey();
            }
        }
        throw new PackageNotFoundException(
                "Remote repository " + remoteRepoKey + " does not allow storing artifacts locally.");
    }

    @Override
    public void clearRemoteRepoCache(@NonNull String remoteRepoKey) {
        RemoteRepo remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
        if (remoteRepo == null) {
            log.error("Could not zap remote cache for repository {}: repo not found.", remoteRepoKey);
            return;
        }
        log.debug("Zapping remote cache for repository {}", remoteRepoKey);
        remoteRepo.clearCaches();
    }

    /**
     * @param errorMsg - log/exception message in case that repo doesn't exist
     * @return LocalRepo if exists, null if remote without cache.
     * @throws PackageNotFoundException - in case that repo doesn't exist
     */
    @Nullable
    private LocalRepo<?, ?> getLocalRepoForPropertiesOperation(String repoKey, String errorMsg)
            throws PackageNotFoundException {
        LocalRepo<?, ?> localRepo = repositoryService.localFederatedOrCachedRepositoryByKey(repoKey);
        if (localRepo == null) {
            if (repositoryService.remoteRepositoryByKey(repoKey) != null) {
                return null;
            }
            log.error(errorMsg + ". Only local, federated and cached repositories are supported.");
            throw new PackageNotFoundException(errorMsg);
        }
        return localRepo;
    }

    @Override
    public boolean isFederatedRepo(@NonNull String repoKey) throws PackageException {
        Repo<?, ?> repo = getRepo(repoKey);
        if (repo == null) {
            throw new PackageRepoNotFoundException("Repository '" + repoKey + "' was not found");
        }
        return repo instanceof FederatedRepo;
    }

    @Override
    public boolean isPathAcceptedByRepo(@NonNull String repoKey, @NonNull String path) {
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        return repositoryService.isRepoPathAccepted(repoPath);
    }

    @Override
    public boolean isSmartRepo(@NonNull String repoKey) {
        RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(removeDashCache(repoKey));
        if (!(remoteRepo instanceof HttpRepo httpRepo)) {
            return false;
        }
        // Content synchronisation is the cheap local marker for a smart remote; only fall back to
        // upstream capability discovery when it is not enabled.
        if (httpRepo.getRepoConfig().isSmart()) {
            return true;
        }
        // Fail closed for path safeguards: SMART means fully smart-capable, but other kinds can still
        // be Artifactory (e.g. COMMON for npm virtuals, or UNKNOWN/UNAUTHORIZED when discovery is
        // inconclusive). Only skip when the upstream is definitively not Artifactory / not configured.
        // Do not use equals(SMART) or notSmart() here — those are for Edge entitlement checks.
        UpstreamRepoKind upstreamKind = httpRepo.getUpstreamRepoKind();
        return upstreamKind != UpstreamRepoKind.NOT_ARTIFACTORY &&
                upstreamKind != UpstreamRepoKind.NOT_CONFIGURED;
    }
}