package org.artifactory.repo.confighandler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.Diff;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.repo.ReferencedConfigChange.ConfigTrigger.REFERENCED_URL_BASE_CHANGE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;

/**
 * Handle base url configuration change event
 *
 * @author Dor Solomon
 */
@Component
@Slf4j
public class BaseUrlConfigChangeEventHandler extends AbstractConfigDescriptorEventHandler
        implements ConfigChangeEventHandler {

    private final Pattern baseUrlChangePattern = Pattern.compile("urlBase|federatedRepoUrlBase");

    @Override
    public void handle(ConfigChangeEventContext configEventContext) {
        handleCentralConfigDescriptorChangeEvent(configEventContext);
    }

    @Override
    public Set<SystemConfigChangeEvent.SystemConfigChangeEventType> getConfigChangeEventTypeSubscription() {
        return configChangeEventTypeSubscription;
    }

    @Override
    void conditionallyMarkRepositoriesForUpdateOrReload(String key, ConfigChangeType configChangeType,
            ConfigChangeEventContext configEventContext) {
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<FederatedRepoConfig> federatedRepoConfigs =
                repositoryConfigService.getRepoConfigsByType(FEDERATED).stream().map(FederatedRepoConfig.class::cast)
                        .toList();
        for (FederatedRepoConfig federatedRepoConfig : federatedRepoConfigs) {
            String repoKey = federatedRepoConfig.getKey();
            addToReposToReload(configEventContext, repoKey, REFERENCED_URL_BASE_CHANGE);
        }
    }

    @Override
    public Map<ConfigChangeType, List<String>> handleDiff(Map<String, Set<Diff<?>>> descriptorDiff) {
        Map<ConfigChangeType, List<String>> changesToHandle = new EnumMap<>(ConfigChangeType.class);
        descriptorDiff.forEach((key, diff) -> {
            Matcher matcher = getConfigPattern().matcher(key);
            if (matcher.matches()) {
                Optional<Diff<?>> optionalDiff = diff.stream().findFirst();
                if (optionalDiff.isPresent()) {
                    ConfigChangeType changeType = calculateChangeType(optionalDiff.get());
                    changesToHandle.put(changeType, List.of(key));
                }
            }
        });
        return changesToHandle;
    }

    private ConfigChangeType calculateChangeType(Diff<?> diff) {
        if (diff.getLeft() == null) {
            log.debug("Detected 'reference created' event with url base {}", diff.getRight());
            return ConfigChangeType.CREATED;
        }
        if (diff.getLeft() != null && diff.getRight() == null) {
            log.debug("Detected 'reference deleted' event of url base {}", diff.getLeft());
            return ConfigChangeType.DELETED;
        }
        log.debug("Detected 'reference updated' event of url base {} to {}", diff.getLeft(), diff.getRight());
        return ConfigChangeType.UPDATED;
    }

    @Override
    public Pattern getConfigPattern() {
        return baseUrlChangePattern;
    }
}