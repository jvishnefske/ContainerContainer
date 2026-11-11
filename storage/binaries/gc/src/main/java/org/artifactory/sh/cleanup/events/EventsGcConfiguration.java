package org.artifactory.sh.cleanup.events;

import lombok.experimental.UtilityClass;
import org.artifactory.common.ConstantValues;

@UtilityClass
public class EventsGcConfiguration {

    /**
     * @return {@code true} if event-based garbage collection is enabled during minor garbage collection,
     * as determined by both the global configuration
     * and the specific <i>artifactory.gc.minor.use_event_gc</i> runtime property setting, {@code false} otherwise.
     * @see org.artifactory.common.ConstantValues#gcEventsCleanupEnabled
     *
     * This implementation take the <i>artifactory.gc.minor.use_event_gc</i> system property into account.
     * Once we make sure that we can deprecate Full GC, this property won't be used anymore.
     */
    public boolean useEventGCinMinorGC() {
        return (ConstantValues.gcEventsCleanupEnabled.getBoolean() &&
                Boolean.parseBoolean(System.getProperty("artifactory.gc.minor.use_event_gc", "true")));
    }
}
