package org.artifactory.rest.filter;

import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.rest.common.DisabledApi;
import org.glassfish.jersey.server.model.AnnotatedMethod;
import org.springframework.stereotype.Component;

/**
 * @author Igor Usenko
 */
@Component
@Provider
public class DisabledApiDynamicFeature implements DynamicFeature {
    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        DisabledApi annotation = new AnnotatedMethod(resourceInfo.getResourceMethod())
                .getAnnotation(DisabledApi.class);
        if (annotation != null) {
            context.register(new DisabledApiFilter(annotation.disabledWhen(), annotation.responseMessage()));
        }
    }
}
