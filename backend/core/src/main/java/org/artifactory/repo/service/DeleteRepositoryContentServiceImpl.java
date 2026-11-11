package org.artifactory.repo.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.util.RetryHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
class DeleteRepositoryContentServiceImpl implements DeleteRepositoryContentService {

    private final StorageInterceptors storageInterceptors;

    DeleteRepositoryContentServiceImpl() {
        storageInterceptors = ContextHelper.get().beanForType(StorageInterceptors.class);
    }

    @Override
    public void deleteRepositoryContent(String repoKey, BasicStatusHolder statusHolder) {
        deleteRepositoryContentInternal(repoKey, statusHolder);
        triggerAfterRepoContentDeleteInterceptors(repoKey, statusHolder);
    }

    private void deleteRepositoryContentInternal(String repoKey, BasicStatusHolder statusHolder) {
        InternalRepositoryService repoService = getRepoService();
        try {
            repoService.deleteRepositoryContent(repoKey, statusHolder, true);
        } catch (CancelException | ForbiddenException e) {
            String errMsg = getErrMsgWithRepoKey(e, repoKey);
            statusHolder.warn(errMsg, e.getErrorCode(), e, log);
        }
        if (statusHolder.hasErrorsOrWarnings()) {
            throw new RepositoryContentDeletionFailedException(statusHolder);
        }
        if (repoService.containsRealContent(repoKey)) {
            deleteOrphanedNodes(repoKey, repoService);
        }
    }

    /**
     * Cleans up orphaned nodes (files and folders) that remain in the repository after the deletion process
     * (e.g. added by concurrent uploads before the blacked-out state propagated).
     * Retries with a configurable interval until no content remains beyond the root folder, or retries are exhausted.
     */
    private void deleteOrphanedNodes(String repoKey, InternalRepositoryService repoService) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        String description = "Repository '" + repoKey + "' orphaned nodes cleanup";

        boolean success = RetryHelper.builder()
                .maxRetries(ConstantValues.repoDeleteOrphanRetryCount.getInt())
                .retryIntervalMillis(
                        ConstantValues.repoDeleteOrphanRetryIntervalMillis.getLong())
                .operation(() -> repoService.deleteOrphanNodes(repoKey, statusHolder, true))
                .stopCondition(() -> !repoService.containsRealContent(repoKey))
                .logger(log)
                .description(description)
                .build()
                .execute();

        if (!success) {
            throw new RepositoryContentDeletionFailedException(statusHolder);
        }
    }

    private void triggerAfterRepoContentDeleteInterceptors(String repoKey, BasicStatusHolder statusHolder) {
        try {
            storageInterceptors.afterRepositoryContentDelete(repoKey, statusHolder);
        } catch (Exception e) {
            throw new RepositoryContentDeletionFailedException(statusHolder);
        }
    }

    @Nonnull
    private static InternalRepositoryService getRepoService() {
        return ContextHelper.get().beanForType(InternalRepositoryService.class);
    }

    @Nonnull
    private static String getErrMsgWithRepoKey(Exception e, String repoKey) {
        return e.getMessage() != null ? e.getMessage() + ". Repository: '%s'".formatted(repoKey) :
                "Unknown error occurred when trying to delete repository: '%s' content".formatted(repoKey);
    }
}