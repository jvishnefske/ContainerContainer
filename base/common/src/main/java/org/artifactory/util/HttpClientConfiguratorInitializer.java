package org.artifactory.util;

/**
 * Classes, implementing this interface, will get a chance to apply service-wide global configuration
 * to the new {@link HttpClientConfigurator} instance. Artifactory will use it to plug in the observability
 * interceptors.
 */
@FunctionalInterface
public interface HttpClientConfiguratorInitializer {
    void configure(HttpClientConfigurator httpClientConfigurator);
}
