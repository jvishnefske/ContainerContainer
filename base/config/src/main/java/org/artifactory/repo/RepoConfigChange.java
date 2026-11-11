package org.artifactory.repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;

import jakarta.annotation.Nullable;

import java.util.Optional;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;


/**
 * Class represent a repository CREATE/UPDATE/DELETE operation, it can be used before/after save operations and to
 * reload a state in the system after a change was done
 *
 * <p>The {@link #repoMetadata} field carries a pre-resolved snapshot of project / replication / environments /
 * shared-projects values, populated once at construction time by the change factory. Re-using the snapshot across
 * the BEFORE and AFTER interceptor phases keeps {@code AFTER_REPO_DELETE} payloads consistent with their BEFORE
 * counterpart, even though the repository is no longer present in descriptors / Access by the time the AFTER event
 * fires. The static {@code createOf/updateOf/deleteOf} factory methods leave it {@code null} (test convenience).
 *
 * @author shayb
 */
@Data
@AllArgsConstructor
public class RepoConfigChange {

    private String repoKey;
    private ConfigAction configAction;
    @Nullable
    private RepoConfig newRepoConfig;
    @Nullable
    private RepoConfig oldRepoConfig;
    @Nullable
    private RepoMetadata repoMetadata;

    public RepoConfigChange(String repoKey, ConfigAction configAction, @Nullable RepoConfig newRepoConfig,
            @Nullable RepoConfig oldRepoConfig) {
        this(repoKey, configAction, newRepoConfig, oldRepoConfig, null);
    }

    public enum ConfigAction {
        REPO_CREATE,
        REPO_UPDATE,
        REPO_DELETE,
        REFERENCED_PROXY_CHANGE,
        REFERENCED_LAYOUT_CHANGE
    }

    public static RepoConfigChange createOf(RepoConfig repoConfig) {
        return new RepoConfigChange(repoConfig.getKey(), REPO_CREATE, repoConfig, null);
    }

    public static RepoConfigChange updateOf(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        return new RepoConfigChange(oldRepoConfig.getKey(), REPO_UPDATE, newRepoConfig, oldRepoConfig);
    }

    public static RepoConfigChange deleteOf(RepoConfig repoConfig) {
        return new RepoConfigChange(repoConfig.getKey(), REPO_DELETE, null, repoConfig);
    }

    public boolean isCreate() {
        return configAction == REPO_CREATE;
    }

    public boolean isUpdate() {
        return configAction == REPO_UPDATE;
    }

    public boolean isDelete() {
        return configAction == REPO_DELETE;
    }

    public boolean isCreateOrUpdate() {
        return isCreate() || isUpdate();
    }

    public long getModification() {
        Long modification =
                Optional.ofNullable(extractModification(newRepoConfig)).orElse(extractModification(oldRepoConfig));
        if (modification == null) {
            modification = 0L;
        }
        return modification;
    }

    public RepoDetailsType getRepoType() {
        return Optional.ofNullable(extractRepoType(newRepoConfig)).orElse(extractRepoType(oldRepoConfig));
    }

    public RepoType getPackageType() {
        return Optional.ofNullable(extractPackageType(newRepoConfig)).orElse(extractPackageType(oldRepoConfig));
    }

    private Long extractModification(RepoConfig repoConfig) {
        return repoConfig == null ? null : repoConfig.getModification();
    }

    private RepoDetailsType extractRepoType(RepoConfig repoConfig) {
        return repoConfig == null ? null : repoConfig.getRepoType();
    }

    private RepoType extractPackageType(RepoConfig repoConfig) {
        return repoConfig == null ? null : repoConfig.getPackageType();
    }
}
