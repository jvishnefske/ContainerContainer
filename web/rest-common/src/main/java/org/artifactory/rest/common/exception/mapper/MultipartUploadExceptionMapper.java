package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.storage.mpu.exceptions.MultipartUploadException;
import org.jfrog.storage.mpu.MultipartUpload;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Intercepts and maps {@link MultipartUploadException} exceptions thrown by {@link MultipartUpload}.
 *
 * @author Andrey Tashlitsky
 */
@Component
@Provider
public class MultipartUploadExceptionMapper implements ExceptionMapper<MultipartUploadException> {

    @Override
    public Response toResponse(MultipartUploadException e) {
        ErrorResponse errorResponse = new ErrorResponse(e.getStatusCode(), e.getMessage());
        return Response.status(e.getStatusCode())
                .type(APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}
