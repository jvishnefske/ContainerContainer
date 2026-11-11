package org.artifactory.probes;

/**
 * @author Andrey Tashlitsky
 */
public interface ProbesService {

    /**
     * Triggers all the readiness related checks.
     */
    void triggerReadinessCheck();

    /**
     * Checks the readiness probe status.
     *
     * @param noCache Do not return cached status.
     */
    void checkReadinessStatus(boolean noCache);

    /**
     * Checks the liveness probe status.
     */
    void checkLivenessStatus();

    /**
     * Checks if the application is considered as "Ready" at the moment. Should be <code>true</code> if last readiness
     * check was successful and the application was considered "Ready" as a result of that check.
     */
    boolean isReady();
}
