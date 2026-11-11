package org.artifactory.addon.worker;

import lombok.NonNull;
import org.artifactory.common.addon.Addon;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.jfrog.worker.proto.execution.Action;

import java.util.List;

/**
 * @author Tomer Slutsky
 */
public interface WorkersAddon extends Addon {

    /**
     * This method is the entry point for performing the workers with dynamic API or V2
     * NOTE: The implementation should provide the worker with the necessary security and repository configuration.
     *
     * @param event The action requested
     * @param args   The args the worker receives
     */
    void run(@NonNull SupportedUnifiedEvents event, Object... args);

    /**
     * This method is the entry point for performing the workers
     *
     * @param action The action requested
     * @param args   The args the worker receives
     * @deprecated
     * (7.101.0,
     *  AutoInjecting security and RepoConfig in new api,
     *  Use runWorkerAutoInjectedSecurityAndRepoConfig())
     */
    @Deprecated(since="7.101.0", forRemoval = true)
    void runWorker(@NonNull Action action, Object... args);

    /**
     * This method is the entry point for fetching the available events for the integration with the workers
     *
     * @return List<WorkerEventsModel> containing the available events
     */
    List<WorkerEventsModel> getAvailableEvents();

    /**
     * This method is the entry point for performing the workers
     * The implementation should provide the worker with the necessary security and repository configuration.
     *
     * @param action The action requested
     * @param args   The args the worker receives
     * @deprecated
     * (7.117.0,
     *  Should use the runWorkerV2 method instead
     */
    @Deprecated(since="7.117.0", forRemoval = true)
    void runWorkerAutoInjectedSecurityAndRepoConfig(@NonNull Action action, Object... args);

    /**
     * This method is the entry point for performing the workers in async way (in another thread)
     * This can be used if the original thread has not committed a transaction and this can block the worker.
     * The implementation should provide the worker with the necessary security and repository configuration.
     *
     * @param action The action requested
     * @param args   The args the worker receives
     * @deprecated
     * (7.117.0,
     *  Should use the runWorkerV2 method instead
     */
    @Deprecated(since="7.117.0", forRemoval = true)
    void runWorkerAsyncAutoInjectedSecurityAndRepoConfig(@NonNull Action action, Object... args);

}
