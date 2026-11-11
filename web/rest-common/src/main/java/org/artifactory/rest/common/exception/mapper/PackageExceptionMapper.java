package org.artifactory.rest.common.exception.mapper;

import lombok.extern.slf4j.Slf4j;
import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Component
@Provider
@Slf4j
public class PackageExceptionMapper implements ExceptionMapper<PackageException> {

    @Override
    public Response toResponse(PackageException exception) {
        log.debug(exception.getInternalReason());
        ErrorResponse errorResponse = new ErrorResponse(exception.getStatus(), exception.getPublicReason());
        Response.ResponseBuilder builder =
                Response.status(exception.getStatus()).type(MediaType.APPLICATION_JSON).entity(errorResponse);
        exception.getAdditionalHeaders().forEach(builder::header);
        return builder.build();
    }
}
