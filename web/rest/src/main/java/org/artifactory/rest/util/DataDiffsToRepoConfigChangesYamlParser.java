package org.artifactory.rest.util;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.GridTopologyService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.GridTopology;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.migration.mappers.FederatedRepoConfigMapper;
import org.artifactory.repo.migration.mappers.LocalRepoConfigMapper;
import org.artifactory.repo.migration.mappers.RemoteRepoConfigMapper;
import org.artifactory.repo.migration.mappers.VirtualRepoConfigMapper;
import org.artifactory.repo.service.validation.RepositoryNameConfigurationValidator;
import org.artifactory.storage.StorageException;
import org.jfrog.common.config.diff.DataDiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

@Slf4j
@UtilityClass
public class DataDiffsToRepoConfigChangesYamlParser {

    private static final String YML_INHERITANCE_MARKER = "»";
    private static final String YML_REPRESENTATION_FOR_MISSING_DATA = "$$deleted";
    private static final String REMOTE_REPOSITORIES = "remoteRepositories";
    private static final String LOCAL_REPOSITORIES = "localRepositories";
    private static final String VIRTUAL_REPOSITORIES = "virtualRepositories";
    private static final String FEDERATED_REPOSITORIES = "federatedRepositories";
    private static final String RELEASE_BUNDLE_REPOSITORIES = "releaseBundleRepositories";

    /**
     * Parses {@link DataDiff} to {@link RepoConfigChanges}
     * In case of an attempt to delete a non-existing repository, silently ignores and continues.
     */
    public static RepoConfigChanges parse(Set<DataDiff<?>> repoDataDiffs) {
        return parseReposFromDataDiffs(repoDataDiffs);
    }

    private RepoConfigChanges parseReposFromDataDiffs(Set<DataDiff<?>> newDataDiffs) {
        Map<String, Object> migratedDiffs = new HashMap<>();
        newDataDiffs.forEach(e -> {
            checkNoEmptyRepoConfigIsPassed(e);
            migratedDiffs.put(e.getFieldName(), e.getNewValue());

        });
        List<String> repoConfigDataByKeys = new ArrayList<>(migratedDiffs.keySet());
        Collections.sort(repoConfigDataByKeys);
        String key = "";
        Map<String, RepoDetailsType> duplicateKey = new HashMap<>();
        Map<String, Map<String, Object>> main = new HashMap<>();
        Map<String, Object> second = new HashMap<>();
        Map<String, RepoConfig> repoConfigs = new HashMap<>();
        String type = "";
        for (String repoConfigDataByKey : repoConfigDataByKeys) {
            String[] split = repoConfigDataByKey.split(YML_INHERITANCE_MARKER);
            checkForDuplicate(split[1], duplicateKey, split[0]);
            if (key.isEmpty()) {
                type = split[0];
                key = split[1];
            } else if (!key.equals(split[1])) {
                main.put(key, second);
                addRepoConfigByType(repoConfigs, key, type, main.get(key));
                key = split[1];
                type = split[0];
                second = new HashMap<>();
            }
            if (split.length == 3) {
                second.put(split[2], migratedDiffs.get(repoConfigDataByKey));
            } else if (split.length == 4) {
                if (second.get(split[2]) != null) {
                    ((Map<String, Object>) second.get(split[2])).put(split[3], migratedDiffs.get(repoConfigDataByKey));
                } else {
                    if (split[3].equals(YML_REPRESENTATION_FOR_MISSING_DATA)) {
                        second.put(split[2], null);
                    } else {
                        Map<String, Object> inner = new HashMap<>();
                        inner.put(split[3], migratedDiffs.get(repoConfigDataByKey));
                        second.put(split[2], inner);
                    }
                }
            } else if (split.length == 5) {
                if (second.get(split[2]) != null) {
                    Map<String, Object> thirdLvlMap = (Map<String, Object>) second.get(split[2]);
                    if (thirdLvlMap.get(split[3]) != null) {
                        ((Map<String, Object>) thirdLvlMap.get(split[3])).put(split[4],
                                migratedDiffs.get(repoConfigDataByKey));
                    } else {
                        HashMap<String, Object> fourthLvlMap = new HashMap<>();
                        fourthLvlMap.put(split[4], migratedDiffs.get(repoConfigDataByKey));
                        thirdLvlMap.put(split[3], fourthLvlMap);
                    }
                } else {
                    if (split[4].equals(YML_REPRESENTATION_FOR_MISSING_DATA)) {
                        second.put(split[3], null);
                    } else {
                        Map<String, Object> thirdLvlMap = new HashMap<>();
                        HashMap<String, Object> fourthLvlMap = new HashMap<>();
                        fourthLvlMap.put(split[4], migratedDiffs.get(repoConfigDataByKey));
                        thirdLvlMap.put(split[3], fourthLvlMap);
                        second.put(split[2], thirdLvlMap);
                    }
                }
            }
        }
        main.put(key, second);
        addRepoConfigByType(repoConfigs, key, type, main.get(key));
        main.keySet().forEach(RepositoryNameConfigurationValidator::validateYamlRepoName);
        Set<RepoConfig> repositoryConfigsByKeys = getRepoConfigsByKeys(main.keySet());
        List<RepoConfig> repoConfigsToUpsert = new ArrayList<>();
        List<String> repoConfigsToDelete = new ArrayList<>();
        if (!repositoryConfigsByKeys.isEmpty()) {
            repositoryConfigsByKeys.forEach(
                    dbRepoConfig -> {
                        checkForDuplicate(dbRepoConfig.getKey(), duplicateKey, dbRepoConfig.getRepoType());
                        Map<String, Object> newData = main.remove(dbRepoConfig.getKey());
                        if (newData.entrySet().size() == 1 &&
                                YML_REPRESENTATION_FOR_MISSING_DATA.equals(
                                        newData.entrySet().stream().findFirst().get().getKey())) {
                            repoConfigsToDelete.add(dbRepoConfig.getKey());
                        } else {
                            createRepoByRepoType(dbRepoConfig, newData).ifPresent(repoConfigsToUpsert::add);
                        }
                    });
        }
        main.keySet().forEach(e -> {
            if (repoConfigs.get(e) != null) {
                if (main.get(e).size() == 1 &&
                        YML_REPRESENTATION_FOR_MISSING_DATA.equals(
                                main.get(e).entrySet().stream().findFirst().get().getKey())) {
                    //do nothing
                } else {
                    createRepoByRepoType(repoConfigs.get(e), main.get(e)).ifPresent(repoConfigsToUpsert::add);
                }
            }
        });
        return new RepoConfigChanges(repoConfigsToUpsert, repoConfigsToDelete);
    }

    private static Set<RepoConfig> getRepoConfigsByKeys(Set<String> repoKeys) {
        return getRepositoryConfigService().getRepoConfigsByKeys(repoKeys);
    }

    @Nonnull
    private static RepositoryConfigService getRepositoryConfigService() {
        return ContextHelper.get().beanForType(RepositoryConfigService.class);
    }

    @Nonnull
    public static Set<DataDiff<?>> extractRepoConfigs(Set<DataDiff<?>> dataDiffs) {
        Set<DataDiff<?>> repoDiffs =
                dataDiffs.stream().filter(getPatchYamlRepoConfigChangePredicate())
                        .collect(Collectors.toSet());
        dataDiffs.removeIf(getPatchYamlRepoConfigChangePredicate());
        return repoDiffs;
    }

    @Nonnull
    private static Predicate<DataDiff<?>> getPatchYamlRepoConfigChangePredicate() {
        return e -> e.getFieldName().startsWith(LOCAL_REPOSITORIES) ||
                e.getFieldName().startsWith(REMOTE_REPOSITORIES) ||
                e.getFieldName().startsWith(FEDERATED_REPOSITORIES) ||
                e.getFieldName().startsWith(VIRTUAL_REPOSITORIES);
    }

    private void checkNoEmptyRepoConfigIsPassed(DataDiff<?> e) {
        if (e.getFieldName().split(YML_INHERITANCE_MARKER).length == 2 && e.getNewValue() == null) {
            throw new IllegalArgumentException("No values under " + e.getFieldName().split(
                    YML_INHERITANCE_MARKER)[0]);
        }
    }

    private Optional<RepoConfig> createRepoByRepoType(RepoConfig repoConfig, Map<String, Object> stringObjectMap) {
        CentralConfigDescriptor descriptor = ContextHelper.get().getCentralConfig().getDescriptor();
        if (LOCAL.equals(repoConfig.getRepoType())) {
            return Optional.of(LocalRepoConfigMapper.mapLocalRepoConfig(
                    (LocalRepoConfig) repoConfig, stringObjectMap, descriptor));
        } else if (REMOTE.equals(repoConfig.getRepoType())) {
            return Optional.of(RemoteRepoConfigMapper.mapRemoteRepoConfig(
                    (RemoteRepoConfig) repoConfig, stringObjectMap, descriptor));
        } else if (VIRTUAL.equals(repoConfig.getRepoType())) {
            return Optional.of(VirtualRepoConfigMapper.mapVirtualRepoConfig(
                    (VirtualRepoConfig) repoConfig, stringObjectMap, descriptor, getRepositoryConfigService()));
        } else if (FEDERATED.equals(repoConfig.getRepoType())) {
            return Optional.of(FederatedRepoConfigMapper.mapFederatedRepoConfig(
                    (FederatedRepoConfig) repoConfig, stringObjectMap, descriptor));
        }
        return Optional.empty();
    }

    private void checkForDuplicate(String key, Map<String, RepoDetailsType> duplicateName, String type) {
        if (type.startsWith(LOCAL_REPOSITORIES)) {
            checkForDuplicate(key, duplicateName, LOCAL);
        } else if (type.startsWith(VIRTUAL_REPOSITORIES)) {
            checkForDuplicate(key, duplicateName, VIRTUAL);
        } else if (type.startsWith(FEDERATED_REPOSITORIES)) {
            checkForDuplicate(key, duplicateName, FEDERATED);
        } else if (type.startsWith(REMOTE_REPOSITORIES)) {
            checkForDuplicate(key, duplicateName, REMOTE);
        } else if (type.startsWith(RELEASE_BUNDLE_REPOSITORIES)) {
            checkForDuplicate(key, duplicateName, RELEASE_BUNDLE);
        } else {
            log.error("Repo details type - {} , for key - {} does not exist in the possible RepoDetailsType", type,
                    key);
            throw new ConfigurationException(
                    "Repo details type - " + type + " , for key - " + key +
                            " does not exist in the possible RepoDetailsType");
        }
    }

    private void checkForDuplicate(String key, Map<String, RepoDetailsType> duplicateName, RepoDetailsType type) {
        if (!duplicateName.computeIfAbsent(key, k -> type).equals(type)) {
            log.error("duplicate repo key {} for repo types {} {} ", key, duplicateName.get(key), type);
            throw new StorageException(
                    "duplicate repo key - " + key + " for repo types " + duplicateName.get(key) + " and " + type);
        }
    }

    /**
     * Creates RepoConfig based on YAML section type. For localRepositories, creates FederatedRepoConfig
     * if gridTopologyId or members are present (consistent with RestAddonImpl.createLocalRepo).
     */
    private void addRepoConfigByType(Map<String, RepoConfig> repoConfigs, String key, String yamlSectionType,
            Map<String, Object> repoData) {
        try {
            RepoType repoType = objectToRepoType(repoData.get("type"));
            if (yamlSectionType.startsWith(LOCAL_REPOSITORIES)) {
                if (hasFederationConfig(repoData)) {
                    repoConfigs.put(key, FederatedRepoConfig.create(key, repoType));
                } else {
                    repoConfigs.put(key, LocalRepoConfig.create(key, repoType));
                }
            } else if (yamlSectionType.startsWith(REMOTE_REPOSITORIES)) {
                repoConfigs.put(key, RemoteRepoConfig.create(key, repoType));
            } else if (yamlSectionType.startsWith(VIRTUAL_REPOSITORIES)) {
                repoConfigs.put(key, VirtualRepoConfig.create(key, repoType));
            } else if (yamlSectionType.startsWith(FEDERATED_REPOSITORIES)) {
                repoConfigs.put(key, FederatedRepoConfig.create(key, repoType));
            }
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("No values under type");
        }
    }

    /**
     * Checks if the repository config should be treated as federated (lenient mode).
     * Returns false on errors (topology not found, grid disabled, etc.).
     * <ul>
     *   <li>Explicit members → federated</li>
     *   <li>gridTopologyId pointing to MIRROR topology → federated</li>
     *   <li>gridTopologyId pointing to SELF_DEFINED topology → local</li>
     * </ul>
     */
    private boolean hasFederationConfig(Map<String, Object> repoConfig) {
        // Explicit members always means federated
        // Can be a List (from JSON array) or Map (from YAML diff nested structure)
        if (repoConfig.containsKey("members")) {
            Object members = repoConfig.get("members");
            if (members instanceof List && !((List<?>) members).isEmpty()) {
                return true;
            }
            if (members instanceof Map && !((Map<?, ?>) members).isEmpty()) {
                return true;
            }
        }

        // Check gridTopologyId - only MIRROR topology triggers federation
        if (repoConfig.containsKey("gridTopologyId")) {
            String gridTopologyId = (String) repoConfig.get("gridTopologyId");
            if (gridTopologyId != null && !gridTopologyId.isBlank()) {
                return isMirrorTopology(gridTopologyId);
            }
        }

        return false;
    }

    /**
     * Checks if a topology is MIRROR type (has remote members).
     * Returns false if grid is disabled.
     * Throws IllegalArgumentException if topology doesn't exist or lookup fails.
     */
    private boolean isMirrorTopology(String gridTopologyId) {
        if (!ConstantValues.gridEnabled.getBoolean()) {
            return false;
        }
        try {
            GridTopologyService gridTopologyService =
                    ContextHelper.get().beanForType(GridTopologyService.class);
            GridTopology topology = gridTopologyService.getGridTopologyById(gridTopologyId);
            return topology.getTopologyType() == GridTopology.TopologyType.MIRROR;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to get grid topology '" + gridTopologyId + "': " + e.getMessage(), e);
        }
    }

    private RepoType objectToRepoType(Object repoType) {
        return repoType != null ? RepoType.fromType((String) repoType) : RepoType.Generic;
    }

    public record RepoConfigChanges(List<RepoConfig> repoConfigsToUpsert, List<String> repoConfigsToDelete) {
        public boolean isNotEmpty() {
            return !repoConfigsToUpsert.isEmpty() || !repoConfigsToDelete.isEmpty();
        }
    }
}