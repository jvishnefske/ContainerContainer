package org.artifactory.repo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.SpecialAction;
import org.artifactory.repo.RepoConfigChange.ConfigAction;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;

@Data
@Builder
@AllArgsConstructor
@ToString
public class RepoConfigReload {

    private String repoKey;
    private RepoDetailsType repoType;
    private RepoType packageType;
    private ConfigAction configAction;
    private SpecialAction specialAction = SpecialAction.NONE;
    private long modification;

    public RepoConfigReload(RepoConfigChange repoConfigChange) {
        this(repoConfigChange.getRepoKey(), repoConfigChange.getRepoType(), repoConfigChange.getPackageType(),
                repoConfigChange.getConfigAction(), resolveAndGetModification(repoConfigChange));
    }

    public RepoConfigReload(String repoKey, RepoDetailsType repoType, RepoType packageType,
            ConfigAction configAction, long modification) {
        this.repoKey = repoKey;
        this.repoType = repoType;
        this.packageType = packageType;
        this.configAction = configAction;
        this.modification = modification;
    }

    private static long resolveAndGetModification(RepoConfigChange repoConfigChange) {
        RepoConfig oldRepoConfig = repoConfigChange.getOldRepoConfig();
        RepoConfig newRepoConfig = repoConfigChange.getNewRepoConfig();
        long modification = 0L;
        if (repoConfigChange.getConfigAction() == REPO_DELETE && oldRepoConfig != null) {
            modification = oldRepoConfig.getModification();
        } else if (newRepoConfig != null) {
            modification = newRepoConfig.getModification();
        }
        return modification;
    }

    public BasicRepoConfig toBasic() {
        return new BasicRepoConfig(repoKey, modification, repoType, packageType);
    }
}