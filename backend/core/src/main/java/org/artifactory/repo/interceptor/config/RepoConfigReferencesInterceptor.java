/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.interceptor.config;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * @author Tamar Rosen
 */
@Component
@Slf4j
public class RepoConfigReferencesInterceptor implements RepoConfigChangeInterceptor {
    static final String ERR_REF_DOES_NOT_EXIST =
            "Cannot create or update repo '%s': %s reference '%s' does not exist in the config descriptor.";

    static final String WARN_REF_DOES_NOT_EXIST =
            "Repo '%s': contains %s reference '%s' does not exist in the config descriptor.";

    private final CentralConfigService configService;
    private final RepositoryService repositoryService;
    private static final String PROP_SET_REF_TYPE = "property set";
    private static final String PROXY_REF_TYPE = "proxy";
    private static final String REPO_LAYOUT_REF_TYPE = "repo layout";
    private static final String SECONDARY_KEY_PAIR_REF_TYPE = "secondary key pair";
    private static final String PRIMARY_KEY_PAIR_REF_TYPE = "primary key pair";
    private static final String REPO_REF_TYPE = "repo";

    @Autowired
    public RepoConfigReferencesInterceptor(CentralConfigService configService,
            RepositoryService repositoryService) {
        this.configService = configService;
        this.repositoryService = repositoryService;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        if (!configService.isContextCreated()) {
            return;
        }
        CentralConfigDescriptor configDescriptor = configService.getDescriptor();
        List<RepoConfigChange> newLocalRepoConfigChanges =
                getNewRepoConfigsOfType(mutableRepoConfigChanges, RepoDetailsType.LOCAL);
        List<RepoConfigChange> newRemoteRepoConfigChanges =
                getNewRepoConfigsOfType(mutableRepoConfigChanges, RepoDetailsType.REMOTE);
        List<RepoConfigChange> newFedRepoConfigChanges =
                getNewRepoConfigsOfType(mutableRepoConfigChanges, RepoDetailsType.FEDERATED);
        List<RepoConfigChange> newVirtualRepoConfigChanges =
                getNewRepoConfigsOfType(mutableRepoConfigChanges, RepoDetailsType.VIRTUAL);
        List<RepoConfigChange> newReleaseBundleRepoConfigChanges =
                getNewRepoConfigsOfType(mutableRepoConfigChanges, RepoDetailsType.RELEASE_BUNDLE);

        List<RepoConfigChange> allNewRepoConfigChanges =
                Stream.of(newRemoteRepoConfigChanges, newLocalRepoConfigChanges, newVirtualRepoConfigChanges,
                                newFedRepoConfigChanges,
                                newReleaseBundleRepoConfigChanges)
                        .flatMap(Collection::stream).toList();

        validateRepoLayoutRef(allNewRepoConfigChanges, newRemoteRepoConfigChanges, configDescriptor);
        validateProxyRef(newRemoteRepoConfigChanges, newFedRepoConfigChanges, configDescriptor);
        validateKeyPairRef(allNewRepoConfigChanges, configDescriptor);
        validatePropertySetRefs(newLocalRepoConfigChanges, newReleaseBundleRepoConfigChanges, newFedRepoConfigChanges,
                newRemoteRepoConfigChanges, configDescriptor);
        validateRepoConfigRefs(allNewRepoConfigChanges, newVirtualRepoConfigChanges);
    }

    private void validateRepoLayoutRef(List<RepoConfigChange> allNewRepoConfigChanges,
            List<RepoConfigChange> newRemoteRepoConfigChanges, CentralConfigDescriptor configDescriptor) {
        allNewRepoConfigChanges
                .forEach(repoConfigChange -> handleRepoLayoutRef(configDescriptor, repoConfigChange));

        newRemoteRepoConfigChanges
                .forEach(repoConfigChange -> handleRemoteRepoLayoutRef(configDescriptor, repoConfigChange));
    }

    private void handleRemoteRepoLayoutRef(CentralConfigDescriptor configDescriptor,
            RepoConfigChange remoteRepoConfigChange) {
        String remoteRepoLayoutRef =
                ((RemoteRepoConfig) remoteRepoConfigChange.getNewRepoConfig()).getRepoTypeConfig()
                        .getRemoteRepoLayoutRef();
        if (remoteRepoLayoutRef != null && configDescriptor.getRepoLayout(remoteRepoLayoutRef) == null) {
            if (remoteRepoConfigChange.getOldRepoConfig() == null) {
                throw new ConfigurationException(
                        getErrorMessage(remoteRepoConfigChange.getRepoKey(), REPO_LAYOUT_REF_TYPE,
                                remoteRepoLayoutRef));
            } else {
                String oldRemoteRepoLayoutRef =
                        ((RemoteRepoConfig) remoteRepoConfigChange.getOldRepoConfig()).getRepoTypeConfig()
                                .getRemoteRepoLayoutRef();
                if (remoteRepoLayoutRef.equals(oldRemoteRepoLayoutRef)) {
                    remoteRepoConfigChange.getNewRepoConfig().getBaseConfig().setRepoLayoutRef(null);
                    log.warn(getWarnMessage(remoteRepoConfigChange.getRepoKey(), REPO_LAYOUT_REF_TYPE,
                            remoteRepoLayoutRef));
                } else {
                    throw new ConfigurationException(
                            getErrorMessage(remoteRepoConfigChange.getRepoKey(), REPO_LAYOUT_REF_TYPE,
                                    remoteRepoLayoutRef));
                }
            }
        }
    }

    private void handleRepoLayoutRef(CentralConfigDescriptor configDescriptor, RepoConfigChange repoConfigChange) {
        String repoLayoutRef = repoConfigChange.getNewRepoConfig().getBaseConfig().getRepoLayoutRef();
        if (repoLayoutRef != null && configDescriptor.getRepoLayout(repoLayoutRef) == null) {
            if (repoConfigChange.getOldRepoConfig() == null) {
                throw new ConfigurationException(
                        getErrorMessage(repoConfigChange.getRepoKey(), REPO_LAYOUT_REF_TYPE, repoLayoutRef));
            } else {
                String oldRepoLayoutRef =
                        repoConfigChange.getOldRepoConfig().getBaseConfig().getRepoLayoutRef();
                if (repoLayoutRef.equals(oldRepoLayoutRef)) {
                    repoConfigChange.getNewRepoConfig().getBaseConfig().setRepoLayoutRef(null);
                    log.warn(getWarnMessage(repoConfigChange.getRepoKey(), REPO_LAYOUT_REF_TYPE, repoLayoutRef));
                } else {
                    throw new ConfigurationException(
                            getErrorMessage(repoConfigChange.getRepoKey(), REPO_LAYOUT_REF_TYPE, repoLayoutRef));
                }
            }
        }
    }

    private void validateProxyRef(List<RepoConfigChange> newRemoteRepoConfigChanges,
            List<RepoConfigChange> newFedRepoConfigChanges, CentralConfigDescriptor configDescriptor) {
        newRemoteRepoConfigChanges.forEach(repoConfigChange -> {
            String proxyRef = ((RemoteRepoConfig) repoConfigChange.getNewRepoConfig()).getProxyRef();
            String oldProxyRef = repoConfigChange.getOldRepoConfig() == null ? null :
                    ((RemoteRepoConfig) repoConfigChange.getOldRepoConfig()).getProxyRef();
            handleProxyRef(configDescriptor, repoConfigChange, proxyRef, oldProxyRef);
        });
        newFedRepoConfigChanges.forEach(repoConfigChange -> {
            String proxyRef = ((FederatedRepoConfig) repoConfigChange.getNewRepoConfig()).getProxyRef();
            String oldProxyRef = null;
            if (repoConfigChange.getOldRepoConfig() instanceof FederatedRepoConfig oldFedRepoConfig) {
                oldProxyRef = oldFedRepoConfig.getProxyRef();
            }
            handleProxyRef(configDescriptor, repoConfigChange, proxyRef, oldProxyRef);
        });
    }

    private void handleProxyRef(CentralConfigDescriptor configDescriptor, RepoConfigChange repoConfigChange,
            String proxyRef, String oldProxyRef) {
        if (proxyRef != null && configDescriptor.getProxy(proxyRef) == null) {
            if (proxyRef.equals(oldProxyRef)) {
                if (repoConfigChange.getNewRepoConfig() instanceof RemoteRepoConfig remoteRepoConfig) {
                    remoteRepoConfig.getRepoTypeConfig().setProxyRef(null);
                }
                if (repoConfigChange.getNewRepoConfig() instanceof FederatedRepoConfig federatedRepoConfig) {
                    federatedRepoConfig.getRepoTypeConfig().setProxyRef(null);
                }
                log.warn(getWarnMessage(repoConfigChange.getRepoKey(), PROXY_REF_TYPE, proxyRef));
            } else {
                throw new ConfigurationException(
                        getErrorMessage(repoConfigChange.getRepoKey(), PROXY_REF_TYPE, proxyRef));
            }
        }
    }

    private void validatePropertySetRefs(List<RepoConfigChange> newLocalRepoConfigChanges,
            List<RepoConfigChange> newReleaseBundleRepoConfigChanges,
            List<RepoConfigChange> newFederatedRepoConfigChanges,
            List<RepoConfigChange> newRemoteRepoConfigChanges, CentralConfigDescriptor configDescriptor) {

        Stream.of(newLocalRepoConfigChanges, newReleaseBundleRepoConfigChanges, newFederatedRepoConfigChanges,
                        newRemoteRepoConfigChanges)
                .flatMap(Collection::stream)
                .forEach(repoConfigChange -> {
                    List<String> propertySetRefs =
                            ((RealRepoConfig) repoConfigChange.getNewRepoConfig()).getRepoTypeConfig()
                                    .getPropertySetRefs();
                    handlePropertySetRefs(configDescriptor, repoConfigChange, propertySetRefs);
                });
    }

    private void handlePropertySetRefs(CentralConfigDescriptor configDescriptor, RepoConfigChange repoConfigChange,
            List<String> propertySetRefs) {
        if (propertySetRefs == null) {
            return;
        }
        List<String> propertySetRefsToRemove = new ArrayList<>();
        propertySetRefs.forEach(propertySetRef -> {
            if (propertySetRef != null && configDescriptor.getPropertySets().stream()
                    .noneMatch(propertySet -> propertySetRef.equals(propertySet.getName()))) {
                RealRepoConfig oldRepoConfig = (RealRepoConfig) repoConfigChange.getOldRepoConfig();
                if (oldRepoConfig == null) {
                    throw new ConfigurationException(
                            getErrorMessage(repoConfigChange.getRepoKey(), PROP_SET_REF_TYPE, propertySetRef));
                }
                List<String> oldPropertySetRefs = oldRepoConfig.getRepoTypeConfig().getPropertySetRefs();
                if (oldPropertySetRefs.contains(propertySetRef)) {
                    propertySetRefsToRemove.add(propertySetRef);
                    log.warn(getWarnMessage(repoConfigChange.getRepoKey(), PROP_SET_REF_TYPE, propertySetRef));
                } else {
                    throw new ConfigurationException(
                            getErrorMessage(repoConfigChange.getRepoKey(), PROP_SET_REF_TYPE, propertySetRef));
                }
            }
        });
        if (!propertySetRefsToRemove.isEmpty()) {
            ArrayList<String> newPropertySetRefs = new ArrayList<>(propertySetRefs);
            newPropertySetRefs.removeAll(propertySetRefsToRemove);
            ((RealRepoConfig) repoConfigChange.getNewRepoConfig()).getRepoTypeConfig()
                    .setPropertySetRefs(newPropertySetRefs);
        }
    }

    private void validateKeyPairRef(List<RepoConfigChange> allNewRepoConfigs,
            CentralConfigDescriptor configDescriptor) {
        allNewRepoConfigs.forEach(repoConfigChange -> {
            handlePrimaryKeyPair(configDescriptor, repoConfigChange);
            handleSecondaryKeyPair(configDescriptor, repoConfigChange);
        });
    }

    private void handleSecondaryKeyPair(CentralConfigDescriptor configDescriptor, RepoConfigChange repoConfigChange) {
        String secondaryKeyPairRef = repoConfigChange.getNewRepoConfig().getSecurityConfig().getSecondaryKeyPairRef();
        if (secondaryKeyPairRef != null && configDescriptor.getKeyPair(secondaryKeyPairRef) == null) {
            if (repoConfigChange.getOldRepoConfig() == null) {
                throw new ConfigurationException(
                        getErrorMessage(repoConfigChange.getRepoKey(), SECONDARY_KEY_PAIR_REF_TYPE,
                                secondaryKeyPairRef));
            } else {
                String oldKeyPairRef =
                        repoConfigChange.getOldRepoConfig().getSecurityConfig().getSecondaryKeyPairRef();
                if (secondaryKeyPairRef.equals(oldKeyPairRef)) {
                    repoConfigChange.getNewRepoConfig().getSecurityConfig().setSecondaryKeyPairRef(null);
                    log.warn(getWarnMessage(repoConfigChange.getRepoKey(), SECONDARY_KEY_PAIR_REF_TYPE,
                            secondaryKeyPairRef));
                } else {
                    throw new ConfigurationException(
                            getErrorMessage(repoConfigChange.getRepoKey(), SECONDARY_KEY_PAIR_REF_TYPE,
                                    secondaryKeyPairRef));
                }
            }
        }
    }

    private void handlePrimaryKeyPair(CentralConfigDescriptor configDescriptor, RepoConfigChange repoConfigChange) {
        String primaryKeyPairRef = repoConfigChange.getNewRepoConfig().getSecurityConfig().getPrimaryKeyPairRef();
        if (primaryKeyPairRef != null && configDescriptor.getKeyPair(primaryKeyPairRef) == null) {
            if (repoConfigChange.getOldRepoConfig() == null) {
                throw new ConfigurationException(
                        getErrorMessage(repoConfigChange.getRepoKey(), PRIMARY_KEY_PAIR_REF_TYPE, primaryKeyPairRef));
            } else {
                String oldKeyPairRef =
                        repoConfigChange.getOldRepoConfig().getSecurityConfig().getPrimaryKeyPairRef();
                if (primaryKeyPairRef.equals(oldKeyPairRef)) {
                    repoConfigChange.getNewRepoConfig().getSecurityConfig().setPrimaryKeyPairRef(null);
                    log.warn(getWarnMessage(repoConfigChange.getRepoKey(), PRIMARY_KEY_PAIR_REF_TYPE,
                            primaryKeyPairRef));
                } else {
                    throw new ConfigurationException(
                            getErrorMessage(repoConfigChange.getRepoKey(), PRIMARY_KEY_PAIR_REF_TYPE,
                                    primaryKeyPairRef));
                }
            }
        }
    }

    private List<RepoConfigChange> getNewRepoConfigsOfType(
            Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges, RepoDetailsType repoType) {
        List<RepoConfigChange> repoConfigChangesOfType = repoConfigChanges.getOrDefault(repoType, List.of());
        return getNewRepoConfigChanges(repoConfigChangesOfType);
    }

    @Nonnull
    private List<RepoConfigChange> getNewRepoConfigChanges(List<RepoConfigChange> repoConfigChanges) {
        return repoConfigChanges.stream()
                .filter(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                        REPO_UPDATE.equals(repoConfigChange.getConfigAction()))
                .toList();
    }

    private String getErrorMessage(String repoKey, String referenceType, String referenceKey) {
        return String.format(ERR_REF_DOES_NOT_EXIST, repoKey, referenceType, referenceKey);
    }

    private String getWarnMessage(String repoKey, String referenceType, String referenceKey) {
        return String.format(WARN_REF_DOES_NOT_EXIST, repoKey, referenceType, referenceKey);
    }

    private void validateRepoConfigRefs(List<RepoConfigChange> allNewRepoConfigChanges,
            List<RepoConfigChange> newVirtualRepoConfigChanges) {
        Set<String> allNewRepoConfigsKeySet = allNewRepoConfigChanges.stream()
                .map(RepoConfigChange::getNewRepoConfig)
                .map(RepoConfig::getKey).collect(Collectors.toSet());

        newVirtualRepoConfigChanges.forEach(virtualRepoConfigChange -> {
            handleRepositoryRefs(allNewRepoConfigsKeySet, virtualRepoConfigChange);
            handleDefaultDeploymentRepoRef(allNewRepoConfigsKeySet, virtualRepoConfigChange);
        });
    }

    private void handleDefaultDeploymentRepoRef(Set<String> allNewRepoConfigsKeySet,
            RepoConfigChange virtualRepoConfigChange) {
        VirtualRepoConfig newVirtualRepoConfig = (VirtualRepoConfig) virtualRepoConfigChange.getNewRepoConfig();
        String defaultDeploymentRepoRef = newVirtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
        if (defaultDeploymentRepoRef == null || allNewRepoConfigsKeySet.contains(defaultDeploymentRepoRef)) {
            return;
        }
        if (!repositoryService.existsByRepoKey(defaultDeploymentRepoRef)) {
            // newly created virtual repo - throw exception
            if (virtualRepoConfigChange.getOldRepoConfig() == null) {
                throw new ConfigurationException(
                        getErrorMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE, defaultDeploymentRepoRef));
            } else {
                String oldDefaultDeploymentRepoRef =
                        ((VirtualRepoConfig) virtualRepoConfigChange.getOldRepoConfig()).getRepoTypeConfig()
                                .getDefaultDeploymentRepoRef();
                // not a new repo ref - remove ref and log warning
                if (defaultDeploymentRepoRef.equals(oldDefaultDeploymentRepoRef)) {
                    newVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(null);
                    log.warn(getWarnMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE,
                            defaultDeploymentRepoRef));
                } else {
                    // new repo ref - throw exception
                    throw new ConfigurationException(
                            getErrorMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE,
                                    defaultDeploymentRepoRef));
                }
            }
        }
    }

    /**
     * Validates repository references for a virtual repo.
     * <p>
     * For federated virtual repos, dangling references (children that don't exist) are ALLOWED - we just log a warning.
     * This supports federation use cases where child repos may be created later on target sites.
     * <p>
     * For non-federated virtual repos, we keep the legacy strict behavior:
     * - new missing references fail the save
     * - existing missing references are removed (and a warning is logged)
     */
    private void handleRepositoryRefs(Set<String> allNewRepoConfigsKeySet,
            RepoConfigChange virtualRepoConfigChange) {
        VirtualRepoConfig newVirtualRepoConfig = (VirtualRepoConfig) virtualRepoConfigChange.getNewRepoConfig();
        List<String> repositoryRefs = newVirtualRepoConfig.getRepoTypeConfig().getRepositoryRefs();
        if (repositoryRefs == null) {
            return;
        }
        if (newVirtualRepoConfig.getBaseConfig() == null || !newVirtualRepoConfig.getBaseConfig().isFederated()) {
            handleNonFederatedRepositoryRefs(allNewRepoConfigsKeySet, virtualRepoConfigChange, newVirtualRepoConfig,
                    repositoryRefs);
            return;
        }

        handleFederatedRepositoryRefs(allNewRepoConfigsKeySet, virtualRepoConfigChange, repositoryRefs);
    }

    private void handleNonFederatedRepositoryRefs(Set<String> allNewRepoConfigsKeySet,
            RepoConfigChange virtualRepoConfigChange,
            VirtualRepoConfig newVirtualRepoConfig,
            List<String> repositoryRefs) {
        List<String> repositoryRefsToRemove = new ArrayList<>();
        for (String repositoryRef : repositoryRefs) {
            if (allNewRepoConfigsKeySet.contains(repositoryRef) || repositoryService.existsByRepoKey(repositoryRef)) {
                continue;
            }

            if (virtualRepoConfigChange.getOldRepoConfig() == null) {
                // newly created virtual repo - throw exception
                throw new ConfigurationException(
                        getErrorMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE, repositoryRef));
            }

            List<String> oldRepositoryRefs = ((VirtualRepoConfig) virtualRepoConfigChange.getOldRepoConfig())
                    .getRepoTypeConfig()
                    .getRepositoryRefs();
            oldRepositoryRefs = oldRepositoryRefs == null ? List.of() : oldRepositoryRefs;

            // not a new repo ref - remove ref and log warning
            if (oldRepositoryRefs.contains(repositoryRef)) {
                repositoryRefsToRemove.add(repositoryRef);
                log.warn(getWarnMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE, repositoryRef));
            } else {
                // new repo ref - throw exception
                throw new ConfigurationException(
                        getErrorMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE, repositoryRef));
            }
        }

        if (!repositoryRefsToRemove.isEmpty()) {
            List<String> newRepositoryRefs = new ArrayList<>(repositoryRefs);
            newRepositoryRefs.removeAll(repositoryRefsToRemove);
            newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(newRepositoryRefs);
        }
    }

    private void handleFederatedRepositoryRefs(Set<String> allNewRepoConfigsKeySet,
            RepoConfigChange virtualRepoConfigChange,
            List<String> repositoryRefs) {
        for (String repositoryRef : repositoryRefs) {
            if (allNewRepoConfigsKeySet.contains(repositoryRef)) {
                continue;
            }
            // Note: this is a LOCAL config-cache existence check. We intentionally do not attempt to query other
            // sites/nodes here, because in federation scenarios child repos may be created later on target sites.
            if (!repositoryService.existsByRepoKey(repositoryRef)) {
                // Child repo doesn't exist - log warning but allow (dangling reference)
                log.warn(getWarnMessage(virtualRepoConfigChange.getRepoKey(), REPO_REF_TYPE, repositoryRef));
            }
        }
    }
}
