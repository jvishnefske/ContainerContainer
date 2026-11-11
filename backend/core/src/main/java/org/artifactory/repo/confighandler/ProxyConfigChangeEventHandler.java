package org.artifactory.repo.confighandler;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.model.ProxyRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

import static org.artifactory.repo.ReferencedConfigChange.ConfigTrigger.REFERENCED_PROXY_CHANGE;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;

/**
 * Handle proxy configuration change event
 *
 * @author shayb
 */
@Component
public class ProxyConfigChangeEventHandler extends AbstractConfigDescriptorEventHandler
        implements ConfigChangeEventHandler {
    private final Pattern proxyChangePattern =
            Pattern.compile("^(proxies)»(?<configKey>.[^»]*)»(?<excess>.[^»]*).*$");

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
        Collection<RepoConfig> allRepositoryConfigs = repositoryConfigService.getAllRepoConfigs();
        for (RepoConfig repoConfig : allRepositoryConfigs) {
            markRepoConfigForUpdateOrReload(configKey, configChangeType, configEventContext, repoConfig);
        }
    }

    private void markRepoConfigForUpdateOrReload(String configKey, ConfigChangeType changeType,
            ConfigChangeEventContext configEventContext, RepoConfig repoConfig) {
        if (RepoDetailsType.REMOTE == repoConfig.getRepoType() ||
                RepoDetailsType.FEDERATED == repoConfig.getRepoType()) {
            String repoKey = repoConfig.getKey();
            ProxyRepoConfig proxyRepoConfig = (ProxyRepoConfig) repoConfig;
            String proxyRef = proxyRepoConfig.getProxyRef();
            if (proxyRef == null) {
                handleDefaultProxyChange(configKey, configEventContext, proxyRepoConfig);
            } else if (configKey.equals(proxyRef)) {
                if (changeType == ConfigChangeType.UPDATED) {
                    addToReposToReload(configEventContext, repoConfig.getKey(), REFERENCED_PROXY_CHANGE);
                } else if (changeType == ConfigChangeType.DELETED) {
                    addToReposToUpdate(configEventContext, repoKey, proxyRepoConfig);
                }
            }
        }
    }

    private void addToReposToUpdate(ConfigChangeEventContext configEventContext, String repoKey,
            ProxyRepoConfig proxyRepoConfig) {
        ProxyRepoConfig configToUpdate =
                (ProxyRepoConfig) configEventContext.getReposToUpdate().get(repoKey);
        configToUpdate = configToUpdate != null ? configToUpdate : proxyRepoConfig;
        configToUpdate.setProxyRef(null);
        configEventContext.getReposToUpdate().put(repoKey, (RepoConfig) configToUpdate);
    }

    private void handleDefaultProxyChange(String proxyKey, ConfigChangeEventContext configEventContext,
            ProxyRepoConfig repoConfig) {
        CentralConfigDescriptorImpl newConfig =
                (CentralConfigDescriptorImpl) configEventContext.getEvent().getNewConfig();
        CentralConfigDescriptorImpl oldConfig =
                (CentralConfigDescriptorImpl) configEventContext.getEvent().getOldConfig();

        ProxyDescriptor potentialDefaultProxy = newConfig.getProxy(proxyKey);
        ProxyDescriptor oldConfigProxy = oldConfig.getProxy(proxyKey);

        if (!isDefaultProxy(potentialDefaultProxy, oldConfigProxy) || repoConfig.isProxyDisabled()) {
            return;
        }
        addToReposToReload(configEventContext, repoConfig.getKey(), REFERENCED_PROXY_CHANGE);
    }

    private boolean isDefaultProxy(ProxyDescriptor potentialDefaultProxy, ProxyDescriptor oldConfigProxy) {
        return (potentialDefaultProxy != null && potentialDefaultProxy.isPlatformDefault()) ||
                (oldConfigProxy != null && oldConfigProxy.isPlatformDefault());
    }

    @Override
    public Pattern getConfigPattern() {
        return proxyChangePattern;
    }
}
