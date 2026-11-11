package org.artifactory.rest.servlet;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceModel;

import jakarta.ws.rs.core.Configuration;
import java.util.Set;

import static org.artifactory.common.ConstantValues.shadowingEnabled;

/**
 * @author Michael Akushsky
 * This class is responsible for adding shadowing to the package handler resources.
 * It iterates through the resources and adds shadowing to the ones that are related to the path patterns.
 */
@Slf4j
public class ShadowModelProcessor implements ModelProcessor {

    Set<String> pathsToShadow = Set.of("{packageType}/{context}", "{packageType}/{version: v[0-9]+}/{repoKey}");

    @Override
    public ResourceModel processResourceModel(ResourceModel resourceModel, Configuration configuration) {
        final ResourceModel.Builder newResourceModelBuilder = new ResourceModel.Builder(false);
        try {
            if (!shadowingEnabled.getBoolean()) {
                log.debug("processResourceModel: packageHandlerShadowingEnabled is false, skipping");
                return resourceModel;
            }

            resourceModel.getResources()
                    .forEach(resource -> newResourceModelBuilder.addResource(addShadowing(resource, false)));

        } catch (Exception e) {
            log.error("Failed to process resource model", e);
        }
        return newResourceModelBuilder.build();
    }

    @Override
    public ResourceModel processSubResource(ResourceModel subResourceModel, Configuration configuration) {
        return subResourceModel;
    }

    private Resource addShadowing(Resource resource, boolean isChildResource) {

        final Resource.Builder resourceBuilder = Resource.builder(resource);

        // Skip resources that are not related to the path patterns
        resource.getChildResources().stream()
                .filter(childResource -> pathsToShadow.contains(childResource.getPath()))
                .forEach(childResource ->
                        resourceBuilder.addChildResource(addShadowing(childResource, true)));

        if (!isChildResource) {
            return resourceBuilder.build();
        }

        // Start with a copy of the original resource using different path
        Resource updatedResource = resourceBuilder.path("shadow/" + resource.getPath()).build();
        log.debug("Added shadowing to resource with path {} and pathPattern {}", resource.getPath(),
                resource.getPathPattern());
        return updatedResource;
    }
}