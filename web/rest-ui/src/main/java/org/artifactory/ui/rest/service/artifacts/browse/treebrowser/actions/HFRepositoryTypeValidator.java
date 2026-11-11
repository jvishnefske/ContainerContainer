package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.rest.common.service.RestResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for HuggingFace repository types
 *
 * @author Volodymyr Shulga
 */
@UtilityClass
public class HFRepositoryTypeValidator {

    @SuppressWarnings("rawtypes")
    public static ValidationResult validateRepository(@NonNull RepoConfig repoConfig,
            @NonNull Set<RepoDetailsType> supportedTypes,
            @NonNull String operation,
            @NonNull RepositoryService repositoryService,
            @NonNull RestResponse response) {

        String repoKey = repoConfig.getKey();
        RepoDetailsType repoType = repoConfig.getRepoType();

        if (!supportedTypes.contains(repoType)) {
            String message;
            if (repoType == RepoDetailsType.VIRTUAL) {
                message = String.format("Repository %s is virtual. Not supported.", repoKey);
            } else {
                message = String.format("Repository %s with type %s is not supported", repoKey, repoType);
            }
            response.error(message);
            return HFRepositoryTypeValidator.invalid(message);
        }

        if (repoType == RepoDetailsType.FEDERATED) {
            ValidationResult federatedValidation = validateFederatedRepository(repoKey, operation, repositoryService,
                    response);
            if (!federatedValidation.valid()) {
                return federatedValidation;
            }
        }

        return HFRepositoryTypeValidator.valid();
    }

    @SuppressWarnings("rawtypes")
    private static ValidationResult validateFederatedRepository(@NonNull String repoKey,
            @NonNull String operation, @NonNull RepositoryService repositoryService, @NonNull RestResponse response) {

        FederatedRepoConfig federatedRepoConfig =
                repositoryService.getFederatedRepoConfigByKey(repoKey);
        if (federatedRepoConfig == null) {
            String message = String.format("Failed to get federated descriptor for repository %s", repoKey);
            response.error(message);
            return HFRepositoryTypeValidator.invalid(message);
        }

        List<org.artifactory.repo.federated.FederatedMember> federatedMembers =
                federatedRepoConfig.getRepoTypeConfig().getFederatedMembers();
        if (federatedMembers == null || federatedMembers.isEmpty()) {
            return HFRepositoryTypeValidator.valid();
        }

        Set<String> activeMembers = new HashSet<>();
        for (FederatedMember federatedMember : federatedMembers) {
            if (federatedMember.isEnabled()) {
                activeMembers.add(federatedMember.getUrlOrTemplate());
            }
        }

        if (!activeMembers.isEmpty()) {
            String message = String.format(
                    "Federated repository %s cannot be used in %s while it has active member(s): %s",
                    repoKey, operation, String.join(", ", activeMembers));
            response.error(message);
            return HFRepositoryTypeValidator.invalid(message);
        }

        return HFRepositoryTypeValidator.valid();
    }

    public record ValidationResult(boolean valid, String message) {
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }
}