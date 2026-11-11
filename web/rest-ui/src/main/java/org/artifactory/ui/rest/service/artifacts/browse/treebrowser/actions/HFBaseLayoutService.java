package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.jfrog.common.concurrency.ConflictsGuard;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class HFBaseLayoutService<T> implements RestService<T> {
    private static final String OPERATION_COMPLETED_MSG_TEMPLATE =
            "Layout %s for HuggingFaceML repository %s completed";
    private static final String OPERATION_COMPLETED_WITH_RESULT_MSG_TEMPLATE =
            "Layout %s for HuggingFaceML repository %s completed with result: %s";
    private static final Set<RepoDetailsType> SUPPORTED_REPO_TYPES =
            Set.of(RepoDetailsType.LOCAL, RepoDetailsType.REMOTE, RepoDetailsType.FEDERATED);

    protected final RepositoryService repositoryService;
    protected final SecurityService securityService;
    protected final AddonsManager addonsManager;

    protected HFBaseLayoutService(RepositoryService repositoryService,
                               SecurityService securityService, AddonsManager addonsManager) {
        this.repositoryService = repositoryService;
        this.securityService = securityService;
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        String repoKey = extractRepoKey(request.getImodel());
        try {
            final ConflictsGuard<String> conflictsGuard = addonsManager.addonByType(HaAddon.class)
                    .getConflictsGuard(getGuardName());

            final RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
            if (repoConfig == null) {
                String message = String.format("Repository %s descriptor not found", repoKey);
                response.error(message);
                log.warn(message);
                return;
            }

            HFRepositoryTypeValidator.ValidationResult validationResult =
                    HFRepositoryTypeValidator.validateRepository(repoConfig, getSupportedRepoTypes(),
                            getOperationName(), repositoryService, response);

            if (!validationResult.valid()) {
                log.error("Validation failed for repository '{}'. Message {}", repoKey, validationResult.message());
                return;
            }

            String result = processRepo(repoKey, conflictsGuard);
            String message = buildCompletionMessage(repoKey, result);

            log.info(message);
            response.info(message);
        } catch (Exception e) {
            String message = String.format("Failed to run %s for HuggingFace repo %s",
                    getOperationName().toLowerCase(), repoKey);
            response.error(message);
            log.warn(message, e);
        }
    }

    private String processRepo(String repoKey, ConflictsGuard<String> lock) {
        String result;
        boolean locked = false;
        try {
            locked = lock.tryToLock(repoKey, 30, TimeUnit.SECONDS);
            setRepoBlackout(repoKey, true);

            result = performMigrationOrRestore(repoKey);
        } catch (InterruptedException e) {
            result = String.format("Failed to acquire lock for %s process", getOperationName().toLowerCase());
            log.warn("{}: '{}'. {}'", result, getGuardName(), e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            result = String.format("Exception occurred during %s process", getOperationName().toLowerCase());
            log.warn(result, e);
        } finally {
            if (locked) {
                setRepoBlackout(repoKey, false);
                lock.unlock(repoKey);
            }
        }
        return result;
    }

    private void setRepoBlackout(String repoKey, boolean isBlackedOut) {
        RealRepoConfig repoConfig = repositoryService.getRealRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            log.warn("Failed to black out repository '{}'", repoKey);
            return;
        }
        repoConfig.getRepoTypeConfig().setBlackedOut(isBlackedOut);
        repositoryService.updateRepoConfig(repoConfig);
    }

    private String buildCompletionMessage(String repoKey, String result) {
        if (result == null) {
            return String.format(OPERATION_COMPLETED_MSG_TEMPLATE, getOperationName(), repoKey);
        } else {
            return String.format(OPERATION_COMPLETED_WITH_RESULT_MSG_TEMPLATE, getOperationName(), repoKey, result);
        }
    }

    /**
     * Get the supported repository types for HF layout migrate operation.
     * @return Set of supported RepoDetailsTypes
     */
    protected Set<RepoDetailsType> getSupportedRepoTypes() {
        return SUPPORTED_REPO_TYPES;
    }

    protected abstract String extractRepoKey(@NonNull T model);

    /**
     * Perform the actual migration or restore operation.
     * @param repoKey the repository key to operate on
     * @return Optional result message, or null if no specific result
     * @throws Exception if any error occurs during the operation
     */
    @SuppressWarnings("squid:S112")
    protected abstract String performMigrationOrRestore(String repoKey) throws Exception;

    /**
     * Get the name of the operation, e.g. "migrate" or "restore".
     */
    protected abstract String getOperationName();

    /**
     * Get the name of the guard used for locking, e.g. "hf_migrate_layout_guard" or "hf_restore_layout_guard".
     */
    protected abstract String getGuardName();
}