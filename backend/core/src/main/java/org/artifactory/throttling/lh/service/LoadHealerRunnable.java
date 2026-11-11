package org.artifactory.throttling.lh.service;

/**
 * @author Amir Avidan
 */
@FunctionalInterface
public interface LoadHealerRunnable {

    void run() throws Exception;
}
