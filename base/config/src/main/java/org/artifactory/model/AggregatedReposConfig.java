package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Tamar Rosen
 */
@Data
@NoArgsConstructor
public class AggregatedReposConfig {
    List<LocalRepoConfig> localRepoConfigs;
    List<FederatedRepoConfig> federatedRepoConfigs;
    List<ReleaseBundleRepoConfig> releaseBundleRepoConfigs;
    List<RemoteRepoConfig> remoteRepoConfigs;
    List<VirtualRepoConfig> virtualRepoConfigs;

    public AggregatedReposConfig(Collection<RepoConfig> allRepositoryConfigs) {
        this.localRepoConfigs = new ArrayList<>();
        this.federatedRepoConfigs = new ArrayList<>();
        this.releaseBundleRepoConfigs = new ArrayList<>();
        this.remoteRepoConfigs = new ArrayList<>();
        this.virtualRepoConfigs = new ArrayList<>();
        setRepoConfigsByType(allRepositoryConfigs);
    }

    @JsonIgnore
    public List<RepoConfig> getAll() {
        return Stream.of(localRepoConfigs, federatedRepoConfigs, releaseBundleRepoConfigs, remoteRepoConfigs,
                virtualRepoConfigs).flatMap(Collection::stream).collect(Collectors.toList());
    }

    private void setRepoConfigsByType(Collection<RepoConfig> allRepositoryConfigs) {
        if (allRepositoryConfigs == null) {
            return;
        }
        allRepositoryConfigs.forEach(repoConfig -> {
            if (repoConfig instanceof FederatedRepoConfig federatedRepoConfig) {
                // Export federated repos as local repos with federation details
                // Convert to LocalRepoConfig (preserves gridTopologyId in BaseConfig)
                LocalRepoConfig localRepoConfig = LocalRepoConfig.fromFederated(federatedRepoConfig);
                localRepoConfigs.add(localRepoConfig);
            } else if (repoConfig instanceof ReleaseBundleRepoConfig releaseBundleRepoConfig) {
                releaseBundleRepoConfigs.add(releaseBundleRepoConfig);
            } else if (repoConfig instanceof LocalRepoConfig localRepoConfig) {
                localRepoConfigs.add(localRepoConfig);
            } else if (repoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
                remoteRepoConfigs.add(remoteRepoConfig);
            } else if (repoConfig instanceof VirtualRepoConfig virtualRepoConfig) {
                virtualRepoConfigs.add(virtualRepoConfig);
            }
        });
    }
}
