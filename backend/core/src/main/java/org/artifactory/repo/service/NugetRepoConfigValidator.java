package org.artifactory.repo.service;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.InvalidOperationException;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.DoesNotExistException;
import org.artifactory.util.RemoteRepoCachePeriodValidator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Boolean.parseBoolean;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;

/**
 * This is an utility class used to validate the virtualRepoConfig for nuget package type.
 *
 * @author prasannasudharsanr
 */
@Slf4j
@UtilityClass
public class NugetRepoConfigValidator {

    /**
     * This method is to validate,  when the enableNormalisedVersion flag is set true, all the selected repositories
     * package type config must have the enableNormalizedVersion flag set to true, i.e all the selected repositories
     * should be Normalized.
     * throws InvalidOperationException when virtual repo is enabled for normalized and any one of the repo is non
     * normalized
     * throws DoesNotExistException when any one of the repo is not found while getting validated for normalization
     *
     * @param virtualRepoConfig       -  config on which the validations are applied.
     * @param repositoryConfigService used to get the repository configurations of selected repos
     */
    public static void validateNugetPackageTypeConfig(VirtualRepoConfig virtualRepoConfig,
            RepositoryConfigService repositoryConfigService) {
        validate(virtualRepoConfig, repositoryConfigService,
                repoKey -> getRepoConfig(repoKey, repositoryConfigService));
    }

    /**
     * Validates that all repositories referenced by the given NuGet virtual repository are consistently normalized
     * according to the virtual repo's configuration. Checks both incoming configuration changes and persisted config.
     *
     * @param virtualRepoConfig         The virtual NuGet repository configuration to validate.
     * @param repositoryConfigService   Service to retrieve persisted repository configurations.
     * @param mutableRepoConfigChanges  Map of incoming repository config changes (created/updated in this request).
     * @throws InvalidOperationException if any referenced repo does not match the normalization state.
     * @throws DoesNotExistException     if any referenced repo cannot be found.
     */
    public static void validateNugetPackageTypeConfig(VirtualRepoConfig virtualRepoConfig,
            RepositoryConfigService repositoryConfigService,
            Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        validate(virtualRepoConfig, repositoryConfigService,
                repoKey -> getRepoConfigFromChanges(repoKey, repositoryConfigService, mutableRepoConfigChanges));
    }

    /**
     * Core validation logic for NuGet virtual repositories. Ensures all referenced repositories match the
     * normalization state of the virtual repo.
     *
     * @param virtualRepoConfig         The virtual NuGet repository configuration to validate.
     * @param repositoryConfigService   Service to retrieve persisted repository configurations.
     * @param repoConfigResolver        Function to resolve a repo key to its configuration.
     * @throws InvalidOperationException if any referenced repo does not match the normalization state.
     */
    private static void validate(VirtualRepoConfig virtualRepoConfig,
            RepositoryConfigService repositoryConfigService,
            Function<String, RepoConfig> repoConfigResolver) {
        if (virtualRepoConfig == null) {
            log.warn("Validation skipped because, Virtual repo config is null");
            return;
        }

        if (repositoryConfigService == null) {
            log.warn("Validation skipped because, RepositoryConfigService is null");
            return;
        }

        if (virtualRepoConfig.getPackageType() != RepoType.NuGet) {
            return;
        }

        String isVirtualRepoNormalized = getNormalizedFlag(virtualRepoConfig, repositoryConfigService);

        List<RepoConfig> conflictingRepos = virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs().stream()
                .map(repoConfigResolver)
                .filter(repo -> parseBoolean(isVirtualRepoNormalized) != isRepoNormalized(repo))
                .toList();

        if (!conflictingRepos.isEmpty()) {
            String expected = parseBoolean(isVirtualRepoNormalized) ? "normalized" : "non-normalized";
            String actual = parseBoolean(isVirtualRepoNormalized) ? "non normalized" : "normalized";

            logWarnMessageForVirtualRepo(virtualRepoConfig, conflictingRepos, expected, actual);
            throw new InvalidOperationException(
                    "Nuget Repository " + virtualRepoConfig.getKey() + " must have only " + expected + " repositories");
        }
    }

    private static boolean isRepoNormalized(RepoConfig repoConfig) {
        return parseBoolean(repoConfig.getPackageTypeConfig().get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()));
    }

    private static String getNormalizedFlag(VirtualRepoConfig virtualRepoConfig,
            RepositoryConfigService configService) {
        return Optional.ofNullable(configService.getVirtualRepoConfigByKey(virtualRepoConfig.getKey()))
                .orElse(virtualRepoConfig)
                .getPackageTypeConfig()
                .get(ENABLE_NUGET_NORMALIZED_VERSION.getKey());
    }

    private static void logWarnMessageForVirtualRepo(VirtualRepoConfig virtualRepoConfig,
            List<RepoConfig> repos, String repoType, String oppositeRepoType) {
        String repoKeys = repos.stream().map(RepoConfig::getKey).collect(Collectors.joining(","));
        log.warn("Failed to create {} nuget repo of key {}, with {} repositories of {}", repoType,
                virtualRepoConfig.getKey(), oppositeRepoType, repoKeys);

    }

    /**
     * Attempts to resolve a repository configuration by key, first from the incoming changes map,
     * then from the persisted configuration service if not found.
     *
     * @param repoKey      The key of the repository to resolve.
     * @param configService Service to retrieve persisted repository configurations.
     * @param changesMap   Map of incoming repository config changes.
     * @return The resolved repository configuration.
     * @throws DoesNotExistException if the repository cannot be found in either source.
     */
    @NonNull
    static RepoConfig getRepoConfigFromChanges(String repoKey,
            RepositoryConfigService configService,
            Map<RepoDetailsType, List<RepoConfigChange>> changesMap) {
        if (changesMap == null || changesMap.isEmpty()) {
            return getRepoConfig(repoKey, configService);
        }
        return changesMap.values().stream()
                .flatMap(List::stream)
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(newConfig -> newConfig != null && repoKey.equals(newConfig.getKey()))
                .findFirst()
                .orElseGet(() -> getRepoConfig(repoKey, configService));
    }

    @NonNull
    private static RepoConfig getRepoConfig(String repoKey, RepositoryConfigService repositoryConfigService) {
        return Optional.ofNullable(repositoryConfigService.getRepoConfigByKey(repoKey))
                .orElseThrow(() -> new DoesNotExistException("Selected Repo key" + repoKey + " not found"));

    }

    /**
     * Validates that the metadata retrieval cache period of a NuGet remote repository meets the configured
     * minimum. Delegates to {@link RemoteRepoCachePeriodValidator#assertValidValue}, which enforces the
     * general 30-second minimum (subject to feature flag and exclusions) and any matching custom upstream
     * rules (unconditional). No-op when {@code remoteRepoConfig} or its
     * {@link RemoteRepositoryRepoTypeConfig} is null.
     *
     * @param remoteRepoConfig the NuGet remote repo configuration to validate.
     * @throws IllegalArgumentException if the cache period is below the required minimum.
     */
    public static void validateNugetRemoteCachePeriod(RemoteRepoConfig remoteRepoConfig) {
        if (remoteRepoConfig == null) {
            return;
        }
        RemoteRepositoryRepoTypeConfig repoTypeConfig = remoteRepoConfig.getRepoTypeConfig();
        if (repoTypeConfig == null) {
            return;
        }
        RemoteRepoCachePeriodValidator.assertValidValue(
                remoteRepoConfig.getUrl(),
                repoTypeConfig.getRetrievalCachePeriodSecs(),
                remoteRepoConfig.isSmart(),
                remoteRepoConfig.getPackageType()
        );
    }

    public static boolean isNugetSmartRepoNormalized(RemoteRepoConfig repoConfig) {
        if (repoConfig == null) {
            return false;
        }
        if (RepoType.NuGet != repoConfig.getPackageType()) {
            return false;
        }
        if (repoConfig.getRepoTypeConfig() == null ||
                repoConfig.getRepoTypeConfig().getContentSynchronisation() == null ||
                !repoConfig.getRepoTypeConfig().getContentSynchronisation().isEnabled()) {
            return false;
        }

        return repoConfig.getPackageTypeConfig() != null && "true".equalsIgnoreCase(
                repoConfig.getPackageTypeConfig().get(ENABLE_NUGET_NORMALIZED_VERSION.getKey()));
    }
}