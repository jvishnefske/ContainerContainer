package org.artifactory.repo.confighandler;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.jfrog.common.config.validation.NotUnicode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.artifactory.repo.ReferencedConfigChange.ConfigTrigger.REFERENCED_PROPERTY_SET_CHANGE;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;

/**
 * @author Uriah Levy
 */
@Component
public class PropertySetsConfigHandler extends AbstractConfigDescriptorEventHandler
        implements ConfigChangeEventHandler {

    private final Pattern propertySetsChangePattern =
            Pattern.compile("^(propertySets)»(?<configKey>.[^»]*)»(?<excess>.[^»]*).*$");

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
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        Set<LocalRepoConfig> localRepoConfigs =
                repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL).stream()
                        .map(LocalRepoConfig.class::cast)
                        .collect(Collectors.toSet());

        Set<RemoteRepoConfig> remoteRepoConfigs =
                repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE).stream()
                        .map(RemoteRepoConfig.class::cast)
                        .collect(Collectors.toSet());

        localRepoConfigs.forEach(dbRepoConfig -> {
            List<@NotUnicode String> propertySetRefs = dbRepoConfig.getRepoTypeConfig().getPropertySetRefs();
            propertySetRefs.stream()
                    .filter(configKey::equals)
                    .findFirst()
                    .ifPresent(changedPropertySetKey -> handleReloadOrUpdate(changedPropertySetKey, configChangeType,
                            configEventContext, dbRepoConfig));
        });

        remoteRepoConfigs.forEach(dbRepoConfig -> {
            List<@NotUnicode String> propertySetRefs = dbRepoConfig.getRepoTypeConfig().getPropertySetRefs();
            propertySetRefs.stream()
                    .filter(configKey::equals)
                    .findFirst()
                    .ifPresent(changedPropertySetKey -> handleReloadOrUpdate(changedPropertySetKey, configChangeType,
                            configEventContext, dbRepoConfig));
        });
    }

    @Override
    Pattern getConfigPattern() {
        return propertySetsChangePattern;
    }

    private void handleReloadOrUpdate(@NotUnicode String changedPropertySetKey, ConfigChangeType changeType,
            ConfigChangeEventContext configEventContext, RepoConfig dbRepoConfig) {
        // Prefer to update an already mutated copy if we have one in hand, otherwise the one from the DB
        RepoConfig configToUpdate = configEventContext.getReposToUpdate().get(dbRepoConfig.getKey());
        configToUpdate = configToUpdate != null ? configToUpdate : dbRepoConfig;
        if (changeType == ConfigChangeType.UPDATED) {
            addToReposToReload(configEventContext, configToUpdate.getKey(), REFERENCED_PROPERTY_SET_CHANGE);
        } else if (changeType == ConfigChangeType.DELETED) {
            RepoDetailsType repoType = configToUpdate.getRepoType();
            if (RepoDetailsType.LOCAL.equals(repoType)) {
                removePropertySetFromLocalRepoConfig(changedPropertySetKey, configEventContext, configToUpdate);
            } else if (RepoDetailsType.REMOTE.equals(repoType)) {
                removePropertySetFromRemoteRepoConfig(changedPropertySetKey, configEventContext, configToUpdate);
            }
        }
    }

    private void removePropertySetFromRemoteRepoConfig(String changedPropertySetKey,
            ConfigChangeEventContext configEventContext, RepoConfig configToUpdate) {
        RemoteRepositoryRepoTypeConfig remoteRepositoryRepoTypeConfig =
                (RemoteRepositoryRepoTypeConfig) configToUpdate.getRepoTypeConfig();
        List<@NotUnicode String> currentRefs = remoteRepositoryRepoTypeConfig.getPropertySetRefs();
        ArrayList<@NotUnicode String> mutableRefList = new ArrayList<>(currentRefs);
        mutableRefList.remove(changedPropertySetKey);
        remoteRepositoryRepoTypeConfig.setPropertySetRefs(mutableRefList);
        configEventContext.getReposToUpdate().put(configToUpdate.getKey(), configToUpdate);
    }

    private void removePropertySetFromLocalRepoConfig(String changedPropertySetKey,
            ConfigChangeEventContext configEventContext, RepoConfig configToUpdate) {
        LocalRepositoryRepoTypeConfig repoTypeConfig =
                (LocalRepositoryRepoTypeConfig) configToUpdate.getRepoTypeConfig();
        List<@NotUnicode String> currentRefs = repoTypeConfig.getPropertySetRefs();
        ArrayList<@NotUnicode String> mutableRefList = new ArrayList<>(currentRefs);
        mutableRefList.remove(changedPropertySetKey);
        repoTypeConfig.setPropertySetRefs(mutableRefList);
        configEventContext.getReposToUpdate().put(configToUpdate.getKey(), configToUpdate);
    }
}
