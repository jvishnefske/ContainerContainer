package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.repo.RepoConfigReload;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * @author vovam
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RepoConfigPropagationModel {
    private Set<RepoConfigReload> repoConfigCreateOrUpdate;
    private Set<RepoConfigReload> repoConfigDelete;
    private boolean all;

    public boolean isEmpty() {
        return CollectionUtils.isEmpty(repoConfigCreateOrUpdate)
                && CollectionUtils.isEmpty(repoConfigDelete)
                && !all;
    }

    @JsonIgnore
    public Map<String, BasicRepoConfig> getRepoConfigCreatedOrUpdatedMap() {
        if (repoConfigCreateOrUpdate == null) {
            return Map.of();
        }
        return getRepoConfigCreateOrUpdate().stream()
                .collect(toMap(RepoConfigReload::getRepoKey, RepoConfigReload::toBasic));
    }

    @JsonIgnore
    public Set<String> getRepoConfigDeletedKeys() {
        if (repoConfigDelete == null) {
            return Set.of();
        }
        return repoConfigDelete.stream()
                .map(RepoConfigReload::getRepoKey)
                .collect(Collectors.toSet());
    }

    @JsonIgnore
    public Set<String> getRepoConfigCreatedKeys() {
        if (repoConfigCreateOrUpdate == null) {
            return Set.of();
        }
        return repoConfigCreateOrUpdate.stream()
                .filter(reload -> reload.getConfigAction() == REPO_CREATE)
                .map(RepoConfigReload::getRepoKey)
                .collect(Collectors.toSet());
    }

    @JsonIgnore
    public Set<String> getRepoConfigUpdatedKeys() {
        if (repoConfigCreateOrUpdate == null) {
            return Set.of();
        }
        return repoConfigCreateOrUpdate.stream()
                .filter(reload -> reload.getConfigAction() == REPO_UPDATE)
                .map(RepoConfigReload::getRepoKey)
                .collect(Collectors.toSet());
    }
}