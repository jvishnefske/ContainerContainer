package org.artifactory.addon.curation.wq;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.workqueue.WorkQueueInfo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Nonnull;

import static org.artifactory.common.ConstantValues.curationEventsWorkers;

/**
 * @author Andrey Tashlitsky
 */
@Configuration
public class CurationCachedPackageEventWorkQueueInfoConfig {

    public static final String CURATION_EVENTS_WQ_NAME = "Curation Cached Package Events";

    @Bean
    public WorkQueueInfo curationEventsWorkQueueInfo(@Nonnull AddonsManager addonsManager) {
        return new WorkQueueInfo(curationEventsWorkers.getInt(), CURATION_EVENTS_WQ_NAME,
                (key, info) -> addonsManager.addonByType(HaAddon.class).getConflictsGuard(key));
    }
}
