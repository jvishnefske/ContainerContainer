package org.artifactory.repo.service;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.RepoRemovalStatus;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ReloadableRepositoryConfigService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.spring.Reloadable;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.api.common.RepoRemovalStatus.CANNOT_DELETE_REPO_MSG_PREFIX;
import static org.artifactory.api.common.RepoRemovalStatus.INVALID_REPO_KEY;
import static org.artifactory.api.common.RepoRemovalStatus.REPO_IMPORT_IN_PROGRESS;
import static org.artifactory.repo.service.RepositoryConfigServiceImpl.isBlankTrashOrSupportBundle;

@Slf4j
@NoArgsConstructor
@Reloadable(beanClass = RemoveRepositoryValidator.class, initAfter = {InternalRepositoryService.class,
        ReloadableRepositoryConfigService.class})
@Component
class RemoveRepositoryValidatorImpl implements RemoveRepositoryValidator {

    private StorageInterceptors storageInterceptors;
    private List<RepoConfigChangeValidator> repoConfigChangeValidators;
    private List<RemoveRepositoryValidation> validations;

    @VisibleForTesting
    RemoveRepositoryValidatorImpl(StorageInterceptors storageInterceptors,
            List<RepoConfigChangeValidator> repoConfigChangeValidators) {
        this.storageInterceptors = storageInterceptors;
        this.repoConfigChangeValidators = repoConfigChangeValidators;
        this.validations = populateRemoveRepositoryValidations();
    }

    @Override
    public void init() {
        this.storageInterceptors = ContextHelper.get().beanForType(StorageInterceptors.class);
        this.repoConfigChangeValidators =
                new ArrayList<>(ContextHelper.get().beansForType(RepoConfigChangeValidator.class).values());
        this.validations = populateRemoveRepositoryValidations();
    }

    @Nonnull
    private List<RemoveRepositoryValidation> populateRemoveRepositoryValidations() {
        return List.of(
                RemoveRepositoryValidatorImpl::validateBlankTrashOrSupportBundle,
                RemoveRepositoryValidatorImpl::validateNoRepoImportInProgress,
                this::assertDeleteRepoAllowed,
                this::validateRepoConfigDeletion
        );
    }

    @Override
    public void validate(@Nonnull RepoConfig repoConfig) {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder(repoConfig.getKey());
        for (RemoveRepositoryValidation validation : validations) {
            validation.validate(repoConfig, statusHolder);
            if (statusHolder.hasErrorsOrWarnings()) {
                throw new RepositoryRemovalFailedException(statusHolder);
            }
        }
    }

    private static void validateNoRepoImportInProgress(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder) {
        if (ContextHelper.get().isReposImportInProgress()) {
            addWarning(statusHolder, REPO_IMPORT_IN_PROGRESS, log, repoConfig.getKey());
        }
    }

    private static void validateBlankTrashOrSupportBundle(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder) {
        if (isBlankTrashOrSupportBundle(repoConfig.getKey())) {
            addWarning(statusHolder, INVALID_REPO_KEY, log);
        }
    }

    private void assertDeleteRepoAllowed(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder) {
        storageInterceptors.assertDeleteRepoAllowed(repoConfig.getKey(), statusHolder);
        if (statusHolder.hasErrorsOrWarnings()) {
            statusHolder.mutateToRepoLevelStatus();
            throw new RepositoryRemovalFailedException(statusHolder);
        }
    }

    private void validateRepoConfigDeletion(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder) {
        try {
            RepoConfigChange repoConfigDeleteChange = RepoConfigChange.deleteOf(repoConfig);
            repoConfigChangeValidators.forEach(
                    validator -> validator.validate(new RepoConfigChanges(repoConfigDeleteChange)));
        } catch (ConfigurationException e) {
            statusHolder.mainWarning(
                    CANNOT_DELETE_REPO_MSG_PREFIX + "'%s', Reason: %s".formatted(repoConfig.getKey(), e.getMessage()),
                    e.getStatusCode(), log);
        }
    }

    private static void addWarning(RepoRemovalStatusHolder statusHolder, RepoRemovalStatus failureStatus,
            Logger log, Object... params) {
        statusHolder.mainWarning(failureStatus.getReason(params), failureStatus.getStatusCode(), log);
    }

    @FunctionalInterface
    private interface RemoveRepositoryValidation {

        void validate(RepoConfig repoConfig, RepoRemovalStatusHolder statusHolder);
    }
}