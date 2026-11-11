package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.exception.LoadHealerException;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

/**
 * Intercepts and maps {@link LoadHealerException} exceptions thrown by the "Load Healer" mechanism.
 *
 * @author Hussein Alshqerat
 */
@Component
@Provider
public class LoadHealerExceptionMapper implements ExceptionMapper<LoadHealerException> {

    @Override
    public Response toResponse(LoadHealerException exception) {
        ErrorResponse errorResponse = new ErrorResponse(TOO_MANY_REQUESTS.getStatusCode(), exception.getMessage());
        return Response.status(TOO_MANY_REQUESTS)
                .type(APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}
