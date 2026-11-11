package org.artifactory.rest.common.exception.mapper;

import lombok.extern.slf4j.Slf4j;
import com.jfrog.ph.acl.exception.PackageRuntimeException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * @author Oded Balazada
 */
@Component
@Provider
@Slf4j
public class PackageRuntimeExceptionMapper implements ExceptionMapper<PackageRuntimeException> {

    @Override
    public Response toResponse(PackageRuntimeException exception) {
        log.debug(exception.getInternalReason());
        ErrorResponse errorResponse = new ErrorResponse(exception.getStatus(), exception.getPublicReason());
        Response.ResponseBuilder builder =
                Response.status(exception.getStatus()).type(MediaType.APPLICATION_JSON).entity(errorResponse);
        return builder.build();
    }
}
