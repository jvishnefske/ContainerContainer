package org.artifactory.repo.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.RepoBulkRemovalReport;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.concurrent.ConflictsGuardUtils;
import org.artifactory.concurrent.LockAlreadyTakenException;
import org.artifactory.concurrent.LockingFailedException;
import org.artifactory.config.ConfigurationException;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ReloadableRepositoryConfigService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.interceptor.RepoConfigCudEventEmitter;
import org.artifactory.repo.metadata.RepoConfigChangeFactory;
import org.artifactory.spring.Reloadable;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FAILED_DEPENDENCY;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.artifactory.api.common.RepoRemovalStatus.CANNOT_DELETE_REPO_MSG_PREFIX;
import static org.artifactory.api.common.RepoRemovalStatus.REPO_DELETION_IN_PROGRESS;
import static org.artifactory.api.common.RepoRemovalStatus.REPO_NOT_FOUND;
import static org.artifactory.concurrent.NonStandardLockCategory.REPO_DELETION;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Reloadable(beanClass = RemoveRepositoryService.class, initAfter = {InternalRepositoryService.class,
        ReloadableRepositoryConfigService.class})
@Service
class RemoveRepositoryServiceImpl implements RemoveRepositoryService {

    private final RemoveRepositoryValidator removeRepositoryValidator;
    private final DeleteRepositoryContentService deleteRepositoryContentService;
    private final SecurityService securityService;
    private final RepoConfigCudEventEmitter repoConfigCudEventEmitter;
    private final RepoConfigChangeFactory repoConfigChangeFactory;
    private List<RepoConfigChangeInterceptor> repoConfigChangeInterceptors;

    @Override
    public void init() {
        repoConfigChangeInterceptors =
                Lists.newArrayList(ContextHelper.get().beansForType(RepoConfigChangeInterceptor.class).values());
    }

    @Override
    public BasicStatusHolder removeRepository(String repoKey) {
        try {
            return runWithRepoDeletionLock(repoKey, () -> {
                RepoConfig repoConfig = getRepoConfig(repoKey);
                if (repoConfig == null) {
                    return getRepoNotFoundStatus(repoKey);
                }
                removeRepositoryValidator.validate(repoConfig);
                return removeRepository(repoConfig);
            });
        } catch (RepositoryRemovalFailedException | RepositoryContentDeletionFailedException e) {
            return e.getStatusHolder();
        }
    }

    @Override
    public RepoBulkRemovalReport removeRepositories(LinkedHashSet<String> repoKeys) {
        try {
            return removeRepositoriesInternal(repoKeys);
        } catch (RepositoriesRemovalFailedException e) {
            return e.getRepoBulkRemovalReport();
        }
    }

    @Nonnull
    private RepoBulkRemovalReport removeRepositoriesInternal(LinkedHashSet<String> repoKeys) {
        validateRepoKeys(repoKeys);
        Set<RepoRemovalStatusHolder> statusHolders = new LinkedHashSet<>();
        Collection<RepoConfig> repoConfigs = filterOutAndRecordNonExistingRepos(repoKeys, statusHolders);
        validateAllRepositories(repoConfigs);
        removeRepositoriesInternal(repoConfigs, statusHolders);
        return new RepoBulkRemovalReport(statusHolders);
    }

    private Collection<RepoConfig> filterOutAndRecordNonExistingRepos(LinkedHashSet<String> repoKeys,
            Set<RepoRemovalStatusHolder> statusHolders) {
        Set<RepoConfig> repoConfigs = new LinkedHashSet<>();
        for (String repoKey : repoKeys) {
            RepoConfig repoConfig = getRepoConfig(repoKey);
            if (repoConfig != null) {
                repoConfigs.add(repoConfig);
            } else {
                statusHolders.add(getRepoNotFoundStatus(repoKey));
            }
        }
        return repoConfigs;
    }

    private static RepoRemovalStatusHolder getRepoNotFoundStatus(String repoKey) {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder(repoKey);
        statusHolder.mainStatus(REPO_NOT_FOUND.getReason(repoKey), REPO_NOT_FOUND.getStatusCode(), log);
        return statusHolder;
    }

    private static RepoRemovalStatusHolder getRepoDeletionInProgressStatus(String repoKey) {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder(repoKey);
        statusHolder.mainStatus(REPO_DELETION_IN_PROGRESS.getReason(repoKey),
                REPO_DELETION_IN_PROGRESS.getStatusCode(), log);
        return statusHolder;
    }

    private ConflictsGuard<String> getRepoDeletionLock() {
        return getAddonsManager().addonByType(HaCommonAddon.class).getConflictsGuard(REPO_DELETION.getName());
    }

    private void removeRepositoriesInternal(Collection<RepoConfig> repoConfigs,
            Set<RepoRemovalStatusHolder> statusHolders) {
        for (RepoConfig repoConfig : repoConfigs) {
            String repoKey = repoConfig.getKey();
            try {
                RepoRemovalStatusHolder statusHolder =
                        runWithRepoDeletionLock(repoKey, () -> removeRepository(repoConfig, true));
                statusHolders.add(toRepoRemovalStatusHolder(statusHolder, repoKey));
            } catch (RepositoryRemovalFailedException | RepositoryContentDeletionFailedException e) {
                // Best-effort bulk delete: record this repo's failure in the shared bulk status holder and continue,
                // so every repo is attempted and all per-repo statuses are surfaced in the RepoBulkRemovalReport.
                statusHolders.add(toRepoRemovalStatusHolder(e.getStatusHolder(), repoKey));
            }
        }
    }

    private static RepoRemovalStatusHolder toRepoRemovalStatusHolder(BasicStatusHolder statusHolder, String repoKey) {
        if (statusHolder instanceof RepoRemovalStatusHolder repoRemovalStatusHolder) {
            return repoRemovalStatusHolder;
        }
        RepoRemovalStatusHolder repoRemovalStatusHolder = new RepoRemovalStatusHolder(repoKey);
        for (StatusEntry entry : statusHolder.getEntries()) {
            if (entry.isError()) {
                repoRemovalStatusHolder.error(entry.getMessage(), entry.getStatusCode(), entry.getException(), log);
            } else if (entry.isWarning()) {
                repoRemovalStatusHolder.warn(entry.getMessage(), entry.getStatusCode(), entry.getException(), log);
            } else {
                repoRemovalStatusHolder.status(entry.getMessage(), entry.getStatusCode(), log);
            }
        }
        return repoRemovalStatusHolder;
    }

    /**
     * Runs the callable while holding the repo deletion lock for the given key. Converts lock failures to
     * status holders or rethrows business exceptions.
     *
     * @throws RepositoryRemovalFailedException         with status "REPO_DELETION_IN_PROGRESS" (202) if the lock is
     * already taken, or with status "SC_INTERNAL_SERVER_ERROR" (500) if lock acquisition failed due to an unexpected
     * error
     * @throws RepositoryContentDeletionFailedException if the content deletion flow failed due to a business
     *                                                  exception (e.g. some artifacts were non-removable)
     */
    private RepoRemovalStatusHolder runWithRepoDeletionLock(String repoKey, Callable<RepoRemovalStatusHolder> callable)
            throws RepositoryRemovalFailedException, RepositoryContentDeletionFailedException {
        long timeoutSecs = ConstantValues.haRepoDeletionLockTimeoutSecs.getLong();
        try {
            return ConflictsGuardUtils.runWithLocks(getRepoDeletionLock(), Set.of(repoKey), timeoutSecs, SECONDS,
                    callable);
        } catch (LockAlreadyTakenException e) {
            return getRepoDeletionInProgressStatus(repoKey);
        } catch (LockingFailedException e) {
            throw createLockAcquisitionFailure(repoKey, e.getMessage());
        } catch (RepositoryRemovalFailedException | RepositoryContentDeletionFailedException e) {
            throw e;
        } catch (Exception e) {
            throw createUnexpectedDeletionFailure(repoKey, e);
        }
    }

    private RepositoryRemovalFailedException createLockAcquisitionFailure(String repoKey, String message) {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder(repoKey);
        statusHolder.mainWarning("Failed to acquire repository deletion lock: " + message, SC_INTERNAL_SERVER_ERROR,
                log);
        return new RepositoryRemovalFailedException(statusHolder);
    }

    private RepositoryRemovalFailedException createUnexpectedDeletionFailure(String repoKey, Exception e) {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder(repoKey);
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        statusHolder.error(message, e, log);
        return new RepositoryRemovalFailedException(statusHolder);
    }

    private RepoRemovalStatusHolder removeRepository(RepoConfig repoConfig) {
        return removeRepository(repoConfig, true);
    }

    private RepoRemovalStatusHolder removeRepository(RepoConfig repoConfig, boolean triggerBeforeEvent) {
        String repoKey = repoConfig.getKey();
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder(repoKey);
        try {
            executeMainDeleteFlow(repoConfig, statusHolder, triggerBeforeEvent);
        } catch (RepositoryContentDeletionFailedException e) {
            // Content deletion failed before the repo config was removed, so deleteRepoConfig (and the
            // config-change interceptor's AFTER event) is skipped. Emit the AFTER repo-delete event ourselves
            // only for a genuine partial deletion (some artifacts removed -> statusCode 207, R5); a total
            // failure (nothing removed) is a no-op and emits no event.
            fireAfterPartialDeleteEvent(repoConfig, statusHolder);
            throw e;
        }
        String successMsg = getSuccessMsg(repoKey);
        getRepoConfigService().deleteRepoConfig(repoKey);
        statusHolder.mainStatus(successMsg, log);
        return statusHolder;
    }

    /**
     * On a partial content deletion (some artifacts removed, some failed) the repo config is not deleted, so the
     * config-change interceptor never emits the AFTER repo-delete event. Emit it here with {@code statusCode: 207}.
     * {@link RepoRemovalStatusHolder#getStatusCode()} returns {@code SC_MULTI_STATUS} only when at least one artifact
     * was deleted and at least one failed, so a total failure (nothing deleted) emits nothing. When repo CUD flags are
     * off, {@link RepoConfigCudEventEmitter} is a no-op.
     */
    private void fireAfterPartialDeleteEvent(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder) {
        if (statusHolder.getStatusCode() == SC_MULTI_STATUS) {
            repoConfigCudEventEmitter.emitAfter(repoConfigChangeFactory.delete(repoConfig), SC_MULTI_STATUS);
        }
    }

    private void executeMainDeleteFlow(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder,
            boolean triggerBeforeEvent) {
        if (triggerBeforeEvent) {
            triggerRepoConfigChangeInterceptors(repoConfig, statusHolder);
        }
        prepareRepoForRemoval(repoConfig);
        removeRepoFromFederatedRemoteMembers(repoConfig);
        deleteRepositoryContentService.deleteRepositoryContent(repoConfig.getKey(), statusHolder);
    }

    private RepoConfig getRepoConfig(String repoKey) {
        return getRepoConfigService().getRepoConfigByKeyNoValidationChecks(repoKey);
    }

    private void validateAllRepositories(Collection<RepoConfig> repoConfigs) {
        try {
            for (RepoConfig repoConfig : repoConfigs) {
                removeRepositoryValidator.validate(repoConfig);
            }
        } catch (RepositoryRemovalFailedException e) {
            throw new RepositoriesRemovalFailedException(e.getStatusHolder());
        }
    }

    private static void validateRepoKeys(Collection<String> repoKeys) {
        if (CollectionUtils.isNullOrEmpty(repoKeys)) {
            RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
            statusHolder.mainWarning("No repository keys were provided for deletion", SC_BAD_REQUEST, log);
            throw new RepositoriesRemovalFailedException(statusHolder);
        }
    }

    @VisibleForTesting
    void prepareRepoForRemoval(RepoConfig repoToRemove) {
        boolean toUpdate = false;
        if (repoToRemove instanceof RealRepoConfig realRepoConfig &&
                !realRepoConfig.getRepoTypeConfig().isBlackedOut()) {
            toUpdate = true;
            realRepoConfig.getRepoTypeConfig().setBlackedOut(true);
        }
        if (repoToRemove instanceof RemoteRepoConfig remoteRepoConfig &&
                !remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally()) {
            toUpdate = true;
            // Enabling -cache repo since it's possible it was enabled before and then disabled, and we don't want to
            // miss it in the delete flow
            remoteRepoConfig.getRepoTypeConfig().setStoreArtifactsLocally(true);
        }
        if (toUpdate) {
            // Skip BOTH BEFORE and AFTER interceptors: this is internal prep (blackout + cache toggle) before the
            // actual delete. Firing BEFORE_REPO_UPDATE would let a worker block the delete flow before
            // BEFORE_REPO_DELETE even fires; firing AFTER_REPO_UPDATE would emit a spurious update event for what
            // is semantically a delete.
            securityService.doAsSystem(() ->
                    getRepoConfigService().updateRepoConfigSilently(repoToRemove));
        }
    }

    private void triggerRepoConfigChangeInterceptors(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder) {
        try {
            RepoConfigChange deleteRepoChange = repoConfigChangeFactory.delete(repoConfig);
            RepoConfigChanges repoConfigChanges = new RepoConfigChanges(deleteRepoChange);
            repoConfigChangeInterceptors.forEach(interceptor -> interceptor.onBeforeSave(repoConfigChanges));
        } catch (ConfigurationException e) {
            statusHolder.mainWarning(getSingleRepoErrorMsg(repoConfig.getKey(), e), e.getStatusCode(), log);
            throw new RepositoryRemovalFailedException(statusHolder);
        } catch (WorkerStopException e) {
            // BEFORE_REPO_DELETE worker rejected the deletion. Wrap in the same RepositoryRemovalFailedException
            // shape every other failure mode uses, so the response body stays a RepoRemovalStatusHolder; status
            // stays 409 to match what WorkerStopExceptionMapper would have produced for the unwrapped exception.
            statusHolder.mainWarning(getSingleRepoErrorMsg(repoConfig.getKey(), e), SC_CONFLICT, log);
            throw new RepositoryRemovalFailedException(statusHolder);
        }
    }

    private void removeRepoFromFederatedRemoteMembers(RepoConfig repoConfig) {
        if (repoConfig.getBaseConfig().isFederated()) {
            FederatedRepoAddon federatedRepoAddon = getAddonsManager().addonByType(FederatedRepoAddon.class);
            try {
                federatedRepoAddon.removeRepoFromFederationMembers(repoConfig);
            } catch (FederatedRemoteMembersUpdateException e) {
                RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
                statusHolder.mainWarning(e.getMessage() + ", URLS: " + e.getUrls(), SC_FAILED_DEPENDENCY, log);
                throw new RepositoryRemovalFailedException(statusHolder);
            }
        }
    }

    private CentralConfigService getCentralConfigService() {
        return ContextHelper.get().beanForType(CentralConfigService.class);
    }

    private AddonsManager getAddonsManager() {
        return ContextHelper.get().beanForType(AddonsManager.class);
    }

    private InternalRepositoryConfigService getRepoConfigService() {
        return ContextHelper.get().beanForType(InternalRepositoryConfigService.class);
    }

    @Nonnull
    private static String getSingleRepoErrorMsg(String repoKey, Exception e) {
        return CANNOT_DELETE_REPO_MSG_PREFIX + "'%s', Reason: %s".formatted(repoKey, e.getMessage());
    }

    @Nonnull
    private String getSuccessMsg(String repoKey) {
        RepoConfig repoConfig = getRepoConfig(repoKey);
        if (repoConfig == null) {
            return getVirtualRepoSuccessMsg(repoKey);
        }
        return repoConfig.isReal() ?
                "Repository '%s' and all its content have been removed successfully.".formatted(repoKey) :
                getVirtualRepoSuccessMsg(repoKey);
    }

    @Nonnull
    private static String getVirtualRepoSuccessMsg(String repoKey) {
        return "Repository '%s' has been removed successfully.".formatted(repoKey);
    }
}