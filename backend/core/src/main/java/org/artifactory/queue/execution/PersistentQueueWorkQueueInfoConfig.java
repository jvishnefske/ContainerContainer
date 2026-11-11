package org.artifactory.queue.execution;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.workqueue.WorkQueueInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistentQueueWorkQueueInfoConfig {

    public static final String PERSISTENT_QUEUE_TRIGGERING_WQ_NAME = "Persistent Queue Triggering";

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    public WorkQueueInfo triggerPersistentQueueWorkQueueInfo(@Nonnull AddonsManager addonsManager) {
        return new WorkQueueInfo(ConstantValues.persistentQueueTriggerNumberOfThreads.getInt(),
                PERSISTENT_QUEUE_TRIGGERING_WQ_NAME,
                (key, info) -> addonsManager.addonByType(HaAddon.class).getConflictsGuard(key));
    }
}
