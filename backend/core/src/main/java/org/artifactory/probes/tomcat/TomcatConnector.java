package org.artifactory.probes.tomcat;

import lombok.Builder;

import javax.management.ObjectName;

/**
 * @author Igor Usenko
 */
@Builder
public record TomcatConnector(
        String name,
        long maxThreads,
        long maxConnections,
        ObjectName threadPool,
        ObjectName requestProcessor) {
}
