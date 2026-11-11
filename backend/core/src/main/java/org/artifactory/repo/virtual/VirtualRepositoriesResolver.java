package org.artifactory.repo.virtual;

import com.google.common.collect.Lists;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.RepoRequests;
import org.artifactory.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

public class VirtualRepositoriesResolver {

    private final VirtualRepositoryConfigResolver virtualRepositoryConfigResolver;
    private final InternalRepositoryService repositoryService;
    private final VirtualRepo virtualRepo;
    private final AddonsManager addonsManager;
    private final CentralConfigService centralConfigService;
    private final PathTranslator pathTranslator;

    VirtualRepositoriesResolver(VirtualRepo virtualRepo) {
        this.virtualRepo = virtualRepo;
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        repositoryService = artifactoryContext.beanForType(InternalRepositoryService.class);
        centralConfigService = artifactoryContext.beanForType(CentralConfigService.class);
        addonsManager = artifactoryContext.beanForType(AddonsManager.class);
        virtualRepositoryConfigResolver = artifactoryContext.beanForType(VirtualRepositoryConfigResolver.class);
        pathTranslator = artifactoryContext.beanForType(PathTranslator.class);
    }

    public List<LocalRepo<?, ?>> getResolvedLocalAndFederatedRepos() {
        //Add all local repositories
        List<LocalRepo<?, ?>> repositories = Lists.newArrayList();
        LinkedHashSet<RepoConfig> configs = virtualRepositoryConfigResolver.getRepoConfigs(
                virtualRepo.getKey(), CollectionUtils.newLinkedHashSet(LOCAL, FEDERATED));
        for (RepoConfig repoConfig : configs) {
            String key = repoConfig.getKey();
            LocalRepo<?, ?> localRepo = repositoryService.localOrFederatedRepositoryByKey(key);
            if (localRepo != null) {
                repositories.add(localRepo);
            }
        }
        return repositories;
    }

    public List<LocalCacheRepo> getResolvedLocalCacheRepos() {
        //Add all local Cache repositories
        List<LocalCacheRepo> repositories = Lists.newArrayList();
        LinkedHashSet<RepoConfig> configs = virtualRepositoryConfigResolver.getRepoConfigs(
                virtualRepo.getKey(), CollectionUtils.newLinkedHashSet(REMOTE));
        for (RepoConfig repoConfig : configs) {
            String key = repoConfig.getKey();
            RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(key);
            if (remoteRepo != null) {
                LocalCacheRepo localCacheRepo = remoteRepo.getLocalCacheRepo();
                if (localCacheRepo != null) {
                    repositories.add(localCacheRepo);
                }
            }
        }
        return repositories;
    }

    public List<RemoteRepo<?, ?>> getResolvedRemoteRepos() {
        //Add all Remote repositories
        List<RemoteRepo<?, ?>> repositories = Lists.newArrayList();
        RepoRequests.logToContext("Appending collective remote repositories");
        LinkedHashSet<RepoConfig> configs = virtualRepositoryConfigResolver.getRepoConfigs(
                virtualRepo.getKey(), CollectionUtils.newLinkedHashSet(REMOTE));
        for (RepoConfig repoConfig : configs) {
            String key = repoConfig.getKey();
            RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(key);
            if (remoteRepo != null) {
                repositories.add(remoteRepo);
            }
        }
        return repositories;
    }


    /**
     * @return A list of local and remote repositories to search the resource in, ordered first by type (local non-cache
     * first) and secondly by order of appearance. We don't simply add all the lists since some of the real
     * repos are resolved transitively and we might have to remove them in case the path is excluded in the
     * virtual repo they belong to.
     */
    public List<RealRepo<?, ?>> assembleSearchRepositoriesList(RepoPath repoPath, boolean fromAnotherArtifactory) {
        RepoRequests.logToContext("Preparing list of aggregated repositories to search in");
        VirtualResolverRequestFilter filter = new VirtualResolverRequestFilter(virtualRepo, repoPath,
                repositoryService, pathTranslator, centralConfigService);
        LinkedHashSet<RepoConfig> repoConfigs = virtualRepositoryConfigResolver.getRepoConfigs(
                virtualRepo.getKey(), CollectionUtils.newLinkedHashSet(LOCAL, FEDERATED, REMOTE),
                filter, Integer.MAX_VALUE);

        // Four buckets preserve order: locals/caches then remotes, each split by priority; single pass over configs.
        List<RealRepo<?, ?>> prioritisedLocalOrCache = Lists.newArrayList();
        List<RealRepo<?, ?>> prioritisedRemote = Lists.newArrayList();
        List<RealRepo<?, ?>> nonPrioritisedLocalOrCache = Lists.newArrayList();
        List<RealRepo<?, ?>> nonPrioritisedRemote = Lists.newArrayList();

        for (RepoConfig repoConfig : repoConfigs) {
            if (!(repoConfig instanceof RealRepoConfig realRepoConfig)) {
                continue;
            }
            boolean priority = realRepoConfig.getRepoTypeConfig().isPriorityResolution();
            List<RealRepo<?, ?>> localOrCacheList = priority ? prioritisedLocalOrCache : nonPrioritisedLocalOrCache;
            List<RealRepo<?, ?>> remoteList = priority ? prioritisedRemote : nonPrioritisedRemote;

            if (repoConfig instanceof LocalConfig) {
                RepoRequests.logToContext("Appending collective local repositories");
                addLocalRepo(localOrCacheList, repositoryService.localOrFederatedRepositoryByKey(repoConfig.getKey()));
            } else if (repoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
                RepoRequests.logToContext("Appending collective local cache repositories");
                addLocalCacheRepo(localOrCacheList,
                        repositoryService.localFederatedOrCachedRepositoryByKey(repoConfig.getKey()));
                addRemoteRepo(fromAnotherArtifactory, remoteList, remoteRepoConfig.getKey());
            }
        }

        List<RealRepo<?, ?>> result = Lists.newArrayList();
        result.addAll(prioritisedLocalOrCache);
        result.addAll(prioritisedRemote);
        result.addAll(nonPrioritisedLocalOrCache);
        result.addAll(nonPrioritisedRemote);
        return result;
    }

    private void addRemoteRepo(boolean fromAnotherArtifactory,
            List<RealRepo<?, ?>> repositories, String remoteRepoKey) {

        if (fromAnotherArtifactory && !virtualRepo.isArtifactoryRequestsCanRetrieveRemoteArtifacts()) {
            //If the request comes from another artifactory don't bother checking any remote repos
            RepoRequests.logToContext("Collective remote repositories aren't appended - Download request was " +
                    "received from another Artifactory instance but is forbidden to search in remote repositories.");
        } else {
            RepoRequests.logToContext("Appending collective remote repositories");
            RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(remoteRepoKey);
            if (remoteRepo != null) {
                repositories.add(remoteRepo);
            }
        }
    }

    private void addLocalRepo(List<RealRepo<?, ?>> repositories, LocalRepo<?, ?> localRepo) {
        if (localRepo != null) {
            repositories.add(localRepo);
        }
    }

    private void addLocalCacheRepo(List<RealRepo<?, ?>> repositories, LocalRepo<?, ?> localRepo) {
        if (!addonsManager.isEntitled(EntitlementType.EDGE_MIXED)) {
            addLocalRepo(repositories, localRepo);
        }
    }
}
