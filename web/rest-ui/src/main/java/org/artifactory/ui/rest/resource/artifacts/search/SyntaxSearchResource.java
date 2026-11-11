package org.artifactory.ui.rest.resource.artifacts.search;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Search;
import org.artifactory.ui.rest.service.artifacts.search.SearchServiceFactory;
import org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author Igor Usenko
 */
@Slf4j
@Path("syntax-search")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SyntaxSearchResource extends BaseResource {

    @Autowired
    private SearchServiceFactory searchFactory;

    @Value("${artifactory.treebrowser.newpagewithsearch.enabled}")
    boolean enabled;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSearchResults(Search search) {
        assertEnabled();
        try {
            return runService(searchFactory.syntaxSearchService(), search);
        } catch (SyntaxSearchServiceException e) {
            log.warn("Syntax search failed: {}", e.getMessage(), e);
            return badRequest(e.getMessage());
        }
    }

    private void assertEnabled() {
        if (!enabled) {
            throw new BadRequestException("Syntax Search is disabled");
        }
    }

    private static Response badRequest(String message) {
        ErrorResponse body = new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(), message);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
