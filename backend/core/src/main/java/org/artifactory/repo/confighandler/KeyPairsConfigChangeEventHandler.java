package org.artifactory.repo.confighandler;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.artifactory.repo.ReferencedConfigChange.ConfigTrigger.REFERENCED_KEYPAIR_CHANGE;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;

/**
 * @author Uriah Levy
 */
@Component
public class KeyPairsConfigChangeEventHandler extends AbstractConfigDescriptorEventHandler implements
        ConfigChangeEventHandler {
    private static final Logger log = LoggerFactory.getLogger(KeyPairsConfigChangeEventHandler.class);
    private final Pattern keyPairsChangePattern =
            Pattern.compile("^(keyPairs)»(?<configKey>.[^»]*)»(?<excess>.[^»]*).*$");

    @Override
    public void handle(ConfigChangeEventContext configEventContext) {
        handleCentralConfigDescriptorChangeEvent(configEventContext);
    }

    @Override
    public Set<SystemConfigChangeEventType> getConfigChangeEventTypeSubscription() {
        return configChangeEventTypeSubscription;
    }

    @Override
    void conditionallyMarkRepositoriesForUpdateOrReload(String configKey, ConfigChangeType configChangeType,
            ConfigChangeEventContext configEventContext) {
        Set<RepoConfig> repoConfigs = new HashSet<>();
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        repoConfigs.addAll(
                new HashSet<>(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)));
        repoConfigs.addAll(
                new HashSet<>(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)));
        repoConfigs.addAll(
                new HashSet<>(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)));
        handleSecondaryAndPrimaryKeyPairs(configKey, configChangeType, configEventContext, repoConfigs);
    }

    @Override
    Pattern getConfigPattern() {
        return keyPairsChangePattern;
    }

    private void handleSecondaryAndPrimaryKeyPairs(String configKey, ConfigChangeType configChangeType,
            ConfigChangeEventContext configEventContext, Set<RepoConfig> repoConfigs) {
        // Secondaries
        repoConfigs.stream()
                .filter(config -> config.getSecurityConfig().getSecondaryKeyPairRef() != null &&
                        config.getSecurityConfig().getSecondaryKeyPairRef().equals(configKey))
                .forEach(
                        config -> handleReloadOrUpdate(configKey, configChangeType, configEventContext, config, false));
        // Primaries
        repoConfigs.stream()
                .filter(config -> config.getSecurityConfig().getPrimaryKeyPairRef() != null &&
                        config.getSecurityConfig().getPrimaryKeyPairRef().equals(configKey))
                .forEach(config -> handleReloadOrUpdate(configKey, configChangeType, configEventContext, config, true));
    }

    private void handleReloadOrUpdate(String keyPairConfigKey, ConfigChangeType configChangeType,
            ConfigChangeEventContext configEventContext,
            RepoConfig dbRepoConfig, boolean isPrimaryRepoKeyPair) {
        RepoConfig repoConfigToUpdate = configEventContext.getReposToUpdate().get(dbRepoConfig.getKey());
        repoConfigToUpdate = repoConfigToUpdate != null ? repoConfigToUpdate : dbRepoConfig;
        if (configChangeType == ConfigChangeType.UPDATED) {
            addToReposToReload(configEventContext, repoConfigToUpdate.getKey(), REFERENCED_KEYPAIR_CHANGE);
            log.debug("Reloading repository with key '{}' after Key Pair changed event for Key Pair '{}'",
                    repoConfigToUpdate.getKey(), keyPairConfigKey);
        } else if (configChangeType == ConfigChangeType.DELETED) {
            // If it's deleted we need to dereference the key-pair
            if (isPrimaryRepoKeyPair) {
                log.info("Dereferencing Primary Key Pair with key '{}' from Repository '{}'", keyPairConfigKey,
                        repoConfigToUpdate.getKey());
                repoConfigToUpdate.getSecurityConfig().setPrimaryKeyPairRef(null);
                if (repoConfigToUpdate.getSecurityConfig().getSecondaryKeyPairRef() != null) {
                    promoteSecondaryKeyToPrimary(keyPairConfigKey, repoConfigToUpdate);
                }
            } else {
                log.info("Dereferencing Secondary Key Pair with key '{}' from Repository '{}'", keyPairConfigKey,
                        repoConfigToUpdate.getKey());
                repoConfigToUpdate.getSecurityConfig().setSecondaryKeyPairRef(null);
            }
            configEventContext.getReposToUpdate().put(repoConfigToUpdate.getKey(), repoConfigToUpdate);
        }
    }

    private void promoteSecondaryKeyToPrimary(String keyPairConfigKey, RepoConfig repoConfigToUpdate) {
        log.info("Promoting found Secondary Key Pair with key '{}' to Primary on Repository '{}'",
                repoConfigToUpdate.getSecurityConfig().getSecondaryKeyPairRef(),
                repoConfigToUpdate.getKey());
        repoConfigToUpdate.getSecurityConfig()
                .setPrimaryKeyPairRef(repoConfigToUpdate.getSecurityConfig().getSecondaryKeyPairRef());
        repoConfigToUpdate.getSecurityConfig().setSecondaryKeyPairRef(null);
        log.debug("Key Pair with key '{}' was dereferenced from Repository '{}'", keyPairConfigKey,
                repoConfigToUpdate.getKey());
    }
}
