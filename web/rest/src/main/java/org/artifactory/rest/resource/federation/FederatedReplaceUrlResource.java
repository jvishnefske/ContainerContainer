package org.artifactory.rest.resource.federation;

import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.federation.FederatedReplaceURLService;
import org.artifactory.model.ReplaceUrlConfig;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.util.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_REPLACE_URL;

/**
 * @author Daniel simantov
 * Handle baseurl change for federated repositories
 * by updating existing remote members and copying their corresponding
 * queue of the old federated member
 */

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("federation")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class FederatedReplaceUrlResource {

    private static final Logger log = LoggerFactory.getLogger(FederatedReplaceUrlResource.class);
    private FederatedReplaceURLService federatedReplaceURLService;
    private SecurityService securityService;

    @Autowired
    public void setFederatedReplaceURLService(FederatedReplaceURLService federatedReplaceURLService) {
        this.federatedReplaceURLService = federatedReplaceURLService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Handle baseurl change for federated repositories
     * by updating existing remote members and copying their corresponding
     * queue of the old federated member
     */
    @POST
    @Path("replaceUrl")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(INTERNAL_SCOPE_REPLACE_URL)
    public Response replaceUrl(@RequestBody ReplaceUrlConfig replaceURLConfig) {
        try {
            checkIsUrlValid(replaceURLConfig);
            log.debug("Received request to replace base url from {} to {}",
                    replaceURLConfig.getOldBaseUrl(), replaceURLConfig.getNewBaseUrl());
            if (!securityService.isAdmin()) {
                log.debug("User is not admin, calling replaceUrlAndQueues as system user");
                securityService.callAsSystem(
                        () -> {
                            federatedReplaceURLService.replaceUrlAndQueues(replaceURLConfig.getOldBaseUrl(),
                                    replaceURLConfig.getNewBaseUrl(), true);
                            return null;
                        });
            } else {
                federatedReplaceURLService.replaceUrlAndQueues(replaceURLConfig.getOldBaseUrl(),
                        replaceURLConfig.getNewBaseUrl(), true);
            }
        } catch (ConfigurationException configurationException) {
            return handleConfigurationException(replaceURLConfig, configurationException);
        } catch (IllegalStateException illegalStateException) {
            return handleIllegalStateException(replaceURLConfig, illegalStateException);
        } catch (Exception e) {
            log.error("Unexpected error when trying to replace URL from {} to {}",
                    replaceURLConfig.getOldBaseUrl(), replaceURLConfig.getNewBaseUrl(), e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Failed to replace URL: " + e.getMessage()).build();
        }

        return Response.status(OK).entity(getResponse(replaceURLConfig)).build();
    }

    @POST
    @Path("updateMemberUrl")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response updateMemberUrl(@RequestBody ReplaceUrlConfig replaceURLConfig) {
        try {
            checkIsUrlValid(replaceURLConfig);
            log.debug("Received request to replace base url from {} to {}",
                    replaceURLConfig.getOldBaseUrl(), replaceURLConfig.getNewBaseUrl());
            federatedReplaceURLService.replaceUrlAndQueues(replaceURLConfig.getOldBaseUrl(),
                    replaceURLConfig.getNewBaseUrl(), false);
        } catch (ConfigurationException configurationException) {
            return handleConfigurationException(replaceURLConfig, configurationException);
        } catch (IllegalStateException illegalStateException) {
            return handleIllegalStateException(replaceURLConfig, illegalStateException);
        }

        return Response.status(OK).entity(getResponse(replaceURLConfig)).build();
    }

    private void checkIsUrlValid(ReplaceUrlConfig replaceURLConfig) {
        isUrlValid(replaceURLConfig.getNewBaseUrl());
        isUrlValid(replaceURLConfig.getOldBaseUrl());
    }

    private static Response handleIllegalStateException(ReplaceUrlConfig replaceURLConfig,
            IllegalStateException illegalStateException) {
        log.error("Replace URL is unavailable for these URLs: old URL:{}, new URL: {}, e: {}",
                replaceURLConfig.getOldBaseUrl(), replaceURLConfig.getNewBaseUrl(),
                illegalStateException.getMessage());
        return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Replace URL is unavailable for these URL").build();
    }

    private static Response handleConfigurationException(ReplaceUrlConfig replaceURLConfig,
            ConfigurationException configurationException) {
        log.error("Replace URL is unavailable for these URLs, Urls incorrect old URL: {}, new URL {}, e: {}",
                replaceURLConfig.getOldBaseUrl(), replaceURLConfig.getNewBaseUrl(),
                configurationException.getMessage());
        return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Replace URL is unavailable for these URLs, Urls incorrect").build();
    }

    private String getResponse(ReplaceUrlConfig replaceURLConfig) {
        return String.format("Binding URL from URL %s to new %s finished.",
                replaceURLConfig.getOldBaseUrl(), replaceURLConfig.getNewBaseUrl());
    }

    /**
     * Validates URL using UrlValidator.
     *
     * @param urlBase url to validate.
     * @throws ConfigurationException if URL is in illegal form.
     * @see org.artifactory.util.UrlValidator
     */
    private void isUrlValid(String urlBase) {
        if (!isBlank(urlBase)) {
            try {
                new UrlValidator("http", "https").validate(urlBase);
            } catch (UrlValidator.UrlValidationException ex) {
                log.warn("invalid base URL {}", urlBase);
                throw new ConfigurationException("Invalid federated repo URL base", ex);
            }
        }
    }
}
