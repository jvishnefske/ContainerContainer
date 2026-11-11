package org.artifactory.repo.confighandler;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.util.RepoLayoutUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

import static org.artifactory.repo.ReferencedConfigChange.ConfigTrigger.REFERENCED_LAYOUT_CHANGE;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;

/**
 * Handle layout configuration change event
 *
 * @author shayb
 */
@Component
public class LayoutConfigChangeEventHandler extends AbstractConfigDescriptorEventHandler implements
        ConfigChangeEventHandler {
    private final Pattern layoutChangePattern =
            Pattern.compile("^(repoLayouts)»(?<configKey>.[^»]*)»(?<excess>.[^»]*).*$");

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
        Collection<RepoConfig> allRepositoryConfigs =
                ContextHelper.get().beanForType(RepositoryConfigService.class).getAllRepoConfigs();
        allRepositoryConfigs.forEach(dbRepoConfig -> {
            String repoKey = dbRepoConfig.getKey();
            String layoutRef = dbRepoConfig.getBaseConfig().getRepoLayoutRef();
            if (configKey.equals(layoutRef)) {
                if (configChangeType == ConfigChangeType.UPDATED) {
                    addToReposToReload(configEventContext, repoKey, REFERENCED_LAYOUT_CHANGE);
                } else if (configChangeType == ConfigChangeType.DELETED) {
                    markReposToUpdate(configKey, configEventContext, dbRepoConfig, repoKey);
                }
            }
        });
    }

    private void markReposToUpdate(String configKey, ConfigChangeEventContext configEventContext,
            RepoConfig dbRepoConfig, String repoKey) {
        RepoConfig configToUpdate = configEventContext.getReposToUpdate().get(repoKey);
        configToUpdate = configToUpdate != null ? configToUpdate : dbRepoConfig;

        switch (configToUpdate.getRepoType()) {
            case LOCAL -> configToUpdate.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
            case REMOTE -> {
                RemoteRepoConfig remoteConfigToUpdate = (RemoteRepoConfig) configToUpdate;
                remoteConfigToUpdate.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT_NAME);
                if (configKey.equals(remoteConfigToUpdate.getRepoTypeConfig().getRemoteRepoLayoutRef())) {
                    remoteConfigToUpdate.getRepoTypeConfig().setRemoteRepoLayoutRef(null);
                }
            }
            case VIRTUAL -> configToUpdate.getBaseConfig().setRepoLayoutRef(null);
            default -> {/*NOOP*/}
        }
        configEventContext.getReposToUpdate().put(repoKey, configToUpdate);
    }

    @Override
    Pattern getConfigPattern() {
        return layoutChangePattern;
    }
}
