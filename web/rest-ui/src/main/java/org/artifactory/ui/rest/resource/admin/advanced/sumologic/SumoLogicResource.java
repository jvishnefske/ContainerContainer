/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.resource.admin.advanced.sumologic;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.logging.sumologic.SumoLogicException;
import org.artifactory.logging.sumologic.SumoLogicService;
import org.artifactory.rest.common.model.sumologic.SumoLogicModel;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.admin.advanced.AdvancedServiceFactory;
import org.artifactory.util.MaskedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.Stream;

import static org.jfrog.common.ArgUtils.requireSatisfies;

/**
 * @author Shay Yaakov
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("sumologic")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SumoLogicResource extends BaseResource {
    private static final Logger log = LoggerFactory.getLogger(SumoLogicResource.class);
    protected static final String ERROR_SUMO_LOGIC = "Error handling authorization callback request from Sumo Logic: ";

    @Autowired
    private AdvancedServiceFactory factory;

    @Autowired
    private SumoLogicService sumoLogicService;

    @Autowired
    private CentralConfigService centralConfigService;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateSumoLogicConfig(SumoLogicModel sumoLogicModel) {
        return runService(factory.updateSumoLogicConfigService(), sumoLogicModel);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSumoLogicConfig() {
        return runService(factory.getSumoLogicConfigService());
    }

    @GET
    @Path("auth_callback/{username}/{setup}")
    public Response callbackFromSumoLogicAuth(@PathParam("username") String username, @PathParam("setup") String setup,
            @QueryParam("code") String code, @QueryParam("base_uri") String baseUri,
            @QueryParam("error") String error, @QueryParam("error_description") String errorDescription) {
        try {
            throwOnError(StringUtils.isNotBlank(errorDescription) ? errorDescription : error);
            requireSatisfies(username, StringUtils::isNotBlank, "username is required");
            requireSatisfies(setup, SetupType::contains, "unexpected setup type: " + setup);
            requireSatisfies(code, StringUtils::isNotBlank, "code is required");
            requireSatisfies(baseUri, StringUtils::isNotBlank, "base_uri is required");
            if (ConstantValues.sumologicVerifyDomain.getBoolean()) {
                requireSatisfies(baseUri, this::isSumologicUrl, "base_uri must be of sumologic domain");
            } else {
                if (!isSumologicUrl(baseUri)) {
                    log.error("base_uri: {} must be of sumologic domain", baseUri);
                }
            }

            String accessToken = sumoLogicService.createToken(username, code, baseUri);

            SetupType setupType = SetupType.valueOf(setup);
            boolean newSetup;
            switch (setupType) {
                case new_app:
                    newSetup = true;
                    break;
                case existing_app:
                    newSetup = false;
                    break;
                default: // Should not get here because of the validation above, but still...
                    throw new IllegalArgumentException("Unexpected setup type: " + setupType);
            }
            sumoLogicService.setupApplication(username, baseUri, newSetup);

            SumoLogicConfigDescriptor sumoLogicConfig = centralConfigService.getDescriptor().getSumoLogicConfig();
            String uri = sumoLogicConfig.getDashboardUrl() + "?access_token=" + accessToken;
            log.debug("Redirecting to dashboard: {}?access_token={}", sumoLogicConfig.getDashboardUrl(),
                    MaskedValue.of(accessToken));
            return Response.seeOther(new URI(uri)).build();
        } catch (IllegalArgumentException e) {
            return getSumoLogicErrorResponse(e, 400);
        } catch (SumoLogicException e) {
            return getSumoLogicErrorResponse(e, e.getRelaxedStatus());
        } catch (Exception e) {
            return getSumoLogicErrorResponse(e, 500);
        }
    }

    private static Response getSumoLogicErrorResponse(Exception e, int status) {
        String msg = ERROR_SUMO_LOGIC + e.getMessage();
        log.error(msg, e);
        return Response.status(status).entity(msg).build();
    }

    private boolean isSumologicUrl(String baseUri) {
        try {
            URI uri = new URI(baseUri);
            String host = uri.getHost();
            if (host == null) {
                log.warn("URI host is null for provided baseUri (length: {})", 
                        baseUri != null ? baseUri.length() : 0);
                return false;
            }

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                log.warn("URI scheme {} is not supported", uri.getScheme());
                return false;
            }

            return host.equalsIgnoreCase("sumologic.com") ||
                    host.toLowerCase().endsWith(".sumologic.com");

        } catch (URISyntaxException e) {
            log.warn("Invalid URI syntax for baseUri: {}", baseUri, e);
            return false;
        }
    }

    private void throwOnError(String error) {
        if (error != null) {
            int status = "invalid_client_id".equals(error) ? 401 : 400;
            throw new SumoLogicException(error, status);
        }
    }

    private enum SetupType {
        new_app, existing_app;

        static boolean contains(String value) {
            return Stream.of(values()).anyMatch(setup -> setup.name().equals(value));
        }
    }

    @POST
    @Path("refreshToken")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response refreshToken() {
        return runService(factory.refreshSumoLogicTokenService());
    }

    @POST
    @Path("registerSumoLogicApplication")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerSumoLogicApplicationService() {
        return runService(factory.registerSumoLogicApplicationService());
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("reset")
    public Response resetSumoLogicApplication() {
        return runService(factory.resetSumoLogicApplicationService());
    }

    @POST
    @Path("updateProxy")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateSumoLogicProxyService() {
        return runService(factory.updateSumoLogicProxyService());
    }
}
