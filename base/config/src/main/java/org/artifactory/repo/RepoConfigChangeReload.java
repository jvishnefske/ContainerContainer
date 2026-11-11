package org.artifactory.repo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.SpecialAction;
import org.artifactory.repo.packagetype.PomCleanupPolicy;
import org.artifactory.repo.packagetype.configuration.MavenConfiguration;
import org.jfrog.common.JsonUtils;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

@Getter
@ToString
public class RepoConfigChangeReload {

    private final RepoConfigChange repoConfigChange;
    @Setter
    private SpecialAction specialAction = SpecialAction.NONE;

    public RepoConfigChangeReload(RepoConfigChange repoConfigChange) {
        this.repoConfigChange = repoConfigChange;
        handleSpecialUpdateActions(repoConfigChange);
    }

    RepoConfigReload toRepoConfigReload() {
        return new RepoConfigReload(getRepoKey(), getRepoType(), getPackageType(), getConfigAction(),
                getModification());
    }

    public long getModification() {
        return repoConfigChange.getModification();
    }

    public RepoConfigChange.ConfigAction getConfigAction() {
        return repoConfigChange.getConfigAction();
    }

    public RepoType getPackageType() {
        return repoConfigChange.getPackageType();
    }

    public RepoDetailsType getRepoType() {
        return repoConfigChange.getRepoType();
    }

    public String getRepoKey() {
        return repoConfigChange.getRepoKey();
    }

    private void handleSpecialUpdateActions(RepoConfigChange repoConfigChange) {
        if (repoConfigChange.getConfigAction() == REPO_UPDATE) {
            handleSpecialVirtualAction(repoConfigChange);
            handleSpecialRemoteAction(repoConfigChange);
        }
    }

    private void handleSpecialRemoteAction(RepoConfigChange repoConfigChange) {
        if (repoConfigChange.getNewRepoConfig().getRepoType() == REMOTE &&
                isRemoteRepoConfigChanged((RemoteRepoConfig) repoConfigChange.getNewRepoConfig(),
                        (RemoteRepoConfig) repoConfigChange.getOldRepoConfig())) {
            setSpecialAction(SpecialAction.PERFORM_CONFIGURATION_REPLICATOR);
        }
    }

    private void handleSpecialVirtualAction(RepoConfigChange repoConfigChange) {
        if (repoConfigChange.getNewRepoConfig().getRepoType() == VIRTUAL &&
                isPomCleanUpPolicyChanged(repoConfigChange.getNewRepoConfig(), repoConfigChange.getOldRepoConfig())) {
            setSpecialAction(SpecialAction.PERFORM_POM_CLEANUP_POLICY);
        }
    }

    private boolean isPomCleanUpPolicyChanged(RepoConfig newRepoConfig, RepoConfig oldRepoConfig) {
        MavenConfiguration newMavenConfig = getDeserializedType(MavenConfiguration.class, newRepoConfig);
        MavenConfiguration oldMavenConfig = getDeserializedType(MavenConfiguration.class, oldRepoConfig);
        PomCleanupPolicy newPolicy = newMavenConfig.getPomRepositoryReferencesCleanupPolicy();
        PomCleanupPolicy oldPolicy = oldMavenConfig.getPomRepositoryReferencesCleanupPolicy();
        return !newPolicy.equals(oldPolicy);
    }

    private static <T> T getDeserializedType(Class<T> packageTypeModel, RepoConfig repositoryConfig) {
        JsonUtils jsonMapper = JsonUtils.getInstance();
        byte[] packageTypeConfigByKey =
                jsonMapper.valueToByteArray(repositoryConfig.getPackageTypeConfig());
        return jsonMapper.readValue(packageTypeConfigByKey, packageTypeModel);
    }

    private boolean isRemoteRepoConfigChanged(RemoteRepoConfig newConfig, RemoteRepoConfig oldConfig) {
        return newConfig.getRepoTypeConfig().getSocketTimeoutMillis() !=
                (oldConfig.getRepoTypeConfig().getSocketTimeoutMillis()) ||
                !StringUtils.equals(newConfig.getRepoTypeConfig().getUrl(), oldConfig.getRepoTypeConfig().getUrl());
    }
}