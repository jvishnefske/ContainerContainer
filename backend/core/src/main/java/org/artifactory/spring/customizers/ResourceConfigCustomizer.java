package org.artifactory.spring.customizers;

import org.glassfish.jersey.server.ResourceConfig;

public interface ResourceConfigCustomizer {
    void customize(ResourceConfig config);
}
