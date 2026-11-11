package org.artifactory.repo.confighandler.manager;

import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.repo.confighandler.ConfigChangeEventContext;
import org.artifactory.repo.confighandler.ConfigChangeWorkItem;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.spring.RepoConfigChangeListener;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;

import static org.artifactory.repo.confighandler.ConfigChangeWorkQueueSpringConfig.CONFIG_CHANGE_WQ_NAME;

/**
 * A manager of configuration change event handlers.
 * This manager receives a system config change event and activates all config change event handlers in the system.
 * After all operations are set on the config change event context by the handlers, the manager executes the necessary
 * persistency operations by the provided {@link UpdateOrReloadStrategy}.
 * Also calls any config change listeners if needed.
 *
 * Example of System config changes:
 * 1. Proxy removal which is required to update the repositories' config for repositories that had a reference to
 * the deleted proxy
 * 2. Proxy port change which is required to reload the repositories but not save them
 * 3. Layout content change, etc.
 *
 * Still under development:
 * Reload specific repositories based on referenced config change:
 * {@link InternalRepositoryService#reloadOnReferencedConfigChange}
 *
 * @author Mor Merhav
 * @see SystemConfigChangeEvent
 * @see ConfigChangeEventContext
 * @see org.artifactory.repo.confighandler.ConfigChangeEventHandler
 * @see UpdateOrReloadStrategy
 * @see RepoConfigChangeListener
 */
public interface ConfigChangeEventHandlerManager extends ReloadableBean {

    /**
     * handle async the config change work item.
     * Reload or update the repositories configurations after a system config change occurred while this
     * config might affect existing repositories.
     *
     * @param configChangeWorkItem the work item which include the config change event
     */
    @Async(delayUntilAfterCommit = true, workQueue = @WorkQueueConfig(name = CONFIG_CHANGE_WQ_NAME,
            blockUntilFinished = true))
    void handle(ConfigChangeWorkItem configChangeWorkItem);

    /**
     * Handle async the config descriptor change work item.
     * Reload or update the repositories configurations after a system config change occurred while this
     * config might affect existing repositories.
     *
     * @param configDescriptorChangeWorkItem the work item which include the config descriptor changes
     */
    @Async(delayUntilAfterCommit = true, workQueue = @WorkQueueConfig(name = CONFIG_CHANGE_WQ_NAME,
            blockUntilFinished = true))
    void handle(ConfigDescriptorChangeWorkItem configDescriptorChangeWorkItem);

    /**
     * collect only create or update config changes and handle async.
     * Reload or update the repositories configurations after a create or update config change might have affected
     * existing repositories
     */
    @Async
    void handleCreateOrUpdateChanges(MutableCentralConfigDescriptor newDescriptor,
            CentralConfigDescriptor oldDescriptor);

    /**
     * Handle only the config descriptor delete changes.
     *
     * Reload or update the repositories configurations after delete change of a system config occurred
     * while this config might affect existing repositories.
     *
     * @param configChangeWorkItem the work item which include the config change event
     * @return ConfigDescriptorChangeWorkItem which contains descriptor changes (create/update) that have been found
     * but haven't been handled
     */
    ConfigDescriptorChangeWorkItem handleOnlyConfigDescriptorDeleteChange(ConfigChangeWorkItem configChangeWorkItem);
}
