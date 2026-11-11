package org.artifactory.repo.confighandler;

import jakarta.validation.constraints.NotNull;
import org.artifactory.repo.confighandler.manager.ConfigChangeEventHandlerManager;

import java.util.Set;

import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;

/**
 * Handler template for configuration change events (Such as change/addition/deletion of: proxy, layout, certificate,
 * etc.). Operations to handle are aggregated on the {@link ConfigChangeEventContext} and persistency operations are
 * done at the end by the handler manager.
 *
 * @author shayb
 * @see ConfigChangeEventHandlerManager
 */
public interface ConfigChangeEventHandler {

    /**
     * Handle config change event
     *
     * @param configEventContext the context onto which to aggregate changes
     */
    void handle(@NotNull ConfigChangeEventContext configEventContext);

    /**
     * Get the config change event type subscription
     */
    Set<SystemConfigChangeEventType> getConfigChangeEventTypeSubscription();

}
