package org.artifactory.rest.common.exception.mapper;

import com.jfrog.bh.acl.error.base.BundleException;
import com.jfrog.bh.acl.error.group.BundleMultiException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import static com.jfrog.bh.util.BundleLoggingUtils.logBundleException;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Acts as a controller advice for all Bundle-Handler REST resources.
 * <p>
 * All instances of {@link BundleException}, which are thrown up to
 * the REST controllers are intercepted at this point.
 * <p>
 * HTTP clients get only a status code and a public message from the exception.
 * <p>
 * The log message varies based on the exception subtype and the log level enabled.
 */
@Slf4j
@Provider
@Component
public class BundleExceptionMapper implements ExceptionMapper<BundleException> {

    @NonNull
    @Override
    public Response toResponse(@NonNull BundleException e) {
        logBundleException(e, log);
        return buildErrorResponse(e);
    }

    @NonNull
    private Response buildErrorResponse(@NonNull BundleException e) {
        ErrorResponse entity = buildErrorResponseEntity(e);
        return Response.status(e.getStatusCode())
                .type(APPLICATION_JSON)
                .entity(entity)
                .build();
    }

    @NonNull
    private ErrorResponse buildErrorResponseEntity(@NonNull BundleException e) {
        return e instanceof BundleMultiException ex
                ? new ErrorResponse(ex.getStatusesWithMessages())
                : new ErrorResponse(e.getStatusCode(), e.getPublicMessage());
    }
}
