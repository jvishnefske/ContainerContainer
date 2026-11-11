package org.artifactory.repo.confighandler;

import org.apache.commons.lang3.builder.Diff;
import org.artifactory.repo.ReferencedConfigChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;

/**
 * Abstract class with base methods for config descriptor event handler
 *
 * @author shayb
 */
public abstract class AbstractConfigDescriptorEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractConfigDescriptorEventHandler.class);
    protected static final Set<SystemConfigChangeEventType> configChangeEventTypeSubscription =
            Set.of(ARTIFACTORY_CONFIG_DESCRIPTOR);
    private static final String DELETED_DIFF_MARKER = "$$deleted";
    private static final String NEW_DIFF_MARKER = "$$new";

    /**
     * Handle the config descriptor changes
     * 1. If a ref entity *content* was changed, find all repos that has ref to it and reload these
     * 2. If a ref that is referenced by a repo was deleted, find all repos that has reference to id and re-save
     * these repos without the once reffed entity
     *
     * @param configEventContext the context that is passed through all the handlers chain and hold information about
     *                           the repositories to update/reload
     */
    public void handleCentralConfigDescriptorChangeEvent(ConfigChangeEventContext configEventContext) {
        Optional.ofNullable(configEventContext.getDescriptorChanges().get(this))
                .ifPresent(changes -> changes.forEach((configChangeType, configKeys) -> configKeys
                        .forEach(key -> conditionallyMarkRepositoriesForUpdateOrReload(key, configChangeType,
                                configEventContext))));
    }

    /**
     * Locate the config descriptor differences that match the handler's config pattern, and identify their change
     * action.
     *
     * @param descriptorDiff the config descriptor diff
     */
    public Map<ConfigChangeType, List<String>> handleDiff(Map<String, Set<Diff<?>>> descriptorDiff) {
        Map<ConfigChangeType, List<String>> changesToHandle = new EnumMap<>(ConfigChangeType.class);
        descriptorDiff.keySet().forEach(diff -> {
            Matcher matcher = getConfigPattern().matcher(diff);
            if (matcher.matches()) {
                String configKey = matcher.group("configKey");
                ConfigChangeType changeType = calculateChangeType(configKey, matcher);
                List<String> configKeys = changesToHandle.get(changeType);
                if (configKeys == null) {
                    configKeys = new ArrayList<>();
                }
                configKeys.add(configKey);
                changesToHandle.put(changeType, configKeys);
            }
        });
        return changesToHandle;
    }

    private ConfigChangeType calculateChangeType(String configKey, Matcher matcher) {
        if (NEW_DIFF_MARKER.equals(matcher.group("excess"))) {
            log.debug("Detected 'reference created' event with config key {}", configKey);
            return ConfigChangeType.CREATED;
        }
        if (DELETED_DIFF_MARKER.equals(matcher.group("excess"))) {
            log.debug("Detected 'reference deleted' event with config key {}", configKey);
            return ConfigChangeType.DELETED;
        }
        log.debug("Detected 'reference updated' event with config key {}", configKey);
        return ConfigChangeType.UPDATED;
    }

    /**
     * Mark repositories to be re-saved or reloaded after a config descriptor change
     *
     * @param configKey          The key of the config which changed
     * @param configChangeType   The action to be performed on the repo (reload/update)
     * @param configEventContext Context that is passed through all the handlers chain and hold information about the
     *                           repositories to update/reload
     */
    abstract void conditionallyMarkRepositoriesForUpdateOrReload(String configKey, ConfigChangeType
            configChangeType, ConfigChangeEventContext configEventContext);

    /**
     * @return regex pattern to identify if the config change is delete/create/update
     */
    abstract Pattern getConfigPattern();

    // TODO: 18/08/2022 testme
    public void addToReposToReload(ConfigChangeEventContext configEventContext, String repoKey,
            ReferencedConfigChange.ConfigTrigger configTrigger) {
        Set<ReferencedConfigChange.ConfigTrigger> configTriggers = new HashSet<>();
        Set<ReferencedConfigChange.ConfigTrigger> existingConfigTriggers =
                configEventContext.getReposToReload().get(repoKey);
        if (existingConfigTriggers != null) {
            configTriggers = existingConfigTriggers;
        }
        configTriggers.add(configTrigger);
        configEventContext.getReposToReload().put(repoKey, configTriggers);
    }
}
