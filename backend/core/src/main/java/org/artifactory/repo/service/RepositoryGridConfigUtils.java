package org.artifactory.repo.service;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;

import java.util.List;

/**
 * Utility class for grid/federation related repository configuration validation and detection.
 * This class is purely stateless with no dependencies.
 */
@Slf4j
@UtilityClass
public class RepositoryGridConfigUtils {

    /**
     * Validates that no repository has gridTopologyId when grid is disabled.
     * This prevents silent data loss where federation config would be ignored.
     *
     * @throws IllegalArgumentException if any repo has gridTopologyId but grid is disabled
     */
    public void validateNoGridTopologyIdWhenGridDisabled(List<? extends RepoConfig> repoConfigs) {
        if (ConstantValues.gridEnabled.getBoolean()) {
            return; // Grid enabled, gridTopologyId is valid
        }

        repoConfigs.stream()
                .filter(config -> {
                    String gridTopologyId = config.getBaseConfig().getFederationConfig().getGridTopologyId();
                    return StringUtils.isNotBlank(gridTopologyId);
                })
                .findAny()
                .ifPresent(config -> {
                    throw new IllegalArgumentException(
                            "Cannot import repositories with gridTopologyId when grid is disabled.");
                });
    }

    /**
     * Detects local repos that have federation capability.
     * Federation capability is indicated by:
     * - gridTopologyId (if grid is enabled), OR
     * - federation field with members/proxy data (works even if grid is disabled)
     *
     * @param repoConfigsToImport The repo configs to check
     * @return List of repo configs that should be migrated to federated
     */
    public List<RepoConfig> detectLocalReposWithFederation(List<? extends RepoConfig> repoConfigsToImport) {
        return repoConfigsToImport.stream()
                .filter(LocalRepoConfig.class::isInstance)
                .filter(RepositoryGridConfigUtils::hasFederationCapability)
                .map(RepoConfig.class::cast)
                .toList();
    }

    /**
     * Checks if config has federation capability based on grid flag:
     * grid enabled → require gridTopologyId; grid disabled → require federation members.
     */
    public boolean hasFederationCapability(RepoConfig config) {
        if (ConstantValues.gridEnabled.getBoolean()) {
            String gridTopologyId = config.getBaseConfig().getFederationConfig().getGridTopologyId();
            return StringUtils.isNotBlank(gridTopologyId);
        }
        if (config instanceof LocalRepoConfig localRepoConfig) {
            LocalRepoConfig.FederationExportData federation = localRepoConfig.getFederation();
            return federation != null &&
                    federation.getMembers() != null &&
                    !federation.getMembers().isEmpty();
        }
        return false;
    }

    /**
     * Determines if a local repo should be migrated to federated based on its configuration.
     * 
     * @param gridTopologyId The gridTopologyId from the config (may be null/blank)
     * @param federationData The federation export data (may be null)
     * @param repoKey The repo key for logging
     * @return true if migration should proceed, false to skip
     */
    public static boolean shouldMigrateToFederated(String gridTopologyId, 
            LocalRepoConfig.FederationExportData federationData, String repoKey) {
        if (ConstantValues.gridEnabled.getBoolean()) {
            if (StringUtils.isBlank(gridTopologyId)) {
                log.debug("Skipping migration of repo {} - grid is enabled but gridTopologyId is missing", repoKey);
                return false;
            }
        } else {
            if (federationData == null || 
                    federationData.getMembers() == null || 
                    federationData.getMembers().isEmpty()) {
                log.debug("Skipping migration of repo {} - grid is disabled but federation members are missing",
                        repoKey);
                return false;
            }
        }
        return true;
    }
}
