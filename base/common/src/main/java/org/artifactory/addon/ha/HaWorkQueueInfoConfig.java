package org.artifactory.addon.ha;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.workqueue.WorkQueueInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HaWorkQueueInfoConfig {

    public static final String HA_EVENT_MESSAGES_WQ_NAME = "HA Event Messages";

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Bean
    public WorkQueueInfo haEventsWorkQueueInfo(@Nonnull AddonsManager addonsManager) {
        return new WorkQueueInfo(ConstantValues.haMessagesWorkers.getInt(), HA_EVENT_MESSAGES_WQ_NAME,
                (key, info) -> addonsManager.addonByType(HaCommonAddon.class).getConflictsGuard(key));
    }
}
