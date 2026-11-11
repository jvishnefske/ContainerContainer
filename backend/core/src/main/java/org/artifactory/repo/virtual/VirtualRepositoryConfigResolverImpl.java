package org.artifactory.repo.virtual;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.VirtualRepoConfigResolverService;
import org.artifactory.descriptor.repo.EmptyVirtualResolverFilter;
import org.artifactory.descriptor.repo.VirtualResolverFilter;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

/**
 * @author Vova Voloshin
 */
@Slf4j
@Component
public class VirtualRepositoryConfigResolverImpl implements VirtualRepositoryConfigResolver {

    private final VirtualRepoConfigResolverService virtualRepoConfigResolverService;

    @Autowired
    public VirtualRepositoryConfigResolverImpl(VirtualRepoConfigResolverService virtualRepoConfigResolverService) {
        this.virtualRepoConfigResolverService = virtualRepoConfigResolverService;
    }

    @Override
    public LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes) {
        return getRepoConfigs(key, repoDetailsTypes, new EmptyVirtualResolverFilter(), Integer.MAX_VALUE);
    }

    @Override
    public LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes,
            VirtualResolverFilter virtualResolverFilter) {
        return getRepoConfigs(key, repoDetailsTypes, virtualResolverFilter, Integer.MAX_VALUE);
    }

    @Override
    public LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes,
            Integer depth) {
        return getRepoConfigs(key, repoDetailsTypes, new EmptyVirtualResolverFilter(), depth);
    }

    @Override
    public LinkedHashSet<RepoConfig> getRepoConfigs(String key, LinkedHashSet<RepoDetailsType> repoDetailsTypes,
            VirtualResolverFilter virtualResolverFilter, Integer depth) {
        VirtualRepoConfig virtualRepoConfig = virtualRepoConfigResolverService.getVirtualRepoConfigByKey(key);
        LinkedHashSet<RepoConfig> repoConfigs = Sets.newLinkedHashSet();
        if (virtualRepoConfig != null) {
            VirtualConfigResolverHelper virtualConfigResolverHelper =
                    new VirtualConfigResolverHelper(Sets.newLinkedHashSet(), Sets.newLinkedHashSet(),
                            Sets.newLinkedHashSet(),
                            virtualResolverFilter,
                            virtualRepoConfigResolverService.getAllRepoConfigKeys());
            virtualConfigResolverHelper.resolve(virtualRepoConfig, Sets.newLinkedHashSet(), depth);
            for (RepoDetailsType repoDetailsType : repoDetailsTypes) {
                switch (repoDetailsType) {
                    case FEDERATED:
                    case LOCAL:
                        repoConfigs.addAll(virtualConfigResolverHelper.getLocalAndFederatedRepoConfigs());
                        break;
                    case REMOTE:
                        repoConfigs.addAll(virtualConfigResolverHelper.getRemoteRepoConfigs());
                        break;
                    case VIRTUAL:
                        repoConfigs.addAll(virtualConfigResolverHelper.getVirtualRepoConfigs());
                        break;
                    default:
                        break;
                }
            }
        }
        return repoConfigs;
    }

    @Override
    public List<RealRepoConfig> getOrderedRepos(String key) {
        return getRepoConfigs(key, CollectionUtils.newLinkedHashSet(LOCAL, FEDERATED, REMOTE)).stream()
                .map(RealRepoConfig.class::cast)
                .collect(Collectors.toList());
    }


    @Getter
    @AllArgsConstructor
    class VirtualConfigResolverHelper {
        private final LinkedHashSet<LocalConfig> localAndFederatedRepoConfigs;
        private final LinkedHashSet<RemoteRepoConfig> remoteRepoConfigs;
        private final LinkedHashSet<VirtualRepoConfig> virtualRepoConfigs;
        private final VirtualResolverFilter filter;
        private final Set<String> allRepoKeys;

        private void resolve(VirtualRepoConfig virtualRepoConfig, LinkedHashSet<String> visitedKeys, int depth) {
            String key = virtualRepoConfig.getKey();
            if (visitedKeys.contains(key)) {
                // don't visit twice the same virtual repo to prevent cycles
                log.debug("Virtual config {} already visited.", visitedKeys);
                return;
            }
            if (depth == 0) {
                return;
            }
            visitedKeys.add(key);
            if (!filter.accepts(virtualRepoConfig)) {
                return;
            }
            LinkedHashSet<RepoConfig> repoConfigs =
                    virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(virtualRepoConfig,
                            allRepoKeys);
            for (RepoConfig repoConfig : repoConfigs) {
                if (repoConfig instanceof LocalConfig) {
                    LocalConfig localRepoConfig = (LocalConfig) repoConfig;
                    localAndFederatedRepoConfigs.add(localRepoConfig);
                } else if (repoConfig instanceof RemoteRepoConfig) {
                    RemoteRepoConfig remoteRepoConfig = (RemoteRepoConfig) repoConfig;
                    remoteRepoConfigs.add(remoteRepoConfig);
                } else if (repoConfig instanceof VirtualRepoConfig virtualRepo) {
                    // resolve recursively
                    if (filter.accepts(virtualRepo)) {
                        virtualRepoConfigs.add(virtualRepo);
                        resolve(virtualRepo, visitedKeys, depth - 1);
                    }
                } else {
                    log.warn("Unexpected repository of type '{}'.", repoConfig.getClass());
                }
            }
        }
    }
}