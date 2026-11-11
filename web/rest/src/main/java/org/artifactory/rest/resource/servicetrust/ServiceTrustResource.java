package org.artifactory.rest.resource.servicetrust;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.access.TokenNotFoundException;
import org.artifactory.rest.common.util.RestUtils;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.servicetrust.ServiceTrustService;
import org.artifactory.servicetrust.ServiceTrustToken;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.ServiceTrustUseCaseData;

import org.artifactory.util.HttpUtils;
import org.artifactory.util.InternalStringUtils;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.dto.MessageResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_MISSION_CONTROL_EXECUTE;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_MISSION_CONTROL_READ;

/**
 * @author igoru
 */
@Component
@Path(ServiceTrustResource.BASE_PATH)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ServiceTrustResource {
    private static final Logger log = LoggerFactory.getLogger(ServiceTrustResource.class);
    private static final String PAIRING = "pairing";
    private static final String REVOKE = "revoke";
    private static final String PAIRINGS = "pairings";
    private static final String EXCHANGE = "exchange";
    private static final String ERROR_CREATING_PAIRING_TOKEN_PATTERN = "Error creating pairing token. %s";
    private static final String ERROR_GETTING_PAIRING_TOKEN_LIST_PATTERN = "Error getting list of pairing tokens. %s";
    private static final String ERROR_TOKEN_PAIRING_PATTERN = "Error while token pairing. %s";
    private static final String ERROR_TOKEN_UNPAIRING_PATTERN = "Error while cancelling token pairing. %s";
    private static final String ERROR_REVOKING_TOKEN_PATTERN = "Error revoking token. %s";
    private static final String ERROR_TOKEN_EXCHANGE_PATTERN = "Error while token exchange. %s";
    private static final String UNKNOWN_USE_CASE = "Unknown use case";
    private static final String TOKEN_ALREADY_REVOKED_OR_NOT_FOUND = "Token already revoked or not found.";

    public static final String TRUST_WITH_SERVICE_WAS_ESTABLISHED_SUCCESSFULLY =
            "Trust with service %s was established successfully";

    public static final String TRUST_WITH_SERVICE_WAS_CANCELLED_SUCCESSFULLY =
            "Trust with service %s was canceled successfully";

    public static final String TOKEN_WAS_REVOKED_SUCCESSFULLY =
            "Token was successfully revoked";

    public static final String BASE_PATH = "/v1/service_trust/";

    @Autowired
    private ServiceTrustService serviceTrustService;

    @Autowired
    private CentralConfigService configService;

    @Context
    HttpServletRequest request;

    static class ErrorResponse {
        private static Response notFound() {
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(new MessageResponse(UNKNOWN_USE_CASE))
                    .build();
        }

        private static Response from(String message, Exception e) {
            String error = message.formatted(e.getMessage());
            log.error(error, e);
            MessageResponse messageResponse = new MessageResponse(error);
            if (e instanceof ServiceTrustException || e instanceof IllegalStateException) {
                return Response.status(Response.Status.BAD_REQUEST).entity(messageResponse).build();
            } else {
                return Response.serverError().entity(messageResponse).build();
            }
        }
    }

    @GET
    @AllowedPermissionScope(INTERNAL_MISSION_CONTROL_READ)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path(PAIRINGS + "/{usecase: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listTokens(@PathParam("usecase") String useCasePath) {
        Optional<ServiceTrustUseCase> useCase = serviceTrustService.findUseCase(useCasePath);
        return useCase.map(u -> createTokenListResponse(useCasePath)).orElse(ErrorResponse.notFound());
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(INTERNAL_MISSION_CONTROL_EXECUTE)
    @Path(PAIRING + "/{usecase: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createToken(@PathParam("usecase") String useCasePath) {
        Optional<ServiceTrustUseCase> useCase = serviceTrustService.findUseCase(useCasePath);
        return useCase.map(u -> createTokenResponse(useCasePath, u))
                .orElse(ErrorResponse.notFound());
    }

    @DELETE
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(INTERNAL_MISSION_CONTROL_EXECUTE)
    @Path(PAIRING + "/{usecase: .*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response unregisterToken(@PathParam("usecase") String useCasePath, BaseUrlModel baseUrlModel,
            @QueryParam("force") boolean force) {
        Optional<ServiceTrustUseCase> useCase = serviceTrustService.findUseCase(useCasePath);
        return useCase.map(u -> unpairTokenResponse(u, useCasePath, baseUrlModel, force))
                .orElse(ErrorResponse.notFound());
    }

    @DELETE
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path(REVOKE + "/{usecase: .*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response revokeToken(@PathParam("usecase") String useCasePath) {
        Optional<ServiceTrustUseCase> useCase = serviceTrustService.findUseCase(useCasePath);
        return useCase.map(u -> revokeTokenResponse(useCasePath)).orElse(ErrorResponse.notFound());
    }

    @PUT
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(INTERNAL_MISSION_CONTROL_EXECUTE)
    @Path(PAIRING + "/{usecase: .*}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registerToken(@PathParam("usecase") String useCasePath, PairingToken body) {
        Optional<ServiceTrustUseCase> useCase = serviceTrustService.findUseCase(useCasePath);
        return useCase.map(u -> pairingTokenResponse(u, body.getPairingToken()))
                .orElse(ErrorResponse.notFound());
    }

    @POST
    @Path(EXCHANGE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exchangeToken(ExchangeTokenRequest exchangeTokenRequest) {
        try {
            ExchangeTokenResponse response = serviceTrustService.exchangePairingToken(exchangeTokenRequest);
            return Response.ok(response).build();
        } catch (Exception e) {
            return ErrorResponse.from(ERROR_TOKEN_EXCHANGE_PATTERN, e);
        }
    }

    @VisibleForTesting
    void setServiceTrustService(ServiceTrustService serviceTrustService) {
        this.serviceTrustService = serviceTrustService;
    }

    @VisibleForTesting
    String getRestApiUrl() {
        return RestUtils.getRestApiUrl(request) + BASE_PATH;
    }

    @VisibleForTesting
    String getBaseUrl() {
        return HttpUtils.getPlatformServletContextUrl(request);
    }

    @VisibleForTesting
    Response createTokenResponse(String useCasePath, ServiceTrustUseCase serviceTrustUseCase) {
        try {
            String baseUrl = getBaseUrl();
            if (useCasePath.contains("federated-repo/")) {
                baseUrl = setBaseUrlForFed(baseUrl);
            }

            String restApiUrl = getRestApiUrl();
            String pairingUrl = restApiUrl + PAIRING;
            String exchangeUrl = restApiUrl + EXCHANGE;

            String token = serviceTrustService
                    .getPairingToken(new ServiceTrustUseCaseData(useCasePath, serviceTrustUseCase.getTrust(),
                            serviceTrustUseCase), new ServiceTrustUrls(baseUrl, pairingUrl, exchangeUrl));
            PairingToken response = new PairingToken(token);
            return Response.ok().entity(response).build();
        } catch (Exception e) {
            return ErrorResponse.from(ERROR_CREATING_PAIRING_TOKEN_PATTERN, e);
        }
    }

    private String setBaseUrlForFed(String baseUrl) {
        String fedBaseUrl = getFederatedRepoUrlBase();
        if (StringUtils.isNotBlank(fedBaseUrl)) {
            baseUrl = fedBaseUrl.endsWith("/artifactory")
                    ? InternalStringUtils.replaceLast(fedBaseUrl, "/artifactory", "")
                    : fedBaseUrl;
        }

        return baseUrl;
    }

    @VisibleForTesting
    String getFederatedRepoUrlBase() {
        return configService.getDescriptor().getFederatedRepoUrlBaseOrDefault();
    }

    private Response createTokenListResponse(String useCasePath) {
        try {
            String baseUrl = getBaseUrl();
            Collection<ServiceTrustToken> tokens = serviceTrustService.getPairingTokenList(useCasePath, baseUrl);

            List<BaseUrlModel> pairedInstances = tokens.stream()
                    .map(ServiceTrustToken::baseUrl)
                    .map(BaseUrlModel::new)
                    .toList();
            return Response.ok().entity(pairedInstances).build();

        } catch (Exception e) {
            return ErrorResponse.from(ERROR_GETTING_PAIRING_TOKEN_LIST_PATTERN, e);
        }
    }


    private Response pairingTokenResponse(ServiceTrustUseCase serviceTrustUseCase, String token) {
        try {
            serviceTrustService.pairToken(serviceTrustUseCase, token);
            String message =
                    String.format(TRUST_WITH_SERVICE_WAS_ESTABLISHED_SUCCESSFULLY, serviceTrustUseCase.getName());
            return Response.ok(new MessageResponse(message)).build();
        } catch (Exception e) {
            return ErrorResponse.from(ERROR_TOKEN_PAIRING_PATTERN, e);
        }
    }

    private Response unpairTokenResponse(ServiceTrustUseCase serviceTrustUseCase, String useCasePath,
            BaseUrlModel baseUrl, boolean force) {
        try {
            assertBaseUrlProvided(baseUrl);
            serviceTrustService.unpairToken(serviceTrustUseCase, useCasePath, baseUrl.getBaseUrl(), force);
            String message =
                    String.format(TRUST_WITH_SERVICE_WAS_CANCELLED_SUCCESSFULLY, serviceTrustUseCase.getName());
            return Response.ok(new MessageResponse(message)).build();
        } catch (Exception e) {
            return ErrorResponse.from(ERROR_TOKEN_UNPAIRING_PATTERN,e);
        }
    }

    private void assertBaseUrlProvided(BaseUrlModel baseUrl) {
        if (baseUrl == null || baseUrl.getBaseUrl() == null) {
            throw new ServiceTrustException("Base url is not provided");
        }
    }

    private Response revokeTokenResponse(String useCasePath) {
        try {
            serviceTrustService.revokeToken(useCasePath);
            return Response.ok(new MessageResponse(TOKEN_WAS_REVOKED_SUCCESSFULLY)).build();
        } catch (TokenNotFoundException e) {
            return Response.ok(new MessageResponse(TOKEN_ALREADY_REVOKED_OR_NOT_FOUND)).build();
        } catch (Exception e) {
            return ErrorResponse.from(ERROR_REVOKING_TOKEN_PATTERN,e);
        }
    }
}
