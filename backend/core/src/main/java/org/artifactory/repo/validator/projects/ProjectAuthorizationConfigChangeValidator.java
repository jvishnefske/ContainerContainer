package org.artifactory.repo.validator.projects;

import jakarta.annotation.Nonnull;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

@Component
public class ProjectAuthorizationConfigChangeValidator implements RepoConfigChangeValidator {

    private static final Set<RepoDetailsType> ALLOWED_REPO_TYPES = Set.of(LOCAL, REMOTE, VIRTUAL, RELEASE_BUNDLE);
    private final SecurityService securityService;

    @Autowired
    public ProjectAuthorizationConfigChangeValidator(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public void validate(RepoConfigChanges repoConfigChanges) {
        if (securityService.isAdmin()) {
            return;
        }
        validateCreateChanges(repoConfigChanges.filterBy(REPO_CREATE));
        validateUpdateChanges(repoConfigChanges.filterBy(REPO_UPDATE));
        validateDeleteChanges(repoConfigChanges.filterBy(REPO_DELETE));
    }

    private void validateCreateChanges(List<RepoConfigChange> createChanges) {
        if (!createChanges.isEmpty()) {
            Set<RepoConfig> repoConfigs = extractRepoConfigs(createChanges, RepoConfigChange::getNewRepoConfig);
            validateAuthorization(repoConfigs, RepoActionType.CREATE);
            validateAllowedRepos(repoConfigs, "create");
        }
    }

    private void validateUpdateChanges(List<RepoConfigChange> updateChanges) {
        if (!updateChanges.isEmpty()) {
            Set<RepoConfig> repoConfigs = extractRepoConfigs(updateChanges, RepoConfigChange::getNewRepoConfig);
            validateAllowedRepos(repoConfigs, "update");
            validateUpdateAuthorization(updateChanges);
        }
    }

    private void validateUpdateAuthorization(List<RepoConfigChange> updateChanges) {
        List<String> failedRepoKeys = updateChanges.stream()
                .filter(change -> !isUpdateAuthorized(change))
                .map(RepoConfigChange::getRepoKey)
                .collect(Collectors.toCollection(ArrayList::new));
        if (!failedRepoKeys.isEmpty()) {
            throwInsufficientPermission(failedRepoKeys, RepoActionType.UPDATE);
        }
    }

    private boolean isUpdateAuthorized(RepoConfigChange change) {
        RepoConfig newRepoConfig = change.getNewRepoConfig();
        RepoConfig oldRepoConfig = change.getOldRepoConfig();
        if (newRepoConfig == null || oldRepoConfig == null) {
            return false;
        }
        if (isPlainUpdateWithoutXrayIndexToggle(oldRepoConfig, newRepoConfig)) {
            return hasUpdatePermission(newRepoConfig);
        }
        return isXrayIndexToggleAuthorized(oldRepoConfig, newRepoConfig);
    }


    private static boolean isPlainUpdateWithoutXrayIndexToggle(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        return oldRepoConfig.getSecurityConfig() == null
                || newRepoConfig.getSecurityConfig() == null
                || !xrayIndexChanged(oldRepoConfig, newRepoConfig);
    }

    private boolean isXrayIndexToggleAuthorized(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        if (!canToggleXrayIndex(newRepoConfig)) {
            return false;
        }
        return !nonXrayFieldsChanged(oldRepoConfig, newRepoConfig) || hasUpdatePermission(newRepoConfig);
    }

    private boolean hasUpdatePermission(RepoConfig newRepoConfig) {
        return getAuthorizationPredicate(RepoActionType.UPDATE).test(newRepoConfig);
    }

    private boolean canToggleXrayIndex(RepoConfig newRepoConfig) {
        return securityService.isProjectAdmin(RepoPathFactory.create(newRepoConfig.getKey()))
                && securityService.canXrayIndexProjectRepoResource(newRepoConfig.getKey());
    }

    private static boolean xrayIndexChanged(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        return oldRepoConfig.getSecurityConfig().isXrayIndex() != newRepoConfig.getSecurityConfig().isXrayIndex();
    }

    private static boolean nonXrayFieldsChanged(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        return !Objects.equals(oldRepoConfig.getBaseConfig(), newRepoConfig.getBaseConfig())
                || !Objects.equals(oldRepoConfig.getRepoTypeConfig(), newRepoConfig.getRepoTypeConfig())
                || !Objects.equals(oldRepoConfig.getPackageTypeConfig(), newRepoConfig.getPackageTypeConfig())
                ||
                securityChangedIgnoringXrayIndex(oldRepoConfig.getSecurityConfig(), newRepoConfig.getSecurityConfig());
    }

    private static boolean securityChangedIgnoringXrayIndex(SecurityConfig oldSec, SecurityConfig newSec) {
        return !Objects.equals(oldSec.getPrimaryKeyPairRef(), newSec.getPrimaryKeyPairRef())
                || !Objects.equals(oldSec.getSecondaryKeyPairRef(), newSec.getSecondaryKeyPairRef())
                || oldSec.isHideUnauthorizedResources() != newSec.isHideUnauthorizedResources()
                || !Objects.equals(oldSec.getKeyPair(), newSec.getKeyPair())
                || oldSec.getSignedUrlTtl() != newSec.getSignedUrlTtl()
                || oldSec.getXrayDataTtl() != newSec.getXrayDataTtl()
                || !Objects.equals(oldSec.getXrayNotAvailableMode(), newSec.getXrayNotAvailableMode());
    }

    private void validateDeleteChanges(List<RepoConfigChange> deleteChanges) {
        if (!deleteChanges.isEmpty()) {
            Set<RepoConfig> repoConfigs = extractRepoConfigs(deleteChanges, RepoConfigChange::getOldRepoConfig);
            validateAuthorization(repoConfigs, RepoActionType.DELETE);
            validateAllowedRepos(repoConfigs, "delete");
        }
    }

    private void validateAuthorization(Set<RepoConfig> repoConfigs, RepoActionType repoAction) {
        ValidationFailure validationFailure = validateRepos(repoConfigs, getAuthorizationPredicate(repoAction));
        if (validationFailure.failed) {
            throwInsufficientPermission(validationFailure.repoKeys, repoAction);
        }
    }

    private void throwInsufficientPermission(List<String> failedRepoKeys, RepoActionType repoAction) {
        String errMsgTemplate = "User: ('%s') has insufficient permission to %s repositories: ".formatted(
                securityService.currentUsername(), repoAction.name().toLowerCase());
        throw new ConfigurationException(buildErrorMsg(failedRepoKeys, errMsgTemplate).toString(), SC_FORBIDDEN);
    }

    private void validateAllowedRepos(Set<RepoConfig> repoConfigs, String action) {
        ValidationFailure validationFailure = validateRepos(repoConfigs, getAllowedReposPredicate());
        if (validationFailure.failed) {
            String errMsgTemplate = "Project admin: ('%s') is not allowed to %s repositories: "
                    .formatted(securityService.currentUsername(), action);
            StringBuilder stringBuilder = buildErrorMsg(validationFailure.repoKeys, errMsgTemplate);
            throw new ConfigurationException(stringBuilder.toString(), SC_FORBIDDEN);
        }
    }

    @Nonnull
    private Predicate<RepoConfig> getAuthorizationPredicate(@Nonnull RepoActionType repoAction) {
        return repoConfig -> securityService.isProjectAdmin(RepoPathFactory.create(repoConfig.getKey())) &&
                securityService.canDoToRepoProjectResource(repoAction, repoConfig.getKey(), repoConfig.getRepoType());
    }

    @Nonnull
    private Predicate<RepoConfig> getAllowedReposPredicate() {
        return repoConfig -> ALLOWED_REPO_TYPES.contains(repoConfig.getRepoType());
    }

    private ValidationFailure validateRepos(Set<RepoConfig> repoConfigs, Predicate<RepoConfig> validation) {
        List<String> repoKeys = new ArrayList<>();
        boolean failed = false;
        for (RepoConfig repoConfig : repoConfigs) {
            if (!validation.test(repoConfig)) {
                failed = true;
                repoKeys.add(repoConfig.getKey());
            }
        }
        return new ValidationFailure(failed, repoKeys);
    }

    private static StringBuilder buildErrorMsg(List<String> repoKeys, String errMsg) {
        StringBuilder stringBuilder = new StringBuilder(errMsg);
        String firstRepoKey = repoKeys.removeFirst();
        stringBuilder.append(firstRepoKey);
        for (String repoKey : repoKeys) {
            stringBuilder.append(",").append(repoKey);
        }
        return stringBuilder;
    }

    private Set<RepoConfig> extractRepoConfigs(Collection<RepoConfigChange> repoConfigChanges,
            Function<RepoConfigChange, RepoConfig> extractionFunction) {
        return repoConfigChanges.stream()
                .map(extractionFunction)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record ValidationFailure(boolean failed, List<String> repoKeys) {
    }
}
