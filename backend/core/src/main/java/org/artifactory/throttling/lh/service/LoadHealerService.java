package org.artifactory.throttling.lh.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.throttling.lh.config.LoadHealerActivity;
import org.artifactory.throttling.lh.mode.ModeUpdateResult;
import org.artifactory.throttling.lh.mode.ModeUpdateStatus;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.artifactory.throttling.lh.model.ActivityState;
import org.artifactory.throttling.lh.model.RunningActivityHandler;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Service interface for managing Load Healer activities and configurations.
 * This interface provides methods to check if the Load Healer is enabled, register activities,
 * start and end activities, and collect metrics related to the Load Healer's operations.
 * The Load Healer can operate in different modes, which are determined by the configuration
 * properties {@code artifactory.load.healer.mode}.
 * Implementations of this interface should provide the actual logic for managing the Load Healer's
 * activities and configurations.
 *
 * @author Hussein Alshqerat
 */
public interface LoadHealerService {

    /**
     * Checks if the Load Healer is enabled.
     *
     * @return true if the Load Healer is enabled, false otherwise
     */
    boolean isEnabled();

    /**
     * Registers a new activity with the Load Healer.
     *
     * @param activityConfig the activity to be registered
     */
    void registerActivity(@Nonnull ActivityConfig activityConfig);

    /**
     * Starts an activity with the specified resource.
     *
     * @param activity the activity to be started
     * @param resource the resource associated with the activity, the resource can be null if the activity does not
     *                 require a resource.
     * @return an ActivityHandler to manage the activity
     */
    RunningActivityHandler startActivity(LoadHealerActivity activity, @Nullable String resource);

    /**
     * Starts an activity with the specified resource.
     *
     * @param activity the activity to be started
     * @return an ActivityHandler to manage the activity
     */
    RunningActivityHandler startActivity(LoadHealerActivity activity);

    /**
     * Ends the specified activity.
     *
     * @param handler the ActivityHandler managing the activity to be ended
     */
    void endActivity(@Nonnull RunningActivityHandler handler);

    /**
     * Asserts that the Load Healer allows the specified activity to proceed.
     * If the activity is blocked, this method logs a debug message and throws a LoadHealerException.
     *
     * @param handler the ActivityHandler managing the activity to be checked
     * @throws LoadHealerException if the activity is blocked due to too many other requests
     */
    void assertAllows(RunningActivityHandler handler);

    /**
     * Executes the given runnable within a Load Healer activity context.
     * This method handles the complete lifecycle:
     * 1. Creates and starts the activity handler
     * 2. Asserts that the activity is allowed to proceed
     * 3. Executes the runnable
     * 4. Automatically closes the activity handler
     * <p>
     * Any checked exception thrown by the runnable is rethrown using @SneakyThrows,
     * preserving its original type so callers can catch specific exceptions.
     *
     * @param activity the activity to be started
     * @param resource the resource associated with the activity (can be null)
     * @param runnable the code to execute within the activity context
     */
    void executeWithActivity(LoadHealerActivity activity, @Nullable String resource, LoadHealerRunnable runnable);

    /**
     * Retrieves a map of all registered activities, where the key is the activity name and the value is the
     * ActivityConfig.
     *
     * @return A map containing all registered activities.
     */
    default Map<String, ActivityConfig> getRegisteredActivities() {
        return Collections.emptyMap();
    }

    /**
     * Retrieves a set of names of all registered activities.
     *
     * @return A set containing the names of all registered activities that should be set to reactive mode.
     */
    default Set<String> getActivitiesNameForReactiveMode() {
        return Collections.emptySet();
    }

    /**
     * Sets all activities that are currently marked for reactive mode to REACTIVE running mode.
     * This method is called when the {@link org.artifactory.probes.tomcat.TomcatReadinessMonitor#isReady()}
     * determines the system is not ready.
     * This method should store the original running modes before switching to reactive mode.
     */
    default void setActivitiesToReactiveMode() {
    }

    /**
     * Restores all activities from reactive mode back to their original running modes.
     * This method is called when exiting reactive mode and should restore the values
     * that were stored during setActivitiesToReactiveMode().
     */
    default void restoreActivitiesFromReactiveMode() {
    }

    /**
     * Retrieves a map of activities that are consume more than 20% of threads
     * This method is useful for monitoring and identifying activities that are consuming significant
     * system resources.
     *
     * @return A map where the key is the activity key.
     * and the value is the ActivityState containing the current state of the activity.
     * Returns an empty map if none of the activities consume over 20% of threads.
     */
    default Map<String, ActivityState> getHeavyActivities() {
        return Collections.emptyMap();
    }

    /**
     * Gets the current Load Healer mode running on this node.
     *
     * @return the current mode from memory
     */
    default String getCurrentMode() {
        return "DISABLED";
    }

    /**
     * Updates Load Healer mode on this node and saves to DB.
     * Other nodes discover the change via DB sync when system is overloaded.
     *
     * @param mode     the new mode to set
     * @param revertAt the instant at which the mode should revert to the default
     * @return result containing whether the mode was changed and the actual revert time
     */
    default ModeUpdateResult updateMode(String mode, Instant revertAt) {
        return new ModeUpdateResult(ModeUpdateStatus.UNCHANGED, revertAt);
    }

    /**
     * Reloads Load Healer mode from configs table.
     * Called by reactive mode job to sync mode from DB when system is overloaded.
     */
    default void reloadModeFromConfig() {
    }
}
