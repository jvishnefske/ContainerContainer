package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.AqlException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import java.sql.SQLTimeoutException;

/**
 * @author Katrin Shor
 */
@Component
@Provider
@Slf4j
public class AqlExceptionMapper implements ExceptionMapper<AqlException> {

    @Override
    public Response toResponse(final AqlException exception) {
        Response blockedResponse = BlockedQueryResponseHelper.blockedQueryResponseOrNull(exception);
        if (blockedResponse != null) {
            return blockedResponse;
        }
        log.warn("Fail to execute AQL query.", exception);
        if (exception.getCause() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(), exception.getMessage()))
                    .build();
        } else if (shouldSendTimeout(exception)) {
            log.warn("An AQL query has terminated due to a timeout in the query.", exception);
            return Response.status(Response.Status.REQUEST_TIMEOUT)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(Response.Status.REQUEST_TIMEOUT.getStatusCode(),
                            exception.getCause().getMessage()))
                    .build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                            exception.getMessage() + "\ncause: " + exception.getCause().getMessage()))
                    .build();
        }
    }

    private boolean shouldSendTimeout(AqlException e) {
        if (e.getCause() != null) {
            final String message = e.getCause().getMessage();
            return (message != null && message.contains("cancel")) ||
                    e.getCause() instanceof SQLTimeoutException;
        }
        return false;
    }
}
