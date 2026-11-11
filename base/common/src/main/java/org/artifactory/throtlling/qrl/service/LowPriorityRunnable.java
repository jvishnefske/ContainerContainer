package org.artifactory.throtlling.qrl.service;

import jakarta.servlet.ServletException;
import java.io.IOException;

/**
 * @author Andrey Tashlitsky
 */
@FunctionalInterface
public interface LowPriorityRunnable {

    void run() throws ServletException, IOException;
}
