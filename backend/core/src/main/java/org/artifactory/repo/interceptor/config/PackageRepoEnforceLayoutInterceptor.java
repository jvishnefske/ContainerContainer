package org.artifactory.repo.interceptor.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.repo.HelmGeneralConfig;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_METADATA_NAME_VERSION;
import static org.artifactory.util.HelmPackageTypeConfigMapper.FORCE_NON_DUPLICATE_CHART;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

/**
 * This interceptor enforces 3 aspects regarding package enforce layout feature
 * 1. When repo is created - if the enforce layout fields are not set - we set them by the general config
 * 2. When repo is created - if the enforce layout fields does set - we are not considering the general config
 * 3. When repo is updated - we make sure that the enforce layout fields are immutable.
 * This interceptor should be skipped in case of repository bootstrap or import process
 */
@Slf4j
@Component
@Order(HIGHEST_PRECEDENCE) // Make sure this interceptor runs first, as it's can modify the repo config
public class PackageRepoEnforceLayoutInterceptor implements RepoConfigChangeInterceptor {
    private final CentralConfigService centralConfigService;
    private final Map<RepoType, Supplier<PackageEnforceLayoutFieldsHandler>> packageTypeToHandlerMap;

    @Autowired
    public PackageRepoEnforceLayoutInterceptor(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
        packageTypeToHandlerMap = new EnumMap<>(RepoType.class);
        packageTypeToHandlerMap.put(RepoType.Helm, this::createHelmpackageEnforceLayoutFieldsHandler);
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        if (ContextHelper.get() == null || centralConfigService.getDescriptor() == null ||
                ContextHelper.get().isReposImportInProgress()) {
            log.trace("Skipping enforce layout fields validation during startup or import is in progress");
            return;
        }
        List<RepoConfigChange> localRepos = getRepoList(mutableRepoConfigChanges);
        validateLocalReposUpdate(localRepos);
        setEnforceLayoutFieldsForNewRepos(localRepos);
    }

    private void validateLocalReposUpdate(List<RepoConfigChange> localRepos) {
        localRepos.stream()
                .filter(repoConfigChange -> REPO_UPDATE.equals(repoConfigChange.getConfigAction()))
                .forEach(repoConfigChange -> {
                    if (repoConfigChange.getOldRepoConfig() != null && repoConfigChange.getNewRepoConfig() != null) {
                        // Skip immutability validation on LOCAL -> FEDERATED migration
                        if (isLocalToFederatedHelmMigration(repoConfigChange)) {
                            return;
                        }
                        Supplier<PackageEnforceLayoutFieldsHandler> handlerSupplier =
                                packageTypeToHandlerMap.get(repoConfigChange.getNewRepoConfig().getPackageType());
                        if (handlerSupplier != null) {
                            assertEnforceLayoutFieldsNotChange(repoConfigChange, handlerSupplier.get());
                        }
                    }
                });
    }

    private void assertEnforceLayoutFieldsNotChange(RepoConfigChange repoConfigChange,
            PackageEnforceLayoutFieldsHandler packageEnforceLayoutFieldsHandler) {
        Set<String> enforceLayoutFields = packageEnforceLayoutFieldsHandler.getFieldToExpectedValue().keySet();
        enforceLayoutFields.forEach(enforceLayoutField -> {
            String valueFromOldConfig =
                    repoConfigChange.getOldRepoConfig().getPackageTypeConfig().get(enforceLayoutField);
            String valueFromNewConfig =
                    repoConfigChange.getNewRepoConfig().getPackageTypeConfig().get(enforceLayoutField);
            if (!isOldRepoCase(valueFromOldConfig, valueFromNewConfig) &&
                    !Objects.equals(valueFromOldConfig, valueFromNewConfig)) {
                throw new IllegalArgumentException(
                        "Cannot change enforce layout field '" + enforceLayoutField + "' for repository: " +
                                repoConfigChange.getRepoKey());
            }
        });
    }


    /**
     * In case the repository existed before adding the enforce layout fields, we need to verify that the
     * valueFromOldConfig does not exist and the valueFromNewConfig is false
     *
     * @param valueFromOldConfig the value from the old repository configuration
     * @param valueFromNewConfig the value from the new repository configuration
     * @return true if the old repository configuration case applies, false otherwise
     */
    private boolean isOldRepoCase(String valueFromOldConfig, String valueFromNewConfig) {
        return StringUtils.isBlank(valueFromOldConfig) && "false".equalsIgnoreCase(valueFromNewConfig);
    }

    private void setEnforceLayoutFieldsForNewRepos(List<RepoConfigChange> localRepos) {
        localRepos.stream()
                .filter(repoConfigChange ->
                        REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                                isLocalToFederatedHelmMigration(repoConfigChange))
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(Objects::nonNull)
                .forEach(newLocalRepo -> {
                    Supplier<PackageEnforceLayoutFieldsHandler> handlerSupplier =
                            packageTypeToHandlerMap.get(newLocalRepo.getPackageType());
                    if (handlerSupplier != null) {
                        setEnforceLayoutFieldsIfNeeded(newLocalRepo, handlerSupplier.get());
                    }
                });
    }

    private void setEnforceLayoutFieldsIfNeeded(RepoConfig localRepo,
            PackageEnforceLayoutFieldsHandler handler) {
        Map<String, String> packageTypeConfig = localRepo.getPackageTypeConfig();
        handler.getFieldToExpectedValue()
                .forEach((key, value) -> {
                    if (value && !Boolean.parseBoolean(packageTypeConfig.get(key))) {
                        log.debug("Setting enforce layout field '{}' to true for repository: {}", key,
                                localRepo.getKey());
                        packageTypeConfig.put(key, "true");
                    }
                });
    }

    private List<RepoConfigChange> getRepoList(
            Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        List<RepoConfigChange> repoConfigChanges =
                new LinkedList<>(mutableRepoConfigChanges.getOrDefault(RepoDetailsType.LOCAL, List.of()));
        repoConfigChanges.addAll(mutableRepoConfigChanges.getOrDefault(RepoDetailsType.FEDERATED, List.of()));
        return repoConfigChanges;    }

    private PackageEnforceLayoutFieldsHandler createHelmpackageEnforceLayoutFieldsHandler() {
        if (centralConfigService.getDescriptor() == null ||
                centralConfigService.getDescriptor().getRepositoriesGeneralConfig() == null
                || centralConfigService.getDescriptor().getRepositoriesGeneralConfig().getHelm() == null) {
            return new PackageEnforceLayoutFieldsHandler(Map.of());
        }
        HelmGeneralConfig generalConfig =
                centralConfigService.getDescriptor().getRepositoriesGeneralConfig().getHelm();
        return new PackageEnforceLayoutFieldsHandler(Map.of(
                FORCE_METADATA_NAME_VERSION, generalConfig.isForceMetadataNameVersion(),
                FORCE_NON_DUPLICATE_CHART, generalConfig.isForceNonDuplicateChart()));
    }

    private record PackageEnforceLayoutFieldsHandler(@Getter Map<String, Boolean> fieldToExpectedValue) {
    }

    private boolean isLocalToFederatedHelmMigration(RepoConfigChange change) {
        return REPO_UPDATE.equals(change.getConfigAction())
                && change.getOldRepoConfig() != null && change.getNewRepoConfig() != null
                && change.getOldRepoConfig().getRepoType() == RepoDetailsType.LOCAL
                && change.getNewRepoConfig().getRepoType() == RepoDetailsType.FEDERATED
                && change.getNewRepoConfig().getPackageType() == RepoType.Helm;
    }
}
