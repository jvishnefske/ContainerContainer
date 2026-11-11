package org.artifactory.addon.huggingfaceml;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;

/**
 * @author shahafg
 */
@Slf4j
public class HuggingFaceLayoutRestore {

    static final String BACKUP_DIR = ".jfrog/old-layout";
    private final RepositoryService repositoryService;
    private final SecurityService securityService;
    private final boolean dryRun;

    public HuggingFaceLayoutRestore(RepositoryService repositoryService, SecurityService securityService,
            boolean dryRun) {
        this.repositoryService = repositoryService;
        this.securityService = securityService;
        this.dryRun = dryRun;
    }

    /**
     * Restore paths from ML layout to HF layout.
     * Will be executed only for HF repositories that have isMLLayout set to true
     * which means the HF repo hasn't been restored to HF layout yet
     *
     * @param repoKey repository key to run conversion for. It should be HuggingFaceML type local/remote repo.
     */
    public String restore(@Nonnull String repoKey) throws Exception {
        return securityService.callAsSystem(() -> {
            log.info("Restoring paths for repo {} with dryRun {}", repoKey, dryRun);
            String result = validateRepoForLayoutRestore(repoKey);
            if (result == null) {
                final RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
                updateLayoutToHF(repoConfig);
                // repo config is not null because it is checked in validateRepoForLayoutRestore
                String repoKeyForRestore = repoConfig.getRepoType() == RepoDetailsType.REMOTE ?
                        repoConfig.getKey() + "-cache" : repoConfig.getKey();
                pullBackupForRepoContent(repoKeyForRestore, repoConfig.getRepoType());
                result = String.format("Restore layout for repo %s %s", repoKey,
                        dryRun ? "done in dry run mode" : "done");
                log.info(result);
            }
            return result;
        });
    }

    private void updateLayoutToHF(RepoConfig repoConfig) {
        if (!dryRun) {
            // if you see an exception, change artifactory.runMode.dev=TRUE and restart.
            // repoConfig is not null because we checked it in validateRepoForLayoutRestore.
            repoConfig.getPackageTypeConfig().put(IS_ML_REPO_LAYOUT, "false");
            repositoryService.updateRepoConfigWithoutChecks(repoConfig);
        }
    }

    /**
     * Restore from backup all files from the repo to .jfrog/old-layout/datasets and .jfrog/old-layout/models
     */
    void pullBackupForRepoContent(String repoKeyForConversion, RepoDetailsType repoDetailsType) {
        restoreLayoutByEntityType(repoKeyForConversion, "datasets", repoDetailsType);
        restoreLayoutByEntityType(repoKeyForConversion, "models", repoDetailsType);
    }


    @VisibleForTesting
    String validateRepoForLayoutRestore(String repoKey) {
        final RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        String error = null;
        if (repoConfig == null) {
            error = String.format("Repository %s descriptor not found", repoKey);
        } else if (!RepoType.HuggingFaceML.equals(repoConfig.getPackageType())) {
            error = String.format("Repository %s is not HuggingFaceML type", repoKey);
        } else if ("false".equalsIgnoreCase(repoConfig.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT))) {
            error = String.format("Repository %s is already using old layout", repoKey);
        }
        if (error != null) {
            log.warn(error);
        }
        return error;
    }

    private void restoreLayoutByEntityType(@NonNull String repoKey, @NonNull String folderName,
            RepoDetailsType repoDetailsType) {
        String backupDir = String.join("/", BACKUP_DIR, folderName);
        RepoPath backupDirPath = RepoPathFactory.create(repoKey, backupDir);
        RepoPath rootFolderPath = RepoPathFactory.create(repoKey, folderName);
        RepoPath rootPath = RepoPathFactory.create(repoKey, StringUtils.EMPTY);
        if (repositoryService.exists(backupDirPath)) {
            if (repositoryService.exists(rootFolderPath) && !dryRun) {
                log.info("Delete all entities under the repo: {}, path: {}", repoKey, folderName);
                repositoryService.undeploy(rootFolderPath);
            }
            log.info("Restore all entities under the repo: {}, path: {} to root repository", repoKey, backupDir);

            if (repoDetailsType == RepoDetailsType.REMOTE) {
                repositoryService.copyToCache(backupDirPath, rootPath, dryRun, false, false, false, null, false);
            } else {
                repositoryService.copy(backupDirPath, rootPath, dryRun, false, true, false, null, false);
            }
        }
    }
}
