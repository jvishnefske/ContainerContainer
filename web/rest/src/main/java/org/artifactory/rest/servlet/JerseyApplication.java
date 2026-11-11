package org.artifactory.rest.servlet;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.rest.common.RestResponseFilter;
import org.artifactory.rest.common.ShadowResponseFilter;
import org.artifactory.rest.filter.HaRestFilter;
import org.artifactory.rest.filter.JsonMappingExceptionOnCollectionResponseFilter;
import org.artifactory.rest.filter.OfflineRestFilter;
import org.artifactory.rest.jersey.RemoveOptionsBinder;
import org.artifactory.spring.customizers.ResourceConfigCustomizer;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * @author haims
 */

public class JerseyApplication extends ResourceConfig {
    /**
     * Register JAX-RS application components.
     */

    private static final Logger log = LoggerFactory.getLogger(JerseyApplication.class);

    @Inject
    ApplicationContext context; // Note: Jersey will inject this application context, not Spring.

    public JerseyApplication() {
        //RestAuthenticationFilter registered with @Provider. Couldn't make it run before RolesAllowedDynamicFeature
        //otherwise
        /*
         * Well-known issue with Jersey, Jackson and PropertyFilter
         * https://stackoverflow.com/q/35437298/561088
         */
        register(JsonMappingExceptionOnCollectionResponseFilter.class);
        register(RestResponseFilter.class);
        register(OfflineRestFilter.class);
        register(HaRestFilter.class);
        packages("org.artifactory;" +
                "org.jfrog.repomd;" +
                "com.jfrog.ph;" +
                "com.jfrog.bh;" +
                "com.jfrog.build;" +
                "com.jfrog.distribution.handler;" +
                "com.jfrog.application.lifecycle;" +
                "com.jfrog.cs");
        property(CommonProperties.OUTBOUND_CONTENT_LENGTH_BUFFER, -1);
        property(ServerProperties.RESOURCE_VALIDATION_DISABLE,
                Boolean.valueOf(System.getProperty(ServerProperties.RESOURCE_VALIDATION_DISABLE)));
        property(ServerProperties.WADL_FEATURE_DISABLE, true);
        register(org.glassfish.jersey.jackson.JacksonFeature.class);
        register(org.glassfish.jersey.media.multipart.MultiPartFeature.class);
        register(RolesAllowedDynamicFeature.class);
        if (disableOptionsMethod()) {
            log.debug("disabling jersey options support");
            register(new RemoveOptionsBinder());
        }
        if (isPackageHandlerShadowingEnabled()) {
            log.debug("enabling package handler shadowing");
            register(ShadowResponseFilter.class);
            register(new ShadowModelBinder());
        }
        if (shouldFixJacksonPrettyPrinter()) {
            JerseyJacksonPrettyPrinterFix.install();
        } else {
            log.info("Skipped applying Jackson pretty-printer fix on Jersey server.");
        }
        if (shouldReduceJerseyMemoryConsumption()) {
            JerseyFixMemoryConsumptionHooks.install();
        } else {
            log.info("Skipped applying memory-saving Java hooks on Jersey server.");
        }
    }

    @PostConstruct
    void applyCustomizers() {
        if (this.context == null) {
            log.warn("The Spring context is not available, JAX-RS metrics will not be available");
            return;
        }
        log.info("Apply additional JAX-RS filters (metrics, etc.)");
        ObjectProvider<ResourceConfigCustomizer> customizers =
                this.context.getBeanProvider(ResourceConfigCustomizer.class);
        customizers.orderedStream().forEach(customizer -> customizer.customize(this));
    }

    private boolean disableOptionsMethod() {
        String property = System.getProperty("artifactory.disable.jersey.options");
        if (StringUtils.isBlank(property)) {
            return false;
        }
        return Boolean.parseBoolean(property);
    }

    boolean shouldReduceJerseyMemoryConsumption() {
        String property = System.getProperty("artifactory.jersey.disable-memory-hooks", "false");
        return !Boolean.parseBoolean(property);
    }

    boolean shouldFixJacksonPrettyPrinter() {
        String property = System.getProperty("artifactory.jersey.disable-jackson-pretty-printer-fix", "false");
        return !Boolean.parseBoolean(property);
    }

    boolean isPackageHandlerShadowingEnabled() {
        String property = System.getProperty("artifactory.shadowing.enabled", "false");
        return Boolean.parseBoolean(property);
    }
}
