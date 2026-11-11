package org.artifactory.debug;

import org.artifactory.debug.threads.SingleThreadDump;
import org.artifactory.debug.threads.ThreadDumpResponse;

/**
 * @author Amir Avidan
 */
public interface DebugService {

    /**
     * Return log level for given class
     *
     * @param className class name
     */
    String getLogLevelFromClass(String className);

    /**
     * Change debug level for given classes for given time, can execute only one until time is up, or delete request
     *
     * @param debugLevelConfig Map of classes and log level and timer
     */
    void changeLogLevel(DebugLevelConfig debugLevelConfig);

    /**
     * Restore all debug level to the default before the change.
     */
    void restoreDebugSettings();

    /**
     * Method for debug level propagation
     *
     * @param debugLevelConfig Map of classes and log level and timer
     */
    void propagateLoggersSettings(DebugLevelConfig debugLevelConfig);

    /**
     * Method for getting thread dumps.
     *
     * @param propagatePods whether to aggregate all instances' thread dumps in the response
     *                      or only of the current server instance.
     */
    ThreadDumpResponse generateThreadDump(boolean propagatePods);

    /**
     * retrieve a single thread dump of the current server instance.
     */
    SingleThreadDump generateSingleThreadDump();
}
