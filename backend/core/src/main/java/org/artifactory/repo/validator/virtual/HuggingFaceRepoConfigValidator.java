package org.artifactory.repo.validator.virtual;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.util.DoesNotExistException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.Boolean.parseBoolean;
import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;

/**
 * class for validating the virtual repository configuration for the HuggingFace package type.
 * Ensures that all repositories aggregated under the virtual repository consistently use the new ML layout only.
 *
 * @author Einas Ashkar
 */

@Slf4j
@Component
public class HuggingFaceRepoConfigValidator implements VirtualRepoPackageSpecificValidator {

    private final RepositoryService repositoryService;

    @Autowired
    public HuggingFaceRepoConfigValidator(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Validates that all repositories referenced by the given HuggingFace virtual repository are consistently using
     * the new ML layout.
     *
     * @param virtualRepoConfig       The virtual HuggingFace repository configuration to validate.
     * @throws InvalidOperationException if any referenced repo does not match the expected ML layout.
     * @throws DoesNotExistException     if any referenced repo cannot be found.
     */
    @Override
    public void validate(VirtualRepoConfig virtualRepoConfig) {
        validate(virtualRepoConfig, this::getRepoConfig);
    }


    @Override
    public RepoType getRepoType() {
        return RepoType.HuggingFaceML;
    }

    @NonNull
    private RepoConfig getRepoConfig(String repoKey) {
        return Optional.ofNullable(repositoryService.getRepoConfigByKey(repoKey))
                .orElseThrow(() -> new DoesNotExistException("Selected Repo key: '" + repoKey + "' not found"));
    }


    private void validate(VirtualRepoConfig virtualRepoConfig, Function<String, RepoConfig> repoConfigResolver) {
        if (virtualRepoConfig == null) {
            log.warn("Validation skipped because, VirtualRepoConfig is null repoKey: {}", virtualRepoConfig);
            return;
        }

        if (repositoryService == null) {
            log.warn("Validation skipped because, RepositoryConfigService is null for repoKey: {}",
                    virtualRepoConfig.getKey());
            return;
        }

        if (virtualRepoConfig.getPackageType() != RepoType.HuggingFaceML) {
            log.debug("Validation skipped because, VirtualRepoConfig is not of HuggingFaceML type for repoKey: {}",
                    virtualRepoConfig.getKey());
            return;
        }

        List<RepoConfig> conflictingRepos = virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().stream()
                .map(repoConfigResolver)
                .filter(repo -> !getHuggingFaceLayout(repo))
                .toList();

        if (!conflictingRepos.isEmpty()) {
            throw new InvalidOperationException(String.format("Virtual repository '%s' of type '%s' " +
                            "contains repositories that do not use the new ML layout. " +
                            "All aggregated repositories must use the new ML layout.",
                    virtualRepoConfig.getKey(), virtualRepoConfig.getPackageType()));
        }
    }

    private static boolean getHuggingFaceLayout(RepoConfig repoConfig) {
        return parseBoolean(repoConfig.getPackageTypeConfig().get(HUGGINGFACE_ML_REPO_LAYOUT.getKey()));
    }
}
