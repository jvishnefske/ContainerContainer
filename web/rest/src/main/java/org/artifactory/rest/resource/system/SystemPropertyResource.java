package org.artifactory.rest.resource.system;

import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.security.access.AccessService;
import org.artifactory.util.AolUtils;
import org.artifactory.util.KeyValueList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alexei Vainshtein
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class SystemPropertyResource {
    private static final Logger log = LoggerFactory.getLogger(SystemPropertyResource.class);
    private final CentralConfigService centralConfigService;
    private final AccessService accessService;

    SystemPropertyResource(CentralConfigService centralConfigService, AccessService accessService) {
        this.centralConfigService = centralConfigService;
        this.accessService = accessService;
    }

    @PUT
    @Path("update")
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateSystemProperty(@QueryParam("properties") KeyValueList properties) {
        validatePropertiesRequest(properties);
        Map<String, List<String>> propertyListMap = properties.toStringMap();
        // get descriptor
        MutableCentralConfigDescriptor configDescriptor = centralConfigService.getMutableDescriptor();
        List<SystemPropertyDescriptor> systemPropertyDescriptors = new ArrayList<>();
        // add to the properties section
        propertyListMap.forEach((key, value) -> {
            SystemPropertyDescriptor systemProperty = new SystemPropertyDescriptor(key, String.join(",", value));
            systemPropertyDescriptors.add(systemProperty);
            configDescriptor.removeSystemProperty(key);
            configDescriptor.addSystemProperty(systemProperty);
        });
        // reload the descriptor
        centralConfigService.saveEditedDescriptorAndReload(configDescriptor, systemPropertyDescriptors);
        if (AolUtils.isAol()) {
            accessService.setSubscriptionTypeOnAccess();
        }
        return Response.status(HttpStatus.SC_CREATED).build();
    }

    @DELETE
    @Path("delete")
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteSystemProperty(@QueryParam("properties") List<String> properties) {
        validatePropertiesRequest(properties);
        // get descriptor
        MutableCentralConfigDescriptor configDescriptor = centralConfigService.getMutableDescriptor();
        // add to the properties section
        properties.forEach(configDescriptor::removeSystemProperty);
        // reload the descriptor
        centralConfigService.saveEditedDescriptorAndReload(configDescriptor);
        return Response.status(HttpStatus.SC_ACCEPTED).build();
    }

    private void validatePropertiesRequest(List<String> properties) {
        validateRequestPreConditions();
        if (properties == null || properties.isEmpty()) {
            log.error("The property request is not valid. No properties are passed to reload");
            throw new BadRequestException("No properties are passed to reload");
        }

        if (properties.size() > 1) {
            log.error("Found multiple properties. Currently supported only one property per request");
            throw new BadRequestException(
                    "Found multiple properties. Currently supported only one property per request");
        }

        if (!properties.get(0).startsWith(ConstantValues.aolSubscriptionType.getPropertyName())) {
            String message = "Properties request only supported to a specific system property. Found " +
                    properties.get(0) + " that is not supported via this api";
            log.error(message);
            throw new BadRequestException(message);
        }
    }

    private void validateRequestPreConditions() {
        if (!AolUtils.isAol()) {
            log.error("Changes in the system property rest api supported only for cloud versions");
            throw new ForbiddenException("Supported rest api only for cloud versions");
        }

        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (!aolService.isDashboardUser()) {
            String message =
                    "Artifactory Online does not support reload of properties. Please contact support@jfrog.com for " +
                            "further assistance if required.";
            log.error(message);
            throw new ForbiddenException(message);
        }
    }
}
