package org.artifactory.repo.migration;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.repo.migration.mappers.RepoConfigMapperHolder;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.version.ArtifactoryConfigVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ConfigDescriptorRepoUtils {
    private static final ArtifactoryConfigVersion LAST_REPO_DESCRIPTOR_SUPPORT_CONFIG_VERSION =
            ArtifactoryConfigVersion.v321;

    public static int getRepoDescriptorTotalCount(CentralConfigDescriptor centralConfigDescriptor) {
        int localReposCount = getSize(centralConfigDescriptor.getLocalRepositoriesMap());
        int virtualReposCount = getSize(centralConfigDescriptor.getVirtualRepositoriesMap());
        int remoteReposCount = getSize(centralConfigDescriptor.getRemoteRepositoriesMap());
        int federatedReposCount = getSize(centralConfigDescriptor.getFederatedRepositoriesMap());
        int releaseBundlesReposCount = getSize(centralConfigDescriptor.getReleaseBundlesRepositoriesMap());
        int totalReposCount = localReposCount + virtualReposCount + remoteReposCount + federatedReposCount
                + releaseBundlesReposCount;
        log.debug("Local repositories count: {}. Virtual repositories count: {}. Remote repositories count: {}. " +
                        "Federated repositories count: {}. Release bundles repositories count: {}. " +
                        "Total repositories count: {}",
                localReposCount, virtualReposCount, remoteReposCount, federatedReposCount, releaseBundlesReposCount,
                totalReposCount);
        return totalReposCount;
    }

    public static void cleanupConfigDescriptorRepositories(MutableCentralConfigDescriptor centralConfigDescriptor) {
        centralConfigDescriptor.setLocalRepositoriesMap(new HashMap<>());
        centralConfigDescriptor.setFederatedRepositoriesMap(new HashMap<>());
        centralConfigDescriptor.setRemoteRepositoriesMap(new HashMap<>());
        centralConfigDescriptor.setVirtualRepositoriesMap(new HashMap<>());
        centralConfigDescriptor.setReleaseBundlesRepositoriesMap(new HashMap<>());
    }

    @VisibleForTesting
    static int getSize(Map<String, ?> map) {
        return MapUtils.isEmpty(map) ? 0 : map.size();
    }

    public static List<RepoConfig> mapRepoDescriptorsToRepoConfigs(CentralConfigDescriptor centralConfig,
            Set<RepositoriesConfigSubConverter> subConverters, RepositoryConfigMapper repositoryConfigMapper) {
        RepoConfigMapperHolder mappersHolder = new RepoConfigMapperHolder(subConverters);
        List<DbRepositoryConfig> localRepos =
                mappersHolder.map(LocalRepoDescriptor.class, centralConfig.getLocalRepositoriesMap());
        log.info("{} local repositories were mapped", localRepos.size());
        List<DbRepositoryConfig> remoteRepos =
                mappersHolder.map(RemoteRepoDescriptor.class, centralConfig.getRemoteRepositoriesMap());
        log.info("{} remote repositories were mapped", remoteRepos.size());
        List<DbRepositoryConfig> virtualRepos =
                mappersHolder.map(VirtualRepoDescriptor.class, centralConfig.getVirtualRepositoriesMap());
        log.info("{} virtual repositories were mapped", virtualRepos.size());
        List<DbRepositoryConfig> fedRepos = mappersHolder.map(FederatedRepoDescriptor.class,
                centralConfig.getFederatedRepositoriesMap());
        log.info("{} federated repositories were mapped", fedRepos.size());
        List<DbRepositoryConfig> releaseBundlesRepos = mappersHolder.map(ReleaseBundlesRepoDescriptor.class,
                centralConfig.getReleaseBundlesRepositoriesMap());
        log.debug("{} release bundles repositories were mapped", releaseBundlesRepos.size());
        List<DbRepositoryConfig> repositoryConfigs = new ArrayList<>();
        repositoryConfigs.addAll(localRepos);
        repositoryConfigs.addAll(remoteRepos);
        repositoryConfigs.addAll(virtualRepos);
        repositoryConfigs.addAll(fedRepos);
        repositoryConfigs.addAll(releaseBundlesRepos);
        return repositoryConfigs.stream()
                .map(repo -> repositoryConfigMapper.dbInfoBlobToRepositoryConfig(repo))
                // null = repo skipped by the unknown-package-type policy; drop it
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static boolean isRepoUpgradeShouldBeUpdated(ArtifactoryConfigVersion configVersion) {
        if (configVersion == null) {
            return true;
        }
        return configVersion.getVersion().before(LAST_REPO_DESCRIPTOR_SUPPORT_CONFIG_VERSION.getVersion());
    }
}