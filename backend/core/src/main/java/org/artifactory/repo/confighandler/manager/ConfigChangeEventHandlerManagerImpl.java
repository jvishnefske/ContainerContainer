package org.artifactory.repo.confighandler.manager;

import org.apache.commons.lang3.builder.Diff;
import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.config.ArtifactoryConfigDiffUtil;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.repo.confighandler.AbstractConfigDescriptorEventHandler;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.confighandler.ConfigChangeEventHandler;
import org.artifactory.repo.confighandler.ConfigChangeType;
import org.artifactory.repo.confighandler.ConfigChangeWorkItem;
import org.artifactory.spring.Reloadable;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.jfrog.common.config.diff.DiffUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.repo.confighandler.manager.UpdateOrReloadStrategyFactory.UpdateOrReloadStrategyMode;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.ARTIFACTORY_CONFIG_DESCRIPTOR;

/**
 * @author Mor Merhav
 */
@Component
@Reloadable(beanClass = ConfigChangeEventHandlerManager.class)
public class ConfigChangeEventHandlerManagerImpl implements ConfigChangeEventHandlerManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeEventHandlerManagerImpl.class);
    private final EnumMap<SystemConfigChangeEventType, Set<ConfigChangeEventHandler>> configChangeEventHandlerMap;
    private UpdateOrReloadStrategy updateOrReloadStrategy;
    private final UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory;

    @Autowired
    public ConfigChangeEventHandlerManagerImpl(Collection<ConfigChangeEventHandler> configChangeEventHandlers,
            UpdateOrReloadStrategyFactory updateOrReloadStrategyFactory) {
        this.configChangeEventHandlerMap = new EnumMap<>(SystemConfigChangeEventType.class);
        this.updateOrReloadStrategyFactory = updateOrReloadStrategyFactory;
        buildConfigChangeEventHandlerMap(configChangeEventHandlers);
    }

    public ConfigChangeEventHandlerManagerImpl(Collection<ConfigChangeEventHandler> configChangeEventHandlers,
            UpdateOrReloadStrategyMode updateOrReloadStrategyMode) {
        this.configChangeEventHandlerMap = new EnumMap<>(SystemConfigChangeEventType.class);
        this.updateOrReloadStrategyFactory = new UpdateOrReloadStrategyFactory();
        this.updateOrReloadStrategy = updateOrReloadStrategyFactory.create(updateOrReloadStrategyMode);
        buildConfigChangeEventHandlerMap(configChangeEventHandlers);
    }

    @Override
    public void init() {
        this.updateOrReloadStrategy = updateOrReloadStrategyFactory.create(UpdateOrReloadStrategyMode.STANDARD);
        updateOrReloadStrategy.init();
    }

    private void buildConfigChangeEventHandlerMap(Collection<ConfigChangeEventHandler> configChangeEventHandlers) {
        configChangeEventHandlers.forEach(handler ->
                handler.getConfigChangeEventTypeSubscription()
                        .forEach(changeEventType -> {
                            Set<ConfigChangeEventHandler> handlerSet =
                                    this.configChangeEventHandlerMap.get(changeEventType);
                            if (handlerSet == null) {
                                if (changeEventType != ARTIFACTORY_CONFIG_DESCRIPTOR) {
                                    this.configChangeEventHandlerMap.put(changeEventType,
                                            Collections.singleton(handler));
                                    return;
                                }
                                handlerSet = new HashSet<>();
                            }
                            handlerSet.add(handler);
                            configChangeEventHandlerMap.put(changeEventType, handlerSet);
                        }));
    }

    @Override
    public void handle(ConfigChangeWorkItem configChangeWorkItem) {
        if (configChangeWorkItem == null) {
            return;
        }
        var configChangeEvent = configChangeWorkItem.getConfigChangeEvent();
        if (!configChangeEventHandlerMap.containsKey(configChangeEvent.getSystemConfigChangeEventType())) {
            log.error("Failed to handle config change event of type '{}'. handler not found",
                    configChangeEvent.getSystemConfigChangeEventType().getType());
            return;
        }
        var configEventContext = new ConfigChangeEventContext(configChangeEvent);
        if (configEventContext.getEvent().getSystemConfigChangeEventType() == ARTIFACTORY_CONFIG_DESCRIPTOR) {
            handleConfigDescriptorChangeEvent(configEventContext, false);
        } else {
            handleConfigChangeEvent(configEventContext);
        }
    }

    @Override
    public ConfigDescriptorChangeWorkItem handleOnlyConfigDescriptorDeleteChange(
            ConfigChangeWorkItem configChangeWorkItem) {
        var configEventContext = new ConfigChangeEventContext(configChangeWorkItem.getConfigChangeEvent());
        if (configEventContext.getEvent().getSystemConfigChangeEventType() == ARTIFACTORY_CONFIG_DESCRIPTOR) {
            var unhandledDescriptorChanges = handleConfigDescriptorChangeEvent(configEventContext, true);
            return new ConfigDescriptorChangeWorkItem(configChangeWorkItem.getConfigChangeEvent(),
                    unhandledDescriptorChanges);
        }
        return null;
    }

    @Override
    public void handle(ConfigDescriptorChangeWorkItem configDescriptorChangeWorkItem) {
        if (configDescriptorChangeWorkItem == null || configDescriptorChangeWorkItem.getDescriptorChanges() == null ||
                configDescriptorChangeWorkItem.getDescriptorChanges().isEmpty()) {
            return;
        }
        var configChangeEventContext =
                new ConfigChangeEventContext(configDescriptorChangeWorkItem.getConfigChangeEvent());
        configChangeEventContext.setDescriptorChanges(configDescriptorChangeWorkItem.getDescriptorChanges());
        handleConfigChangeEvent(configChangeEventContext);
    }

    @Override
    public void handleCreateOrUpdateChanges(MutableCentralConfigDescriptor newDescriptor,
            CentralConfigDescriptor oldDescriptor) {
        var systemConfigEvent =
                new SystemConfigChangeEvent(ARTIFACTORY_CONFIG_DESCRIPTOR, oldDescriptor, newDescriptor);
        var systemConfigChangeWorkItem = new ConfigChangeWorkItem(systemConfigEvent);
        ConfigDescriptorChangeWorkItem configDescriptorChangeWorkItem =
                collectCreateOrUpdateChanges(systemConfigChangeWorkItem);
        if (configDescriptorChangeWorkItem == null || configDescriptorChangeWorkItem.getDescriptorChanges() == null ||
                configDescriptorChangeWorkItem.getDescriptorChanges().isEmpty()) {
            return;
        }
        var configChangeEventContext =
                new ConfigChangeEventContext(configDescriptorChangeWorkItem.getConfigChangeEvent());
        configChangeEventContext.setDescriptorChanges(configDescriptorChangeWorkItem.getDescriptorChanges());
        handleConfigChangeEvent(configChangeEventContext);
    }

    private ConfigDescriptorChangeWorkItem collectCreateOrUpdateChanges(ConfigChangeWorkItem configChangeWorkItem) {
        var configEventContext = new ConfigChangeEventContext(configChangeWorkItem.getConfigChangeEvent());
        if (configEventContext.getEvent().getSystemConfigChangeEventType() == ARTIFACTORY_CONFIG_DESCRIPTOR) {
            var descriptorChanges = findDescriptorChanges(configEventContext);
            descriptorChanges.forEach((handler, changes) -> changes.remove(ConfigChangeType.DELETED));
            return new ConfigDescriptorChangeWorkItem(configChangeWorkItem.getConfigChangeEvent(), descriptorChanges);
        }
        return null;
    }

    private Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>>
            handleConfigDescriptorChangeEvent(ConfigChangeEventContext configChangeEventContext,
            boolean shouldHandleOnlyDeleteChanges) {
        var descriptorChanges = findDescriptorChanges(configChangeEventContext);
        if (descriptorChanges.isEmpty()) {
            return Map.of();
        }
        configChangeEventContext.setDescriptorChanges(descriptorChanges);
        if (shouldHandleOnlyDeleteChanges) {
            Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>> deleteConfigChanges =
                    new HashMap<>();
            descriptorChanges.forEach(
                    (handler, changes) -> Optional.ofNullable(changes.remove(ConfigChangeType.DELETED))
                            .ifPresent(configKeyList -> deleteConfigChanges.put(handler,
                                    Map.of(ConfigChangeType.DELETED, configKeyList))));
            descriptorChanges = descriptorChanges.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            configChangeEventContext.setDescriptorChanges(deleteConfigChanges);
        }
        handleConfigChangeEvent(configChangeEventContext);
        return shouldHandleOnlyDeleteChanges ? descriptorChanges : Map.of();
    }

    private void handleConfigChangeEvent(ConfigChangeEventContext configChangeEventContext) {
        SystemConfigChangeEventType eventType = configChangeEventContext.getEvent().getSystemConfigChangeEventType();
        Set<ConfigChangeEventHandler> handlers;
        if (eventType == ARTIFACTORY_CONFIG_DESCRIPTOR) {
            handlers = configChangeEventContext.getDescriptorChanges().keySet().stream()
                    .map(ConfigChangeEventHandler.class::cast)
                    .collect(Collectors.toSet());
        } else {
            handlers = configChangeEventHandlerMap.get(eventType);
        }
        for (ConfigChangeEventHandler configChangeEventHandler : handlers) {
            configChangeEventHandler.handle(configChangeEventContext);
        }
        updateOrReloadStrategy.updateOrReload(configChangeEventContext);
    }

    private Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>> findDescriptorChanges(
            ConfigChangeEventContext configEventContext) {
        Map<String, Set<Diff<?>>> descriptorDiff = findDescriptorDiff(configEventContext);
        Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>> descriptorChanges =
                new HashMap<>();
        configChangeEventHandlerMap.get(ARTIFACTORY_CONFIG_DESCRIPTOR).stream()
                .map(AbstractConfigDescriptorEventHandler.class::cast)
                .forEach(handler -> {
                    var changes = handler.handleDiff(descriptorDiff);
                    if (!changes.isEmpty()) {
                        descriptorChanges.put(handler, changes);
                    }
                });
        return descriptorChanges;
    }

    private Map<String, Set<Diff<?>>> findDescriptorDiff(ConfigChangeEventContext configEventContext) {
        SystemConfigChangeEvent event = configEventContext.getEvent();
        CentralConfigDescriptor newConfig = (CentralConfigDescriptor) Objects.requireNonNull(event.getNewConfig(),
                "New central config descriptor in change event cannot be null");
        CentralConfigDescriptor oldConfig = (CentralConfigDescriptor) event.getOldConfig();
        if (oldConfig == null) {
            return Map.of();
        }
        DiffResult<?> diffs = ArtifactoryConfigDiffUtil.findDiff(oldConfig, newConfig);
        if (diffs.getDiffs().isEmpty()) {
            return Map.of();
        }
        // TODO [U.L]: This mechanism is not accurate - for example, "propertySets»aPropSet»properties" is redundant,
        //  we should compact the list to avoid unnecessary database calls etc.
        return DiffUtils.hierarchicalPaths(diffs.getDiffs(), Diff::getFieldName);
    }
}
