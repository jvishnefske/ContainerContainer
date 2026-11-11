package org.artifactory.repo.confighandler;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.workqueue.WorkQueueConflictsGuardProvider;
import org.artifactory.workqueue.WorkQueueInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigChangeWorkQueueSpringConfig {

    public static final String CONFIG_CHANGE_WQ_NAME = "System Config Change Work Queue";
    private final WorkQueueConflictsGuardProvider defaultConflictsGuardProvider;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ConfigChangeWorkQueueSpringConfig(@Nonnull AddonsManager addonsManager) {
        defaultConflictsGuardProvider = (key, info) -> addonsManager.addonByType(HaAddon.class).getConflictsGuard(key);
    }

    @Bean
    public WorkQueueInfo systemConfigChangeWorkQueue() {
        return new WorkQueueInfo(ConstantValues.systemConfigChangeWorkers.getInt(), CONFIG_CHANGE_WQ_NAME,
                defaultConflictsGuardProvider);
    }
}
